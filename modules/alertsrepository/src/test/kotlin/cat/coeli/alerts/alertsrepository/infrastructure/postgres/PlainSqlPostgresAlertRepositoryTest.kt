package cat.coeli.alerts.alertsrepository.infrastructure.postgres

import cat.coeli.alerts.alertsrepository.domain.AlertLevel
import cat.coeli.alerts.alertsrepository.domain.AlertRepository
import cat.coeli.alerts.alertsrepository.domain.AlertRepositoryContractTest
import cat.coeli.alerts.alertsrepository.domain.Pagination
import cat.coeli.alerts.testutils.infrastructure.embeddedPostgres
import io.zonky.test.db.postgres.junit5.PreparedDbExtension
import org.junit.jupiter.api.extension.RegisterExtension

class PlainSqlPostgresAlertRepositoryTest : AlertRepositoryContractTest() {

    @Suppress("JUnitMalformedDeclaration") // Instance field on purpose: see embeddedPostgres().
    @JvmField
    @RegisterExtension
    val db: PreparedDbExtension = embeddedPostgres()

    override fun newRepository(): AlertRepository {
        val repository = PostgresAlertRepository(db.r2dbcDatabase())
        return object : AlertRepository by repository {
            override suspend fun search(minimumLevel: AlertLevel, pagination: Pagination) =
                repository.search2(minimumLevel, pagination)
        }
    }
}
