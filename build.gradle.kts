// Loads build-logic's plugins once in the root project's classloader, shared by all subprojects.
plugins {
    alias(libs.plugins.conventions.kotlin.jvm) apply false
    alias(libs.plugins.conventions.coverage)
    alias(libs.plugins.architecture.check)
    alias(libs.plugins.kotlin.serialization) apply false
}

subprojects
    .filter { it.path != projects.testUtils.path }
    .forEach { module ->
        module.pluginManager.withPlugin(libs.plugins.conventions.kotlin.jvm.get().pluginId) {
            module.dependencies.add("testImplementation", projects.testUtils)
        }
    }
