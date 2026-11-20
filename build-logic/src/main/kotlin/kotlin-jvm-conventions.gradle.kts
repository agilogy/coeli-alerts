import dev.detekt.gradle.Detekt
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    org.jetbrains.kotlin.jvm
    com.ncorti.ktfmt.gradle
    dev.detekt
    org.jetbrains.kotlinx.kover
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
    allRules = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

// The detekt tasks with type resolution (detektMain, detektTest…) apply more rules than the plain `detekt`
// task, which they replace in `check`.
tasks.named("check") {
    setDependsOn(dependsOn.filterNot { it is TaskProvider<*> && it.name == "detekt" })
    dependsOn(tasks.withType<Detekt>().matching { it.name != "detekt" && !it.name.endsWith("SourceSet") })
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
    // Netty (used by r2dbc-postgresql) loads native libraries, which JDK 24+ warns about unless allowed.
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
