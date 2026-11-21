package cat.coeli.alerts.testutils.infrastructure

import io.zonky.test.db.postgres.embedded.FlywayPreparer
import io.zonky.test.db.postgres.junit5.EmbeddedPostgresExtension
import io.zonky.test.db.postgres.junit5.PreparedDbExtension

/**
 * Register it as an instance field, not a static one, even if IntelliJ suggests it: as an instance
 * field each test gets its own fresh database; as a static one all tests in the class share one.
 */
fun embeddedPostgres(): PreparedDbExtension =
    EmbeddedPostgresExtension.preparedDatabase(FlywayPreparer.forClasspathLocation("db/migration"))
        // initdb fails if LANG/LC_* name a locale that doesn't exist (e.g. en_ES.UTF-8).
        .customize { it.setLocaleConfig("locale", "C") }
