# coeli-alerts

A small Kotlin backend that shows how to work with external HTTP APIs and PostgreSQL: it reads the
weather forecast from [Open-Meteo](https://open-meteo.com) and records an alert in PostgreSQL for each
rainy or cold day.

## Structure

- `apps/weatheralertservice`: the application. It wires the modules together and runs the use case.
- `modules/alertsrepository`: alerts and their repository, implemented with
  [Exposed](https://www.jetbrains.com/exposed/) on PostgreSQL, with Flyway migrations.
- `modules/weather`: weather forecasts, read from Open-Meteo with the [Ktor](https://ktor.io) client.
- `modules/test-utils`: helpers shared by tests, such as an embedded PostgreSQL.

Each module separates `domain` from `infrastructure`.

## Build

```bash
./gradlew build
```

Compiles, runs the tests (with an embedded PostgreSQL, no Docker needed), and checks formatting (ktfmt)
and lint (detekt). `./gradlew ktfmtFormat` fixes the formatting.

## Run

```bash
DATABASE_URL=jdbc:postgresql://localhost:5432/postgres DATABASE_USER=… DATABASE_PASSWORD=… \
  ./gradlew :weatheralertservice:run
```

## Contributing

`main` only accepts pull requests whose build passes on GitHub Actions.
