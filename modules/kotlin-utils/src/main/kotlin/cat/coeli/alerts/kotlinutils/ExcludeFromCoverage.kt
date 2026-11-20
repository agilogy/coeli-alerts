package cat.coeli.alerts.kotlinutils

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS, AnnotationTarget.FILE)
annotation class ExcludeFromCoverage
