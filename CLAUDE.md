# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Kotlin backend, Gradle multi-module. A developer coming from TypeScript and learning Kotlin works on this
repo: keep the code plain and idiomatic, and don't bring in advanced techniques or libraries (Arrow, `Either`,
property-based testing, …) without asking.

## Commands

```bash
./gradlew build                      # compile, tests, ktfmt check and detekt: must pass before considering work done
./gradlew ktfmtFormat                # format all Kotlin sources and .kts scripts
./gradlew detekt                     # lint only
./gradlew :alertsrepository:test --tests 'cat.coeli.alerts.alertsrepository.infrastructure.postgres.PostgresAlertRepositoryTest.searchBreaksTiesInOccurredAtById'
./gradlew :alertsrepository:generateMigrations # generate a Flyway script from the Exposed tables (needs Docker)
DATABASE_URL=… DATABASE_USER=… DATABASE_PASSWORD=… ./gradlew :weatheralertservice:run
```

`build` fails on any ktfmt difference, any detekt issue and any compiler warning (`allWarningsAsErrors`): fix
the cause, don't suppress it. Run `ktfmtFormat` before `build`.

## Build structure

- **Modules are discovered, not listed** (`settings.gradle.kts`): applications live in `apps/<name>`, library
  modules in `modules/<name>`. Every such directory with a `build.gradle.kts` is a module named `:<name>` (no
  `:apps:`/`:modules:` prefix). To add one, create `apps/<name>/build.gradle.kts` or
  `modules/<name>/build.gradle.kts`. An app's package is `cat.coeli.alerts.<name>`.
- **Shared build config** lives in the `build-logic` included build, in the convention plugin
  `kotlin-jvm-conventions.gradle.kts` (Kotlin JVM, JDK toolchain, warnings as errors, ktfmt with KotlinLang
  style, detekt, kotlin-test on JUnit Platform). Every module applies it with
  `alias(libs.plugins.conventions.kotlin.jvm)`; no `allprojects`/`subprojects`.
- **Everything is referenced through typed accessors**: libraries, plugins and versions in
  `gradle/libs.versions.toml` (`libs.…`), modules as `projects.…` (e.g. `projects.testUtils`). Don't write
  dependency coordinates or project paths as strings in build files.
- `test-utils` holds helpers shared by tests (e.g. `testUuid(1)`); modules use it with
  `testImplementation(projects.testUtils)`. Fakes of a module's domain interfaces (e.g.
  `InMemoryAlertRepository`) live in that module's `testFixtures`, used with
  `testImplementation(testFixtures(projects.alertsrepository))`.

## Code conventions

- **Packages**: `cat.coeli.alerts.<module>.<submodule>*.<domain|infrastructure>`, e.g.
  `cat.coeli.alerts.alertsrepository.domain`, `cat.coeli.alerts.alertsrepository.infrastructure.postgres`.
  - `domain`: domain types and repository interfaces (`XxxRepository`), with no infrastructure dependencies.
    Repository functions are `suspend`.
  - `infrastructure`: adapters, in a subpackage per technology. Implementations are named after it
    (`PostgresXxxRepository`), never `XxxRepositoryImpl`.
- **Errors are exceptions**, not `Either`/result types. Validate invariants in `init` with `require(...)`
  (see `Pagination`).
- **PostgreSQL access** uses Exposed's DSL (`exposed-jdbc`) over HikariCP. Tables are `internal object
  XxxTable` next to the repository. Blocking JDBC calls run inside `withContext(Dispatchers.IO)`.
- **External HTTP APIs** use the Ktor client with kotlinx-serialization (see `weather`): a domain interface
  (`XxxProvider`), an adapter named after the service (`OpenMeteoXxxProvider`) with `internal` `@Serializable`
  DTOs, and tests against responses recorded from the real API, served by Ktor's `MockEngine`.
- **Logging** with kotlin-logging: `private val logger = KotlinLogging.logger {}` and lambda messages
  (`logger.info { "…" }`). Only apps have a logging backend (Logback, `src/main/resources/logback.xml`);
  library modules depend only on kotlin-logging.

## Database migrations

- Flyway scripts live in `<module>/src/main/resources/db/migration` and are named
  `V<yyyyMMddHHmmss>__<DESCRIPTION>.sql`; the timestamp keeps versions unique across modules.
- Generate them from the Exposed tables with the Exposed Gradle plugin (`generateMigrations`, configured in
  the module's `exposed { migrations { … } }` block), review them, and never edit a script that may already
  have been applied.
- Each app applies the pending migrations of the modules it uses with Flyway at startup (see
  `apps/weatheralertservice/.../Main.kt`).

## Tests

- Classic example-based tests with `kotlin.test` (`@Test`, `assertEquals`, …); no property-based testing.
- Each test: set up the data, exercise one operation, assert, separated by blank lines. Test names describe
  the behaviour in camelCase (`searchReturnsOnlyAlertsAtLeastAsSevereAsTheMinimumLevel`). Small builder
  functions with default arguments (e.g. `alert(id = 1, occurredAt = "…")`) keep each test showing only
  what matters to it.
- Repository tests are contract tests: an abstract `XxxRepositoryContractTest` holds the tests, and each
  implementation (the PostgreSQL one and the in-memory fake) has a subclass that creates it.
- The PostgreSQL implementations run against a real database, never mocks: use `embeddedPostgres()` from
  `test-utils`; don't start a database of your own.
- Coroutine code is tested with `runTest`.
