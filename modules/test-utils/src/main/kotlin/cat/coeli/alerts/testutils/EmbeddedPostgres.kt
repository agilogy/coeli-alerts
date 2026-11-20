package cat.coeli.alerts.testutils

import io.zonky.test.db.postgres.embedded.FlywayPreparer
import io.zonky.test.db.postgres.junit5.EmbeddedPostgresExtension
import io.zonky.test.db.postgres.junit5.PreparedDbExtension

/**
 * Embedded PostgreSQL for repository tests: one server per JVM, with the Flyway migrations found in
 * classpath:db/migration applied once to a template database.
 *
 * Register it as an instance field, not a static one:
 * ```
 * @Suppress("JUnitMalformedDeclaration") @JvmField @RegisterExtension val db = embeddedPostgres()
 * ```
 *
 * As an instance field each test gets its own fresh database cloned from the template
 * (`db.testDatabase`); as a static one all tests in the class share a single database. IntelliJ
 * suggests making it static because the extension also implements BeforeAllCallback, which is
 * exactly the behaviour to avoid here.
 */
fun embeddedPostgres(): PreparedDbExtension =
    EmbeddedPostgresExtension.preparedDatabase(FlywayPreparer.forClasspathLocation("db/migration"))
        // An explicit locale, so initdb doesn't depend on the LANG/LC_* of the machine running the
        // tests (initdb fails if they name a locale that doesn't exist, e.g. en_ES.UTF-8).
        .customize { it.setLocaleConfig("locale", "C") }
