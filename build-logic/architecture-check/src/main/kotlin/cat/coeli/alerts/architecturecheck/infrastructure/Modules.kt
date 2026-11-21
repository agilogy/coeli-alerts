package cat.coeli.alerts.architecturecheck.infrastructure

import java.io.File

/** The source sets whose classes are read; [Test]'s only for what they use of the others. */
enum class SourceSet(internal val directoryName: String) {
    Main("main"),
    TestFixtures("testFixtures"),
    Test("test"),
}

/** Where a class was compiled from. */
data class Placement(val module: String, val sourceSet: SourceSet)

/**
 * [path] is the module's directory from the repository root, `.` for the root build script, which
 * has no sources.
 */
class Module(val path: String, private val directory: File) {
    internal val isApp: Boolean
        get() = path.startsWith("apps/")

    internal val buildScript: File
        get() = File(directory, BUILD_SCRIPT)

    internal fun sourceDirectoryOf(sourceSet: SourceSet): File =
        File(directory, "src/${sourceSet.directoryName}/kotlin")

    internal fun classDirectoryOf(sourceSet: SourceSet): File =
        File(directory, "build/classes/kotlin/${sourceSet.directoryName}")

    /** A file of the module as a path from the repository root. */
    internal fun pathOf(file: File): String =
        file.relativeTo(directory).invariantSeparatorsPath.let {
            if (path == ".") it else "$path/$it"
        }
}

private const val BUILD_SCRIPT = "build.gradle.kts"

private val MODULE_PARENTS = listOf("apps", "modules")

/**
 * The root build script and every directory of `apps` and `modules` with a build script, like
 * `settings.gradle.kts`.
 */
internal fun discoverModules(root: File): List<Module> =
    listOf(Module(".", root)) +
        MODULE_PARENTS.flatMap { parent -> File(root, parent).listFiles().orEmpty().toList() }
            .filter { File(it, BUILD_SCRIPT).isFile }
            .map { Module(it.relativeTo(root).invariantSeparatorsPath, it) }
            .sortedBy { it.path }
