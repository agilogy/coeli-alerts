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
                annotatedBy("cat.coeli.alerts.kotlinutils.ExcludeFromCoverage")
            }
        }
        verify {
            rule {
                minBound(minimumLineCoveragePercent)
            }
        }
    }
}
