package cat.coeli.alerts.architecturecheck.domain

/**
 * The visibilities a member is narrowed to, from the narrowest. `protected` members are not judged.
 */
enum class Visibility {
    Private,
    Internal,
    Public;

    internal val keyword: String
        get() = name.lowercase()
}

/** Where a use of a member comes from, seen from the member. */
enum class UseScope {
    /**
     * What `private` reaches: the class and the classes nested in it, or the file of a top-level
     * member.
     */
    Declaration,
    /** Any source set of the member's module. */
    Module,
    OtherModule,
    /**
     * Doesn't count: other modules' tests reach production code through the test fixtures of its
     * module.
     */
    OtherModuleTest,
}

sealed interface Verdict {
    data class TooVisible(val current: Visibility, val needed: Visibility) : Verdict

    data class Unused(val onlyTestsOfOtherModules: Boolean) : Verdict
}

/**
 * What a member's uses say about its visibility, `null` when nothing. [current] is the narrowest of
 * the member's and of the classes around it. [overriders] are where the overrides of an open or
 * abstract member are, which need to see it; such a member can't be private ([canBePrivate]). In an
 * app, which no module depends on, `public` is as good as `internal`.
 */
internal fun verdictOf(
    current: Visibility,
    uses: Set<UseScope>,
    overriders: Set<UseScope>,
    canBePrivate: Boolean,
    isApp: Boolean,
): Verdict? =
    when (val neededByUses = neededBy(uses, canBePrivate)) {
        null -> Verdict.Unused(onlyTestsOfOtherModules = UseScope.OtherModuleTest in uses)
        else ->
            listOfNotNull(neededByUses, neededBy(overriders, canBePrivate = false)).max().let {
                needed ->
                Verdict.TooVisible(current, needed).takeIf {
                    needed < current && !(isApp && needed == Visibility.Internal)
                }
            }
    }

private fun neededBy(scopes: Set<UseScope>, canBePrivate: Boolean): Visibility? =
    when {
        UseScope.OtherModule in scopes -> Visibility.Public
        UseScope.Module in scopes -> Visibility.Internal
        UseScope.Declaration in scopes ->
            if (canBePrivate) Visibility.Private else Visibility.Internal
        else -> null
    }
