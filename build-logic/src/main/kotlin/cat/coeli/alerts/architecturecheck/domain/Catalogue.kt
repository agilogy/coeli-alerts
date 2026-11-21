package cat.coeli.alerts.architecturecheck.domain

/**
 * What the check can't tell from our code. [libraries] gives each library its layer by package
 * prefix, class (`java.lang.System`, `kotlin.random.Random$Default`) or member without parameters
 * (`java.lang.System#getenv`); the most precise entry wins. [testSupportModules] exist for other
 * modules' tests, whose uses of them count.
 */
class Catalogue(
    private val libraries: Map<String, Layer>,
    internal val allowedSuppressions: Set<String> = emptySet(),
    internal val testSupportModules: Set<String> = emptySet(),
) {
    internal fun libraryLayerOf(
        className: String,
        packageName: String,
        member: String? = null,
    ): Layer? =
        (listOfNotNull(member?.let { "$className#$it" }) +
                enclosingClassesOf(className) +
                prefixesOf(packageName))
            .firstNotNullOfOrNull { libraries[it] }
}

/** `a.B$C$D`, then `a.B$C`, then `a.B`. */
private fun enclosingClassesOf(className: String): List<String> =
    className.split('$').let { parts ->
        (parts.size downTo 1).map { parts.take(it).joinToString("$") }
    }

/** `a.b.c`, then `a.b`, then `a`. */
private fun prefixesOf(packageName: String): List<String> =
    packageName.split('.').let { segments ->
        (segments.size downTo 1).map { segments.take(it).joinToString(".") }
    }
