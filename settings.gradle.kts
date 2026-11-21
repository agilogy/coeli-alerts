pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        // The architecture-check plugin's dependencies.
        mavenCentral()
        // The architecture-check plugin. GitHub Packages asks for a token with read:packages even to read:
        // GitHubPackagesUsername and GitHubPackagesPassword in ~/.gradle/gradle.properties.
        maven("https://maven.pkg.github.com/agilogy/architecture-check") {
            name = "GitHubPackages"
            credentials(PasswordCredentials::class)
            content { includeGroupByRegex("""cat\.coeli(\..*)?""") }
        }
    }
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
