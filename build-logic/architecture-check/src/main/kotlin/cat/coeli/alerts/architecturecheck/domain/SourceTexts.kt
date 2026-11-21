package cat.coeli.alerts.architecturecheck.domain

private const val OWN_PACKAGE = "cat.coeli.alerts"

/** `modules/test-utils` hangs from `cat.coeli.alerts.testutils`. */
internal fun rootPackageOf(module: String): String =
    "$OWN_PACKAGE." + module.substringAfterLast('/').replace("-", "")

/** Each branch that leaves the module's root package, cut at the segment where it leaves it. */
internal fun packagesOutsideRootOf(module: String, packageNames: Set<String>): List<String> {
    val root = rootPackageOf(module).split('.')
    return packageNames
        .map { it.split('.') }
        .filterNot { it.size >= root.size && it.take(root.size) == root }
        .map { segments ->
            val shared =
                segments
                    .zip(root)
                    .takeWhile { (segment, rootSegment) -> segment == rootSegment }
                    .size
            segments.take(shared + 1).joinToString(".")
        }
        .distinct()
        .sorted()
}

/** A declaration of a build script, or a suppression of a source file, at a line counted from 1. */
data class TextFinding(val subject: String, val line: Int)

/**
 * A call taking a string literal, maybe through `platform(…)`, `enforcedPlatform(…)` or
 * `testFixtures(…)`.
 */
private val CALL_WITH_LITERAL =
    Regex("""\b(\w+)\s*\(\s*(?:(?:platform|enforcedPlatform|testFixtures)\s*\(\s*)?"([^"\n]*)"""")

/** `group:artifact[:version]`, with neither blanks nor slashes: not prose, not a URL. */
private val COORDINATES = Regex("""[\w.-]+:[\w.-]+(:[^\s/"]*)*""")

private val PROJECT_PATH = Regex(""":[\w:-]*""")

/** Dependencies declared with coordinates or `project(":path")`, named without their version. */
internal fun stringDependenciesOf(buildScript: String): List<TextFinding> =
    CALL_WITH_LITERAL.findAll(buildScript)
        .filterNot { buildScript.isCommentedOut(it.range.first) }
        .mapNotNull { match ->
            val (call, literal) = match.destructured
            when {
                call == "project" && PROJECT_PATH.matches(literal) -> "project $literal"
                call != "project" && COORDINATES.matches(literal) ->
                    "$call ${literal.split(':').take(2).joinToString(":")}"
                else -> null
            }?.let { TextFinding(it, buildScript.lineOf(match.range.first)) }
        }
        .toList()

private val SUPPRESSION = Regex("""@(?:file:)?Suppress(?:Warnings)?\s*\(([^)]*)\)""")

private val STRING_LITERAL = Regex(""""([^"\n]*)"""")

/**
 * Every rule a `@Suppress`, `@file:Suppress` or `@SuppressWarnings` of a Kotlin file names but
 * [allowed].
 */
internal fun forbiddenSuppressionsOf(source: String, allowed: Set<String>): List<TextFinding> =
    SUPPRESSION.findAll(source)
        .filterNot { source.isCommentedOut(it.range.first) }
        .flatMap { match ->
            STRING_LITERAL.findAll(match.groupValues[1])
                .map { it.groupValues[1] }
                .filter { rule -> rule.removePrefix("detekt:") !in allowed }
                .map { rule -> TextFinding(rule, source.lineOf(match.range.first)) }
        }
        .toList()

private fun String.lineOf(index: Int): Int = substring(0, index).count { it == '\n' } + 1

private fun String.isCommentedOut(index: Int): Boolean =
    substring(lastIndexOf('\n', index - 1) + 1, index).contains("//")
