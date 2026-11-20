plugins {
    base
    org.jetbrains.kotlinx.kover
}

dependencies {
    subprojects.forEach { kover(it) }
}

private val minimumLineCoveragePercent = 80

kover {
    reports {
        filters {
            excludes {
                packages("cat.coeli.alerts.testutils")
                // The apps' entry points, which only wire the dependencies together.
                classes("*.MainKt")
            }
        }
        verify {
            rule {
                minBound(minimumLineCoveragePercent)
            }
        }
    }
}
