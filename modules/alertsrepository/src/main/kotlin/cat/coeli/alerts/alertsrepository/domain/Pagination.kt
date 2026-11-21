package cat.coeli.alerts.alertsrepository.domain

data class Pagination(internal val offset: Long, internal val size: Int) {
    init {
        require(offset >= 0) { "Pagination offset must not be negative, but was $offset" }
        require(size > 0) { "Pagination size must be positive, but was $size" }
    }
}
