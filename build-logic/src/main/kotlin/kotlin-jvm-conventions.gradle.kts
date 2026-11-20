import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    org.jetbrains.kotlin.jvm
    com.ncorti.ktfmt.gradle
    dev.detekt
}

val libs = the<LibrariesForLibs>()

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())
    compilerOptions {
        allWarningsAsErrors = true
    }
}

ktfmt {
    kotlinLangStyle()
}

detekt {
    buildUponDefaultConfig = true
}

dependencies {
    testImplementation(libs.kotlin.test)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("kotlin-logging.logStartupMessage", "false")
}
