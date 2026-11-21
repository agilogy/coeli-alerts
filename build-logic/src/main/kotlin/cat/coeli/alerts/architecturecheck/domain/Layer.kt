package cat.coeli.alerts.architecturecheck.domain

/**
 * From the most abstract to the most concrete. Code may depend on its own layer and on the layers
 * above it.
 */
enum class Layer {
    /** General-purpose code, with no business meaning and no infrastructure. */
    Helper,
    /** Business types and rules. */
    Domain,
    /** Adapters to the outside world: databases, HTTP APIs, files, the environment, the console. */
    Infrastructure;

    internal fun mayDependOn(target: Layer): Boolean = target <= this
}

private val LAYER_SEGMENTS =
    mapOf("domain" to Layer.Domain, "infrastructure" to Layer.Infrastructure)

/**
 * The layer of one of our packages is named by its deepest `domain` or `infrastructure` segment, so
 * `x.domain.alerts` is Domain and `x.infrastructure.postgres` Infrastructure. A package with
 * neither holds helpers.
 */
internal fun layerOf(packageName: String): Layer =
    packageName.split('.').mapNotNull { LAYER_SEGMENTS[it] }.lastOrNull() ?: Layer.Helper

/**
 * A class placed in Infrastructure that needs nothing of it belongs higher up. Domain and Helper
 * can't be told apart.
 */
internal fun isPlacedTooLow(declared: Layer, needed: Layer): Boolean =
    declared == Layer.Infrastructure && needed < Layer.Infrastructure
