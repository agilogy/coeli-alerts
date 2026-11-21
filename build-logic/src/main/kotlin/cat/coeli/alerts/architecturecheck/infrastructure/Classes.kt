package cat.coeli.alerts.architecturecheck.infrastructure

import cat.coeli.alerts.architecturecheck.domain.Catalogue
import cat.coeli.alerts.architecturecheck.domain.Finding
import cat.coeli.alerts.architecturecheck.domain.FindingKind
import cat.coeli.alerts.architecturecheck.domain.Layer
import cat.coeli.alerts.architecturecheck.domain.SourceLine
import cat.coeli.alerts.architecturecheck.domain.cyclesOf
import cat.coeli.alerts.architecturecheck.domain.isPlacedTooLow
import cat.coeli.alerts.architecturecheck.domain.layerOf
import cat.coeli.alerts.architecturecheck.domain.neededLayersOf
import cat.coeli.alerts.architecturecheck.domain.packagesOutsideRootOf
import cat.coeli.alerts.architecturecheck.domain.rootPackageOf
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.SourceCodeLocation
import com.tngtech.archunit.core.importer.ClassFileImporter
import java.io.File

/** Every class compiled from the modules' source sets, with where it was compiled from. */
internal fun importClasses(modules: List<Module>): Map<JavaClass, Placement> {
    val directories = modules.flatMap { module ->
        SourceSet.entries
            .map { module.classDirectoryOf(it) to Placement(module.path, it) }
            .filter { (directory, _) -> directory.isDirectory }
    }
    val prefixes = directories.map { (directory, placement) ->
        directory.absolutePath + File.separator to placement
    }
    return if (directories.isEmpty()) emptyMap()
    else
        ClassFileImporter()
            .importPaths(directories.map { it.first.toPath() })
            .mapNotNull { javaClass -> placementOf(javaClass, prefixes)?.let { javaClass to it } }
            .toMap()
}

private fun placementOf(javaClass: JavaClass, prefixes: List<Pair<String, Placement>>): Placement? =
    javaClass.source
        .orElse(null)
        ?.uri
        ?.takeIf { it.scheme == "file" }
        ?.let { File(it).absolutePath }
        ?.let { path -> prefixes.firstOrNull { (prefix, _) -> path.startsWith(prefix) }?.second }

/**
 * A class reached by another, and the member reached when it is reached through a call or a field
 * access.
 */
private data class Reach(
    val target: JavaClass,
    val member: String?,
    val location: SourceCodeLocation,
)

/** Arrays count as their elements; primitives are nobody's. */
private fun JavaClass.reaches(): List<Reach> {
    val accesses = accessesFromSelf.map {
        Reach(it.targetOwner, it.target.name, it.sourceCodeLocation)
    }
    val accessed = accesses.map { it.target.name to it.location.lineNumber }.toSet()
    val others =
        directDependenciesFromSelf
            .filterNot { (it.targetClass.name to it.sourceCodeLocation.lineNumber) in accessed }
            .map { Reach(it.targetClass, null, it.sourceCodeLocation) }
    return (accesses + others)
        .map { reach ->
            reach.copy(
                target = if (reach.target.isArray) reach.target.baseComponentType else reach.target,
            )
        }
        .filterNot { it.target.isPrimitive }
}

/** The source file a class was compiled from, as `package/File.kt`. */
private fun JavaClass.sourceFile(): String =
    packageName.replace('.', '/') +
        "/" +
        (source.orElse(null)?.fileName?.orElse(null) ?: "${simpleName}.kt")

/**
 * Nested, local and anonymous classes, lambdas included, are judged as part of the top-level class
 * around them.
 */
internal fun JavaClass.topLevelName(): String =
    generateSequence(this) { it.enclosingClass.orElse(null) }.last().name

/**
 * The analysis of the classes of `src/main` and `src/testFixtures`; tests are read only for what
 * they use.
 */
