pluginManagement {
    includeBuild("build-logic")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "coeli-alerts"

// Every directory with a build.gradle.kts, at the top level or inside modules/, is a module named after
// its directory (`:alertsrepository`, not `:modules:alertsrepository`), except the included builds.
val notModules = setOf("build-logic")

(rootDir.listFiles()!! + rootDir.resolve("modules").listFiles().orEmpty())
    .filter { it.isDirectory && it.name !in notModules && it.resolve("build.gradle.kts").isFile }
    .forEach { directory ->
        include(directory.name)
        project(":${directory.name}").projectDir = directory
    }
