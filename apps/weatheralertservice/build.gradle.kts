plugins {
    alias(libs.plugins.conventions.kotlin.jvm)
    application
}

dependencies {
    implementation(projects.alertsrepository)
    implementation(projects.weather)
    implementation(libs.exposed.r2dbc)
    implementation(libs.bundles.flyway)
    implementation(libs.kotlin.logging)
    runtimeOnly(libs.logback.classic)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.r2dbc.postgresql)
    runtimeOnly(libs.r2dbc.pool)

    testImplementation(projects.testUtils)
    testImplementation(testFixtures(projects.alertsrepository))
    testImplementation(testFixtures(projects.weather))
    testImplementation(libs.kotlinx.coroutines.test)
}

application {
    mainClass = "cat.coeli.alerts.weatheralertservice.MainKt"
    applicationDefaultJvmArgs = listOf("-Dkotlin-logging.logStartupMessage=false")
}
