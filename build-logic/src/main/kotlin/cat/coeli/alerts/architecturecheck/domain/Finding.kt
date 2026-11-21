package cat.coeli.alerts.architecturecheck.domain

internal const val CATALOGUE_FILE = "config/architecture/catalogue.jsonc"

/** A line of a file, by its path from the repository root. */
data class SourceLine(val path: String, val line: Int)

/**
 * [module] is the module's directory from the repository root, `.` for the root build script.
 * [subject] is what is wrong; [detail] what the reader needs to fix it.
 */
data class Finding(
    val kind: FindingKind,
    val module: String,
    val subject: String,
    val detail: String? = null,
    val source: SourceLine? = null,
)

enum class FindingKind(val title: String, val explanation: String) {
    PackageCycles(
        "Package cycles",
        "Packages that depend on each other in a cycle, so none of them can be understood or changed without the " +
            "others. Move the type they all reach for to a package below them, or invert one of the dependencies " +
            "behind an interface.",
    ),
    ForbiddenDependencies(
        "Dependencies the layering forbids",
        "A class reaching code of a layer below its own, ours or a library's, shown with the layer of each. Domain " +
            "knows nothing of infrastructure, and a helper knows neither. Move the class to the package of the layer " +
            "it needs, or invert the dependency: an interface in the higher layer, its implementation in the lower " +
            "one, injected by the caller.",
    ),
    ClassesPlacedTooLow(
        "Classes placed lower than they need",
        "A class in an `infrastructure` package that reaches nothing of infrastructure, directly or through other " +
            "classes of ours. Move it to the `domain` package of its module.",
    ),
    LibrariesWithoutRole(
        "Libraries without a layer",
        "A library package the catalogue doesn't list, so the layering can't be checked against it. Add the " +
            "shortest prefix that settles it to `libraries` in `$CATALOGUE_FILE`, with the layer it plays for the " +
            "code that uses it.",
    ),
    PackagesOutsideModuleRoot(
        "Packages outside their module's root package",
        "A package that doesn't hang from `cat.coeli.alerts.` followed by the module's directory name without " +
            "dashes, shown as the branch where it leaves it. Move the package under the root.",
    ),
    MembersTooVisible(
        "Members more visible than their uses need",
        "A function, property or constructor of `src/main` or `src/testFixtures` whose uses would compile with a " +
            "narrower visibility: `private` when only its class (with the classes nested in it) or, for a top-level " +
            "member, its file uses it; `internal` when only its module does, tests included. In an app, `public` is " +
            "as good as `internal`. Narrow the visibility.",
    ),
    UnusedMembers(
        "Members nobody uses",
        "A function, property or constructor of `src/main` or `src/testFixtures` that nothing uses, or only tests " +
            "of other modules. Delete it, together with the tests that exercise it.",
    ),
    StringDependencies(
        "Dependencies declared with strings",
        "A dependency a build script declares with coordinates (`\"group:artifact:version\"`) or a project path " +
            "(`project(\":module\")`). Use the version catalogue (`libs.…`) or the type-safe project accessors " +
            "(`projects.…`).",
    ),
    ForbiddenSuppressions(
        "Suppressed warnings",
        "A `@Suppress` naming a rule `allowedSuppressions` in `$CATALOGUE_FILE` doesn't allow. Remove it and fix " +
            "the code the rule reports.",
    ),
}
