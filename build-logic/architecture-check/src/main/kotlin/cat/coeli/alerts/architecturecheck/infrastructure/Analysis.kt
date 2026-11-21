package cat.coeli.alerts.architecturecheck.infrastructure

import cat.coeli.alerts.architecturecheck.domain.Catalogue
import cat.coeli.alerts.architecturecheck.domain.Finding
import cat.coeli.alerts.architecturecheck.domain.FindingKind
import cat.coeli.alerts.architecturecheck.domain.Layer
import cat.coeli.alerts.architecturecheck.domain.SourceLine
import cat.coeli.alerts.architecturecheck.domain.forbiddenSuppressionsOf
import cat.coeli.alerts.architecturecheck.domain.stringDependenciesOf
import java.io.File
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Every finding of the repository at [root], sorted by kind, module and subject. */
internal fun analyze(root: File, catalogue: Catalogue): List<Finding> {
    val modules = discoverModules(root)
    val placements = importClasses(modules)
    val classes = ClassAnalysis(modules, placements, catalogue)
    return (classes.findings() +
            memberFindingsOf(placements, modules, catalogue, classes::sourceLineOf) +
            modules.flatMap { textFindingsOf(it, catalogue) })
        .sortedWith(compareBy({ it.kind }, { it.module }, { it.subject }))
}

private fun textFindingsOf(module: Module, catalogue: Catalogue): List<Finding> {
    val buildScript = module.buildScript
    val dependencies =
        stringDependenciesOf(buildScript.readText()).map {
            Finding(
                FindingKind.StringDependencies,
                module.path,
                it.subject,
                source = SourceLine(module.pathOf(buildScript), it.line),
            )
        }
    val suppressions =
        if (module.path == ".") emptyList()
        else
            SourceSet.entries
                .flatMap { sourceSet ->
                    module.sourceDirectoryOf(sourceSet).walkTopDown().filter {
                        it.extension == "kt"
                    }
                }
                .flatMap { file ->
                    forbiddenSuppressionsOf(file.readText(), catalogue.allowedSuppressions).map {
                        Finding(
                            FindingKind.ForbiddenSuppressions,
                            module.path,
                            it.subject,
                            module.pathOf(file),
                            SourceLine(module.pathOf(file), it.line),
                        )
                    }
                }
    return dependencies + suppressions
}

@OptIn(ExperimentalSerializationApi::class) private val jsonc = Json { allowComments = true }

/**
 * JSON with comments, so the catalogue can explain itself. A layer nobody has heard of is an error,
 * not a guess.
 */
internal fun readCatalogue(text: String): Catalogue {
    val catalogue = jsonc.parseToJsonElement(text).jsonObject
    val strings = { field: String ->
        catalogue[field]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }.toSet()
    }
    return Catalogue(
        libraries =
            (catalogue["libraries"] as? JsonObject).orEmpty().mapValues { (library, layer) ->
                Layer.entries.firstOrNull { it.name == layer.jsonPrimitive.content }
                    ?: throw IllegalArgumentException(
                        "Unknown layer ${layer.jsonPrimitive.content} for $library, expected one of ${Layer.entries}",
                    )
            },
        allowedSuppressions = strings("allowedSuppressions"),
        testSupportModules = strings("testSupportModules"),
    )
}
