plugins {
    base
    org.jetbrains.kotlinx.kover
}

dependencies {
    subprojects.forEach { kover(project(it.path)) }
}

private val minimumLineCoveragePercent = 80

kover {
    reports {
        filters {
            excludes {
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
