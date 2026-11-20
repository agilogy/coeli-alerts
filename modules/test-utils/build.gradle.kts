plugins { alias(libs.plugins.conventions.kotlin.jvm) }

dependencies {
    api(libs.embedded.postgres)
    implementation(platform(libs.junit.bom))
    implementation(libs.junit.jupiter.api)
    implementation(platform(libs.embedded.postgres.binaries.bom))
    runtimeOnly(libs.embedded.postgres.binaries.darwin.arm64v8)
    runtimeOnly(libs.bundles.flyway)
    runtimeOnly(libs.postgresql)
    // Logging backend for tests, configured by logback-test.xml. Among others, it shows the output
    // of
    // embedded PostgreSQL (initdb, postgres) when it fails to start.
    runtimeOnly(libs.logback.classic)
}
