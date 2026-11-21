plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.ktfmt.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.kover.gradle.plugin)
    // Makes the type-safe `libs` catalog usable from precompiled script plugins
    // (https://github.com/gradle/gradle/issues/15383).
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))

    implementation(libs.archunit)
    implementation(libs.kotlin.metadata.jvm)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test { useJUnitPlatform() }
