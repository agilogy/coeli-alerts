plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    // Makes the type-safe `libs` catalog usable from precompiled script plugins
    // (https://github.com/gradle/gradle/issues/15383).
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
