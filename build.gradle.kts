// Loads build-logic's plugins once in the root project's classloader, shared by all subprojects.
plugins {
    alias(libs.plugins.conventions.kotlin.jvm) apply false
    alias(libs.plugins.conventions.coverage)
    alias(libs.plugins.conventions.architecture)
    alias(libs.plugins.kotlin.serialization) apply false
}

subprojects
    .filter { it.path != projects.testUtils.path }
    .forEach { module ->
        module.pluginManager.withPlugin(libs.plugins.conventions.kotlin.jvm.get().pluginId) {
            module.dependencies.add("testImplementation", projects.testUtils)
        }
    }

// The architecture check and the other build logic have tests of their own.
tasks.named("check") { dependsOn(gradle.includedBuild("build-logic").task(":check")) }
