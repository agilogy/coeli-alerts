# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Kotlin backend, Gradle multi-module. A developer coming from TypeScript and learning Kotlin works on this
repo: keep the code plain and idiomatic, and don't bring in advanced techniques or libraries (Arrow, `Either`,
property-based testing, …) without asking.

## Commands

```bash
./gradlew build                      # compile, tests, ktfmt, detekt and coverage: must pass before considering work done
./gradlew ktfmtFormat                # format all Kotlin sources and .kts scripts
./gradlew detektMain detektTest      # lint only, with type resolution
./gradlew koverHtmlReport             # coverage report of all modules (build/reports/kover)
./gradlew :alertsrepository:test --tests 'cat.coeli.alerts.alertsrepository.infrastructure.postgres.PostgresAlertRepositoryTest.searchBreaksTiesInOccurredAtById'
./gradlew :alertsrepository:generateMigrations # generate a Flyway script from the Exposed tables (needs Docker)
DATABASE_URL=… DATABASE_USER=… DATABASE_PASSWORD=… ./gradlew :weatheralertservice:run
```

`main` is protected: changes go through a pull request, and the CI build (`.github/workflows/build.yml`,
`./gradlew build`) must pass before merging.

`build` fails on any ktfmt difference, any detekt issue (all rules, see `config/detekt/detekt.yml`), any compiler
warning (`allWarningsAsErrors`) and a total line coverage below 80% (Kover, `coverage-conventions.gradle.kts`): fix
the cause, don't suppress it. Run `ktfmtFormat` before `build`.

## Build structure

- **Modules are discovered, not listed** (`settings.gradle.kts`): applications live in `apps/<name>`, library
  modules in `modules/<name>`. Every such directory with a `build.gradle.kts` is a module named `:<name>` (no
  `:apps:`/`:modules:` prefix). To add one, create `apps/<name>/build.gradle.kts` or
  `modules/<name>/build.gradle.kts`. An app's package is `cat.coeli.alerts.<name>`.
- **Shared build config** lives in the `build-logic` included build, in the convention plugin
  `kotlin-jvm-conventions.gradle.kts` (Kotlin JVM, JDK toolchain, warnings as errors, ktfmt with KotlinLang
  style, detekt, kotlin-test on JUnit Platform). Every module applies it with
  `alias(libs.plugins.conventions.kotlin.jvm)`; no `allprojects`/`subprojects`, except in the root
  `build.gradle.kts`, which adds `test-utils` to the tests of every other module.
- **Everything is referenced through typed accessors**: libraries, plugins and versions in
  `gradle/libs.versions.toml` (`libs.…`), modules as `projects.…` (e.g. `projects.testUtils`). Don't write
  dependency coordinates or project paths as strings in build files.
- `test-utils` holds helpers shared by tests (e.g. `testUuid(1)`) and their Logback configuration; every
  other module gets it as a `testImplementation` dependency from the root `build.gradle.kts`. Fakes of a module's domain interfaces (e.g.
  `InMemoryAlertRepository`) live in that module's `testFixtures`, used with
  `testImplementation(testFixtures(projects.alertsrepository))`.

## Code conventions

- **Packages**: `cat.coeli.alerts.<module>.<submodule>*.<domain|infrastructure>`, e.g.
  `cat.coeli.alerts.alertsrepository.domain`, `cat.coeli.alerts.alertsrepository.infrastructure.postgres`.
  - `domain`: domain types and repository interfaces (`XxxRepository`), with no infrastructure dependencies.
    Repository functions are `suspend`.
  - `infrastructure`: adapters, in a subpackage per technology. Implementations are named after it
    (`PostgresXxxRepository`), never `XxxRepositoryImpl`.
- **Comments**: few. Nothing the code already shows (names, types, signatures); only non-obvious constraints
  or reasons. Comments in domain code don't mention infrastructure (databases, specific APIs).
- **Errors are exceptions**, not `Either`/result types. Validate invariants in `init` with `require(...)`
  (see `Pagination`).
- **PostgreSQL access** uses Exposed's DSL over R2DBC (`exposed-r2dbc`, non-blocking: no dispatcher needed).
  Tables are `internal object XxxTable` next to the repository. Only Flyway migrations use JDBC.
- **External HTTP APIs** use the Ktor client with kotlinx-serialization (see `weather`): a domain interface
  (`XxxProvider`), an adapter named after the service (`OpenMeteoXxxProvider`) with `internal` `@Serializable`
  DTOs, and tests against responses recorded from the real API, served by Ktor's `MockEngine`.
- **Logging** with kotlin-logging: `private val logger = KotlinLogging.logger {}` and lambda messages
  (`logger.info { "…" }`). Only apps have a logging backend (Logback, `src/main/resources/logback.xml`);
  library modules depend only on kotlin-logging. Tests of every module use Logback through `test-utils`, with
  its `logback-test.xml`, which enables `cat.coeli.alerts` down to TRACE (so the tests cover the
  code that builds those messages) but prints only INFO and above.

## Database migrations

- Flyway scripts live in `<module>/src/main/resources/db/migration` and are named
  `V<yyyyMMddHHmmss>__<DESCRIPTION>.sql`; the timestamp keeps versions unique across modules.
- Generate them from the Exposed tables with the Exposed Gradle plugin (`generateMigrations`, configured in
  the module's `exposed { migrations { … } }` block), review them, and never edit a script that may already
  have been applied.
- Each app applies the pending migrations of the modules it uses with Flyway at startup (see
  `apps/weatheralertservice/.../WeatherAlertServiceApp.kt`).

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
