import cat.coeli.alerts.architecturecheck.infrastructure.ArchitectureCheck

plugins { base }

val architectureCheck =
    tasks.register<ArchitectureCheck>("architectureCheck") {
        group = "verification"
        description =
            "Checks the layering, package names, member visibility and build scripts of every module"
        repositoryRoot.set(layout.projectDirectory)
        catalogue.set(layout.projectDirectory.file("config/architecture/catalogue.jsonc"))
        analysedFiles.from(layout.projectDirectory.file("build.gradle.kts"))
        reportDirectory.set(layout.buildDirectory.dir("reports/architecture"))
        sourceLinkBase.set(
            providers
                .environmentVariable("GITHUB_SERVER_URL")
                .zip(
                    providers.environmentVariable("GITHUB_REPOSITORY"),
                ) { server, repository ->
                    "$server/$repository"
                }
                .zip(providers.environmentVariable("GITHUB_SHA")) { repository, commit ->
                    "$repository/blob/$commit"
                },
        )
    }

private val compilations = setOf("classes", "testFixturesClasses", "testClasses")

subprojects.forEach { module ->
    module.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        architectureCheck.configure {
            dependsOn(module.tasks.matching { it.name in compilations })
            analysedFiles.from(
                module.layout.buildDirectory.dir("classes"),
                module.layout.projectDirectory.dir("src"),
                module.layout.projectDirectory.file("build.gradle.kts"),
            )
        }
    }
}

tasks.named("check") { dependsOn(architectureCheck) }
