import dev.detekt.gradle.Detekt

// build-logic can't apply its own convention plugins: this repeats kotlin-jvm-conventions.
plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.ktfmt)
    alias(libs.plugins.detekt)
}

kotlin { compilerOptions { allWarningsAsErrors = true } }

ktfmt { kotlinLangStyle() }

detekt {
    buildUponDefaultConfig = true
    allRules = true
    config.setFrom(rootProject.file("../config/detekt/detekt.yml"))
}

tasks.named("check") {
    setDependsOn(dependsOn.filterNot { it is TaskProvider<*> && it.name == "detekt" })
    dependsOn(
        tasks.withType<Detekt>().matching { it.name != "detekt" && !it.name.endsWith("SourceSet") },
    )
}

dependencies {
    compileOnly(gradleApi())
    implementation(libs.archunit)
    implementation(libs.kotlin.metadata.jvm)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test { useJUnitPlatform() }
