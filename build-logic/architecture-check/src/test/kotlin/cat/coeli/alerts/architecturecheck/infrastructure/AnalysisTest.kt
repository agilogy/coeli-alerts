package cat.coeli.alerts.architecturecheck.infrastructure

import cat.coeli.alerts.architecturecheck.domain.Catalogue
import cat.coeli.alerts.architecturecheck.domain.Finding
import cat.coeli.alerts.architecturecheck.domain.FindingKind
import cat.coeli.alerts.architecturecheck.domain.Layer
import cat.coeli.alerts.sample.domain.Report
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Analyzes a made-up repository whose classes are the fixtures under `cat.coeli.alerts.sample`,
 * `.app` and `.elsewhere` of this test source set, copied to where Gradle would have compiled them.
 */
class AnalysisTest {
    private val root = createTempDirectory("architecture-check").toFile()

    private val catalogue =
        Catalogue(
            libraries =
                mapOf(
                    "kotlin" to Layer.Helper,
                    "kotlin.io.ConsoleKt" to Layer.Infrastructure,
                    "java.lang" to Layer.Helper,
                    "java.io" to Layer.Infrastructure,
                    "org.jetbrains.annotations" to Layer.Helper,
                ),
            allowedSuppressions = setOf("Allowed"),
        )

    private val findings: List<Finding> by lazy {
        compiled("cat/coeli/alerts/sample/domain", "modules/sample", SourceSet.Main)
        compiled("cat/coeli/alerts/sample/infrastructure", "modules/sample", SourceSet.Main)
        compiled("cat/coeli/alerts/sample/cycle", "modules/sample", SourceSet.Main)
        compiled("cat/coeli/alerts/elsewhere", "modules/sample", SourceSet.Main)
        compiled("cat/coeli/alerts/sample/tests", "modules/sample", SourceSet.Test)
        compiled("cat/coeli/alerts/app/infrastructure", "apps/app", SourceSet.Main)
        compiled("cat/coeli/alerts/app/domain", "apps/app", SourceSet.Main)
        compiled("cat/coeli/alerts/app/tests", "apps/app", SourceSet.Test)
        file("build.gradle.kts", "plugins { base }")
        file(
            "modules/sample/build.gradle.kts",
            """
            dependencies {
                implementation("com.example:library:1.0")
                // implementation("com.example:commented-out:1.0")
                testImplementation(project(":app"))
            }
            """
                .trimIndent(),
        )
        file("apps/app/build.gradle.kts", "dependencies { implementation(projects.sample) }")
        file(
            "modules/sample/src/main/kotlin/cat/coeli/alerts/sample/domain/Suppressed.kt",
            "@Suppress(\"MagicNumber\", \"Allowed\")\nval x = 42",
        )
        analyze(root, catalogue)
    }

    @AfterTest
    fun deleteRepository() {
        root.deleteRecursively()
    }

    private fun compiled(packagePath: String, module: String, sourceSet: SourceSet) {
        val classes = File(Report::class.java.protectionDomain.codeSource.location.toURI())
        File(classes, packagePath)
            .copyRecursively(
                File(root, "$module/build/classes/kotlin/${sourceSet.directoryName}/$packagePath"),
            )
    }

    private fun file(path: String, text: String) {
        File(root, path).apply { parentFile.mkdirs() }.writeText(text)
    }

    private fun found(kind: FindingKind): List<String> =
        findings
            .filter { it.kind == kind }
            .map { listOfNotNull(it.subject, it.detail).joinToString(" ") }

    @Test
    fun domainCodeMayNotReachInfrastructure() {
        assertEquals(
            listOf(
                "cat.coeli.alerts.sample.domain.ReportKt → java.io Domain → Infrastructure: java.io.File",
            ),
            found(FindingKind.ForbiddenDependencies),
        )
    }

    @Test
    fun infrastructureFilesThatNeedNothingOfItArePlacedTooLow() {
        assertEquals(
            listOf(
                "cat/coeli/alerts/sample/infrastructure/Pure.kt needs nothing of Infrastructure",
            ),
            found(FindingKind.ClassesPlacedTooLow),
        )
    }

    @Test
    fun librariesTheCatalogueDoesNotListHaveNoLayer() {
        assertEquals(
            listOf(
                "java.util.zip used by cat.coeli.alerts.sample.infrastructure.UsesAnUncataloguedLibrary",
            ),
            found(FindingKind.LibrariesWithoutRole),
        )
    }

    @Test
    fun packagesThatDependOnEachOtherFormACycle() {
        assertEquals(
            listOf("cat.coeli.alerts.sample.cycle.a ↔ cat.coeli.alerts.sample.cycle.b"),
            found(FindingKind.PackageCycles),
        )
    }

    @Test
    fun packagesHangFromTheirModulesRootPackage() {
        assertEquals(
            listOf("cat.coeli.alerts.elsewhere expected under cat.coeli.alerts.sample"),
            found(FindingKind.PackagesOutsideModuleRoot),
        )
    }

    @Test
    fun membersNeedOnlyTheVisibilityTheirUsesNeed() {
        assertEquals(
            listOf(
                "cat.coeli.alerts.sample.cycle.b.B() public → internal",
                "cat.coeli.alerts.sample.domain.Report.decorated() public → private",
                "cat.coeli.alerts.sample.domain.Report.title public → private",
                "cat.coeli.alerts.sample.domain.ReportKt.readsAFile(String) public → internal",
                "cat.coeli.alerts.sample.domain.ReportKt.usedByOwnTests() public → internal",
                "cat.coeli.alerts.sample.domain.ReportKt.usedInModule() public → internal",
            ),
            found(FindingKind.MembersTooVisible),
        )
    }

    @Test
    fun membersNothingUsesButOtherModulesTestsAreUnused() {
        assertEquals(
            listOf(
                "cat.coeli.alerts.elsewhere.Stray()",
                "cat.coeli.alerts.sample.domain.ReportKt.neverUsed()",
                "cat.coeli.alerts.sample.domain.ReportKt.usedByOtherModulesTests() used only by tests of other modules",
            ),
            found(FindingKind.UnusedMembers),
        )
    }

    @Test
    fun dependenciesAreDeclaredWithoutStrings() {
        assertEquals(
            listOf("implementation com.example:library", "project :app"),
            found(FindingKind.StringDependencies),
        )
    }

    @Test
    fun onlyTheCatalogueAllowsSuppressions() {
        assertEquals(
            listOf(
                "MagicNumber modules/sample/src/main/kotlin/cat/coeli/alerts/sample/domain/Suppressed.kt",
            ),
            found(FindingKind.ForbiddenSuppressions),
        )
    }
}
