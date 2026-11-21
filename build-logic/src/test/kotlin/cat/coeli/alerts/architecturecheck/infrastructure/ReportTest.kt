package cat.coeli.alerts.architecturecheck.infrastructure

import cat.coeli.alerts.architecturecheck.domain.Finding
import cat.coeli.alerts.architecturecheck.domain.FindingKind
import cat.coeli.alerts.architecturecheck.domain.Layer
import cat.coeli.alerts.architecturecheck.domain.SourceLine
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReportTest {
    private val finding =
        Finding(
            FindingKind.MembersTooVisible,
            "modules/weather",
            "a.B.c(List<String>)",
            "public → internal",
            SourceLine("modules/weather/src/main/kotlin/a/B.kt", 7),
        )

    @Test
    fun aReportWithoutFindingsSaysSo() {
        val markdown = Report(emptyList(), sourceLinkBase = null).markdown()

        assertEquals("## Architecture check\n\n✅ No findings.\n", markdown)
    }

    @Test
    fun theMarkdownLinksEachFindingToItsSource() {
        val markdown =
            Report(listOf(finding), sourceLinkBase = "https://github.com/o/r/blob/abc").markdown()

        assertContains(
            markdown,
            "| modules/weather | `a.B.c(List<String>)` | public → internal | " +
                "[B.kt:7](https://github.com/o/r/blob/abc/modules/weather/src/main/kotlin/a/B.kt#L7) |",
        )
    }

    @Test
    fun theHtmlEscapesWhatItShows() {
        val html = Report(listOf(finding), sourceLinkBase = null).html()

        assertContains(html, "<code>a.B.c(List&lt;String&gt;)</code>")
    }

    @Test
    fun theTextListsTheFindingsByKind() {
        val text = Report(listOf(finding), sourceLinkBase = null).text()

        assertEquals(
            "Architecture check: 1 finding. The build fails until none is left.\n\n" +
                "Members more visible than their uses need:\n" +
                "  a.B.c(List<String>) public → internal (modules/weather/src/main/kotlin/a/B.kt:7)",
            text,
        )
    }
}

class CatalogueFileTest {
    @Test
    fun theCatalogueIsJsonWithComments() {
        val catalogue =
            readCatalogue(
                """
                {
                    // Layers of libraries
                    "libraries": { "io.ktor": "Infrastructure" },
                    "allowedSuppressions": ["JUnitMalformedDeclaration"],
                    "testSupportModules": ["modules/test-utils"]
                }
                """,
            )

        assertEquals(
            Layer.Infrastructure,
            catalogue.libraryLayerOf("io.ktor.client.HttpClient", "io.ktor.client"),
        )
        assertEquals(setOf("JUnitMalformedDeclaration"), catalogue.allowedSuppressions)
        assertEquals(setOf("modules/test-utils"), catalogue.testSupportModules)
    }

    @Test
    fun aLayerNobodyHasHeardOfIsAnError() {
        assertFailsWith<IllegalArgumentException> {
            readCatalogue("""{ "libraries": { "io.ktor": "Adapter" } }""")
        }
    }
}
