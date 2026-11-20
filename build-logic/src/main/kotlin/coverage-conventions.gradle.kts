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
            }
        }
        verify {
            rule {
                minBound(minimumLineCoveragePercent)
            }
        }
    }
}
