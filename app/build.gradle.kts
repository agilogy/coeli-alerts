plugins {
    alias(libs.plugins.conventions.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

dependencies {
    implementation(projects.core)
    implementation(libs.bundles.ktor.client)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bundles.exposed)
    implementation(libs.hikaricp)
    runtimeOnly(libs.postgresql)
}

application {
    mainClass = "cat.coeli.alerts.app.MainKt"
}
