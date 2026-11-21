package cat.coeli.alerts.architecturecheck.infrastructure

import cat.coeli.alerts.architecturecheck.domain.Finding
import cat.coeli.alerts.architecturecheck.domain.FindingKind
import cat.coeli.alerts.architecturecheck.domain.SourceLine

private const val TITLE = "Architecture check"

/** [sourceLinkBase] turns each file and line into a link, such as one to the commit on GitHub. */
internal class Report(private val findings: List<Finding>, private val sourceLinkBase: String?) {
    private val byKind = findings.groupBy { it.kind }

    private val summary: String
        get() =
            if (findings.isEmpty()) "No findings."
            else
                "${findings.size} finding${if (findings.size == 1) "" else "s"}. The build fails until none is left."

    fun text(): String =
        (listOf("$TITLE: $summary") +
                byKind.flatMap { (kind, found) ->
                    listOf("", "${kind.title}:") +
                        found.map { finding ->
                            "  ${finding.subject}" +
                                listOfNotNull(
                                        finding.detail,
                                        finding.source?.let { "(${it.path}:${it.line})" },
                                    )
                                    .joinToString(" ", prefix = " ")
                                    .trimEnd()
                        }
                })
            .joinToString("\n")

    fun markdown(): String =
        (listOf("## $TITLE", "", (if (findings.isEmpty()) "✅ " else "❌ ") + summary) +
                byKind.flatMap { (kind, found) ->
                    listOf("", "### ${kind.title} (${found.size})", "", kind.explanation, "") +
                        "| Module | Finding | Detail | Source |" +
                        "|---|---|---|---|" +
                        found.map { finding ->
                            listOf(
                                    finding.module.markdownCell(),
                                    "`${finding.subject.replace("`", "'")}`".markdownCell(),
                                    finding.detail.orEmpty().markdownCell(),
                                    finding.source?.let {
                                        "[${it.path.substringAfterLast('/')}:${it.line}](${linkTo(it)})"
                                    } ?: "",
                                )
                                .joinToString(" | ", prefix = "| ", postfix = " |")
                        }
                })
            .joinToString("\n", postfix = "\n")

    fun html(): String =
        """
        |<!DOCTYPE html>
        |<html lang="en">
        |<head>
        |<meta charset="utf-8">
        |<title>$TITLE</title>
        |<style>
        |body { font-family: system-ui, sans-serif; margin: 2rem auto; max-width: 70rem; padding: 0 1rem; color: #1f2328; }
        |table { border-collapse: collapse; width: 100%; margin-bottom: 2rem; }
        |th, td { border: 1px solid #d0d7de; padding: .35rem .6rem; text-align: left; vertical-align: top; }
        |th { background: #f6f8fa; }
        |code { font-size: .85rem; word-break: break-all; }
        |.explanation { color: #59636e; }
        |</style>
        |</head>
        |<body>
        |<h1>$TITLE</h1>
        |<p>${summary.escaped()}</p>
        |${byKind.entries.joinToString("\n") { (kind, found) -> htmlSection(kind, found) }}
        |</body>
        |</html>
        |"""
            .trimMargin()

    private fun htmlSection(kind: FindingKind, found: List<Finding>): String =
        """
        |<h2>${kind.title.escaped()} (${found.size})</h2>
        |<p class="explanation">${kind.explanation.escaped()}</p>
        |<table>
        |<tr><th>Module</th><th>Finding</th><th>Detail</th><th>Source</th></tr>
        |${found.joinToString("\n") { htmlRow(it) }}
        |</table>
        |"""
            .trimMargin()

    private fun htmlRow(finding: Finding): String {
        val source =
            finding.source
                ?.let {
                    """<a href="${linkTo(it).escaped()}">${"${it.path}:${it.line}".escaped()}</a>"""
                }
                .orEmpty()
        return "<tr><td>${finding.module.escaped()}</td><td><code>${finding.subject.escaped()}</code></td>" +
            "<td>${finding.detail.orEmpty().escaped()}</td><td>$source</td></tr>"
    }

    private fun linkTo(source: SourceLine): String =
        sourceLinkBase?.let { "$it/${source.path}#L${source.line}" } ?: source.path
}

private fun String.markdownCell(): String = replace("|", "\\|").replace("\n", " ")

private fun String.escaped(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
