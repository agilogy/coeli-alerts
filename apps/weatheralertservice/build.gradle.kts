plugins {
    alias(libs.plugins.conventions.kotlin.jvm)
    application
}

dependencies {
    implementation(projects.alertsrepository)
    implementation(projects.weather)
    implementation(libs.bundles.exposed)
    implementation(libs.hikaricp)
    implementation(libs.bundles.flyway)
    implementation(libs.kotlin.logging)
    runtimeOnly(libs.logback.classic)
    runtimeOnly(libs.postgresql)

    testImplementation(testFixtures(projects.alertsrepository))
    testImplementation(testFixtures(projects.weather))
    testImplementation(libs.kotlinx.coroutines.test)
}

application {
    mainClass = "cat.coeli.alerts.weatheralertservice.MainKt"
    applicationDefaultJvmArgs = listOf("-Dkotlin-logging.logStartupMessage=false")
}
