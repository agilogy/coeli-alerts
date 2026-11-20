plugins {
    alias(libs.plugins.conventions.kotlin.jvm)
    alias(libs.plugins.exposed)
    `java-test-fixtures`
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.bundles.exposed)
    implementation(libs.kotlin.logging)

    testImplementation(projects.testUtils)
    testImplementation(libs.kotlinx.coroutines.test)
}

exposed {
    migrations {
        tablesPackage = "cat.coeli.alerts.alertsrepository.infrastructure.postgres"
        testContainersImageName = "postgres:19"
    }
}
