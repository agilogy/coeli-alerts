package cat.coeli.alerts.alertsrepository.infrastructure.postgres

import cat.coeli.alerts.alertsrepository.domain.AlertRepository
import cat.coeli.alerts.alertsrepository.domain.AlertRepositoryContractTest
import cat.coeli.alerts.testutils.infrastructure.embeddedPostgres
import io.zonky.test.db.postgres.junit5.PreparedDbExtension
import org.junit.jupiter.api.extension.RegisterExtension

class PostgresAlertRepositoryTest : AlertRepositoryContractTest() {

    @Suppress("JUnitMalformedDeclaration") // Instance field on purpose: see embeddedPostgres().
    @JvmField
    @RegisterExtension
    val db: PreparedDbExtension = embeddedPostgres()

    override fun newRepository(): AlertRepository = PostgresAlertRepository(db.r2dbcDatabase())
}
