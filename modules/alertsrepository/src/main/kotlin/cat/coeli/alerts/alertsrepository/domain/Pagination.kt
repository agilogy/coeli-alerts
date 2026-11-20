package cat.coeli.alerts.alertsrepository.domain

/** A page of results: skip [offset] results and return at most [size]. */
data class Pagination(val offset: Long, val size: Int) {
    init {
        require(offset >= 0) { "Pagination offset must not be negative, but was $offset" }
        require(size > 0) { "Pagination size must be positive, but was $size" }
    }
}
