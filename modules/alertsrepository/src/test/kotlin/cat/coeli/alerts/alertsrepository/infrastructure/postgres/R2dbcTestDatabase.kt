package cat.coeli.alerts.alertsrepository.infrastructure.postgres

import io.zonky.test.db.postgres.junit5.PreparedDbExtension
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase

internal fun PreparedDbExtension.r2dbcDatabase(): R2dbcDatabase =
    R2dbcDatabase.connect(
        url = "r2dbc:postgresql://localhost:${connectionInfo.port}/${connectionInfo.dbName}",
        user = connectionInfo.user,
    )
