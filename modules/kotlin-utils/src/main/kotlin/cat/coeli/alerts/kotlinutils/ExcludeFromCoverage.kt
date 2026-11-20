package cat.coeli.alerts.kotlinutils

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class ExcludeFromCoverage
