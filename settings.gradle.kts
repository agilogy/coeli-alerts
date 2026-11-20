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

listOf("apps", "modules")
    .flatMap { rootDir.resolve(it).listFiles().orEmpty().toList() }
    .filter { it.isDirectory && it.resolve("build.gradle.kts").isFile }
    .forEach { directory ->
        include(directory.name)
        project(":${directory.name}").projectDir = directory
    }
