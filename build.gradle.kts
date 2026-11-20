// Loads build-logic's plugins once in the root project's classloader, shared by all subprojects.
plugins {
    alias(libs.plugins.conventions.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