internal class ClassAnalysis(
    private val modules: List<Module>,
    private val placements: Map<JavaClass, Placement>,
    private val catalogue: Catalogue,
) {
    private val modulesByPath = modules.associateBy { it.path }
    private val ownClassNames = placements.keys.map { it.name }.toSet()
    private val ownPackages = placements.keys.map { it.packageName }.toSet()
    private val production = placements.filterValues { it.sourceSet != SourceSet.Test }

    private fun isOwn(target: JavaClass) = target.name in ownClassNames

    /** A class of ours the import left out, such as one Kotlin inlined away, is nobody's. */
    private fun isLibrary(target: JavaClass) =
        target.packageName.isNotEmpty() && target.packageName !in ownPackages

    private val neededLayers: Map<String, Layer> by lazy {
        val reachesByUnit =
            production.keys
                .groupBy { it.topLevelName() }
                .mapValues { (_, classes) -> classes.flatMap { it.reaches() } }
        neededLayersOf(
            libraryLayers =
                reachesByUnit
                    .mapNotNull { (unit, reaches) ->
                        reaches
                            .filter { isLibrary(it.target) }
                            .mapNotNull { libraryLayerOf(it) }
                            .maxOrNull()
                            ?.let { unit to it }
                    }
                    .toMap(),
            dependencies =
                reachesByUnit.mapValues { (unit, reaches) ->
                    reaches
                        .filter { isOwn(it.target) }
                        .map { it.target.topLevelName() }
                        .filter { it != unit }
                        .toSet()
                },
        )
    }

    private fun libraryLayerOf(reach: Reach): Layer? =
        catalogue.libraryLayerOf(reach.target.name, reach.target.packageName, reach.member)

    /** A source file moves as a whole, so it needs what the most demanding of its classes needs. */
    private val neededLayersByFile: Map<String, Layer> by lazy {
        production.keys
            .groupBy { it.sourceFile() }
            .mapValues { (_, classes) ->
                classes.maxOf { neededLayers[it.topLevelName()] ?: Layer.Helper }
            }
    }

    /**
     * The layer a class of ours counts at: the one it needs when it is placed too low, as that is
     * reported apart.
     */
    private fun ownLayerOf(target: JavaClass): Layer {
        val declared = layerOf(target.packageName)
        val needed = neededLayersByFile[target.sourceFile()] ?: declared
        return if (isPlacedTooLow(declared, needed)) needed else declared
    }

    fun findings(): List<Finding> =
        forbiddenDependencies() +
            classesPlacedTooLow() +
            librariesWithoutRole() +
            packageCycles() +
            packagesOutsideModuleRoot()

    private fun forbiddenDependencies(): List<Finding> =
        production.entries
            .flatMap { (origin, placement) ->
                val originLayer = layerOf(origin.packageName)
                origin.reaches().mapNotNull { reach ->
                    val targetLayer =
                        when {
                            isOwn(reach.target) -> ownLayerOf(reach.target)
                            isLibrary(reach.target) -> libraryLayerOf(reach)
                            else -> null
                        }
                    targetLayer
                        ?.takeUnless { originLayer.mayDependOn(it) }
                        ?.let {
                            Finding(
                                FindingKind.ForbiddenDependencies,
                                placement.module,
                                "${origin.topLevelName()} → ${reach.target.packageName}",
                                "$originLayer → $it: ${reach.target.name}",
                                sourceLineOf(origin, placement, reach.location),
                            )
                        }
                }
            }
            .sortedWith(compareBy({ it.source?.line ?: 0 }, { it.detail }))
            .distinctBy { it.module to it.subject }

    /** One finding per source file, which is what moves. */
    private fun classesPlacedTooLow(): List<Finding> =
        production.entries
            .distinctBy { (javaClass, _) -> javaClass.sourceFile() }
            .mapNotNull { (javaClass, placement) ->
                val file = javaClass.sourceFile()
                Finding(
                        FindingKind.ClassesPlacedTooLow,
                        placement.module,
                        file,
                        "needs nothing of ${Layer.Infrastructure}",
                        sourceLineOf(javaClass, placement, javaClass.sourceCodeLocation)
                            ?.copy(line = 1),
                    )
                    .takeIf {
                        isPlacedTooLow(
                            layerOf(javaClass.packageName),
                            neededLayersByFile.getValue(file),
                        )
                    }
            }

    /** Once for the whole repository, wherever the library is used from. */
    private fun librariesWithoutRole(): List<Finding> =
        production.entries
            .flatMap { (origin, placement) ->
                origin
                    .reaches()
                    .filter { isLibrary(it.target) && libraryLayerOf(it) == null }
                    .map { reach ->
                        Finding(
                            FindingKind.LibrariesWithoutRole,
                            placement.module,
                            reach.target.packageName,
                            "used by ${origin.topLevelName()}",
                            sourceLineOf(origin, placement, reach.location),
                        )
                    }
            }
            .sortedWith(compareBy({ it.module }, { it.source?.line ?: 0 }, { it.detail }))
            .distinctBy { it.subject }

    private fun packageCycles(): List<Finding> {
        val edges =
            production.keys
                .flatMap { origin ->
                    origin.directDependenciesFromSelf
                        .map { it.targetClass }
                        .filter { isOwn(it) && it.packageName != origin.packageName }
                        .map { origin.packageName to it.packageName }
                }
                .groupBy({ it.first }, { it.second })
                .mapValues { it.value.toSet() }
        val moduleOfPackage =
            production.entries.associate { (javaClass, placement) ->
                javaClass.packageName to placement.module
            }
        return cyclesOf(edges).map { cycle ->
            Finding(
                FindingKind.PackageCycles,
                moduleOfPackage.getValue(cycle.first()),
                cycle.joinToString(" ↔ "),
            )
        }
    }

    private fun packagesOutsideModuleRoot(): List<Finding> =
        placements.entries.groupBy({ it.value.module }, { it.key.packageName }).flatMap {
            (module, packages) ->
            packagesOutsideRootOf(module, packages.toSet()).map {
                Finding(
                    FindingKind.PackagesOutsideModuleRoot,
                    module,
                    it,
                    "expected under ${rootPackageOf(module)}",
                )
            }
        }

    /** The source file where the package convention puts it, when it is there. */
    fun sourceLineOf(
        javaClass: JavaClass,
        placement: Placement,
        location: SourceCodeLocation,
    ): SourceLine? =
        modulesByPath[placement.module]?.let { module ->
            location.sourceFileName
                .takeIf { it.isNotEmpty() }
                ?.let {
                    File(
                        module.sourceDirectoryOf(placement.sourceSet),
                        javaClass.packageName.replace('.', '/') + "/$it",
                    )
                }
                ?.takeIf { it.isFile }
                ?.let { SourceLine(module.pathOf(it), location.lineNumber) }
        }
}
