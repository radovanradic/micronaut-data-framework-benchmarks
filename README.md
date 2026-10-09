# Micronaut Data framework benchmarks

A benchmark suite that compares Java/Kotlin data-access frameworks on the same scenarios, schema and
dataset: Micronaut Data, Spring Data and a plain-JDBC baseline today; Quarkus, Helidon and Kotlin
frameworks next. [`SPEC.md`](SPEC.md) defines what is measured and the fairness rules;
[`DESIGN_NOTE.md`](DESIGN_NOTE.md) records decisions and open questions.

> [!WARNING]
> Laptops, cloud VMs and shared-CPU machines are for **development only**: their numbers move with
> thermal throttling, turbo, noisy neighbours and background processes. Results you publish must come
> from a quiet, dedicated machine (no other workload, fixed CPU frequency where possible, database on
> the same or a dedicated host), with the full `run.json` environment record published next to them.
> Smoke runs (`--profile smoke`) are never representative.

## Implementations

| Id | Framework | Category | Build |
|---|---|---|---|
| `plain-jdbc` | hand-written JDBC + HikariCP | baseline-jdbc | `frameworks/plain-jdbc` |
| `micronaut-data-jdbc` | Micronaut Data JDBC | sql-mapping | `frameworks/micronaut-data/jdbc` |
| `micronaut-data-jpa` | Micronaut Data JPA (Hibernate) | orm-jpa | `frameworks/micronaut-data/jpa` |
| `micronaut-data-r2dbc` | Micronaut Data R2DBC | reactive | `frameworks/micronaut-data/r2dbc` |
| `spring-data-jdbc` | Spring Data JDBC | sql-mapping | `frameworks/spring-data/jdbc` |
| `spring-data-jpa` | Spring Data JPA (Hibernate) | orm-jpa | `frameworks/spring-data/jpa` |
| `spring-data-r2dbc` | Spring Data R2DBC | reactive | `frameworks/spring-data/r2dbc` |

Versions live in [`gradle/libs.versions.toml`](gradle/libs.versions.toml). Drivers, pools and Netty
are pinned there and forced onto every implementation (SPEC.md fairness rule 2). Hibernate is not
pinned: each framework uses the version it manages.

## Requirements

- JDK 25 (`sdk env` picks the version in `.sdkmanrc`); the same JDK runs every implementation.
- Docker (for the benchmark database and for the correctness checks via Testcontainers).
  With Rancher Desktop, if Testcontainers can't find Docker:
  `export DOCKER_HOST=unix://$HOME/.rd/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`.
- Python 3.11+ (standard library only) for the runner.
- On Linux, `/usr/bin/time` (package `time`) for the startup harness's peak RSS.

To set up a Linux machine for runs worth keeping, follow [`MACHINE_SETUP.md`](MACHINE_SETUP.md).

## Running

```bash
./gradlew check
```

Compiles everything and runs the shared correctness check for every implementation and configuration
against a Testcontainers Postgres (or the database in `BENCH_JDBC_URL`). This is also what CI runs. An
implementation must pass it before its numbers mean anything.

```bash
./gradlew benchDist
```

Builds a runnable distribution per implementation in `frameworks/**/build/bench/` (classpath jars plus
`versions.txt`, the resolved dependency versions).

```bash
python3 runner/bench.py db start
```

Starts the benchmark Postgres (`postgres:18.6`, port 55432, `pg_stat_statements` loaded,
`synchronous_commit=off`). To use your own server instead, export `BENCH_JDBC_URL`,
`BENCH_DB_USER` and `BENCH_DB_PASSWORD`; the server needs `pg_stat_statements` in
`shared_preload_libraries` for statement counts and `max_connections` of at least 100.

```bash
python3 runner/bench.py run --impl all --profile smoke
```

Runs JMH for the selected implementations, both configurations (`--config default,tuned`), after a
`pg_stat_statements` pass that counts statements per operation. Options:

- `--impl plain-jdbc,micronaut-data-jdbc`: implementation ids (`python3 runner/bench.py list` shows them);
- `--scenarios reads,tx` or `--scenarios findById,list100`: groups (`reads`, `writes`, `tx`, `reactive`,
  `concurrency`, `all`) or scenario ids;
- `--profile smoke | full | concurrency`: `smoke` is one short fork for development; `full` is 3 forks,
  5 × 2 s warm-up and measurement, throughput and sample (p50/p99) modes; `concurrency` runs the
  concurrency scenarios with 1, 16 and 64 platform threads and 64 virtual threads;
- `--forks`, `--warmup-iterations`, `--warmup-time`, `--iterations`, `--time`, `--modes`, `--threads`,
  `--virtual/--no-virtual`, `--no-statements`, `--no-gc`: override the profile;
- `--dry-run`: print the JMH command lines.

Results go to `results/<timestamp>-<profile>/`: `run.json` (environment, implementation versions,
records), `results.csv` (one row per implementation × configuration × scenario × mode × threads) and
`raw/` (JMH JSON and logs, statement counts with the actual SQL). `results/` is git-ignored: publishing
results is a deliberate step.

```bash
python3 runner/bench.py startup --impl all --runs 10
```

Process-level time to first query, RSS after the first query and peak RSS, median of N fresh JVMs.

```bash
python3 runner/bench.py db stop
```

### Benchmarking a Micronaut Data snapshot

The Micronaut Data build uses the platform BOM plus `micronautDataVersion` (default in the version
catalog: the 5.3.0 snapshot from Central snapshots). To benchmark a local build of micronaut-data:

```bash
python3 runner/bench.py build -P micronautDataVersion=5.3.0-SNAPSHOT -P useMavenLocal
```

after `./gradlew publishToMavenLocal` in the micronaut-data checkout. Every result records the exact
resolved version, including the snapshot timestamp, so runs against a release and against a snapshot
can be compared side by side. Gradle caches snapshots for 24 h; add `--refresh-dependencies` to the
Gradle command for the latest one.

## Adding a framework (phase 2 checklist)

1. Read `SPEC.md` completely. The implementation must be *equivalent*, not just similar: same rows,
   same results, idiomatic API of the framework.
2. Create `frameworks/<name>/` as a standalone Gradle build: copy `settings.gradle` from
   `frameworks/spring-data` (it includes `build-logic` and `common`), one subproject per variant, apply
   `bench.implementation`, import the framework BOM as a `platform(...)`, and add the framework
   version to `gradle/libs.versions.toml`. Add `includeBuild('frameworks/<name>')` to the root
   `settings.gradle`.
3. Map the five tables (SPEC.md section 2) with the framework's entity model; entities implement the
   `bench.common.model` view interfaces. Ids are database identity columns.
4. Implement `BlockingScenarios` (or `ReactiveScenarios`) with repositories and declarative
   transactions. Nested transaction scenarios must call a separate bean. Throw
   `UnsupportedScenarioException` for scenarios the framework can't express idiomatically, and list
   them in `ImplementationInfo.unsupported()`.
5. Implement `TargetFactory` (register it in `META-INF/services/bench.common.spi.TargetFactory`): start
   the framework from `DbSettings` with a fixed pool of `poolSize`, schema generation off, logging at
   WARN. Provide `default` and `tuned` configuration files; document each tuned setting.
6. Add an empty subclass of `BlockingBenchmark` / `ReactiveBenchmark` and an empty test subclass of
   `BlockingCorrectnessCheck` / `ReactiveCorrectnessCheck`.
7. `./gradlew -p frameworks/<name> check` must pass for both configurations. Then
   `./gradlew benchDist` and `python3 runner/bench.py list` should show the implementation.
8. Check the statement counts of a smoke run (`raw/*-statements.json` holds the SQL) against the
   baseline to catch accidental N+1 queries or missing joins, and record any non-equivalence in
   `DESIGN_NOTE.md`.
9. Never change `common/` for one framework. If the spec needs to change, change `SPEC.md`, `common/`
   and every implementation together, and bump `Seed.VERSION` if the dataset changes.

## Layout

```
SPEC.md                 the contract
DESIGN_NOTE.md          decisions, open questions, smoke results
gradle/libs.versions.toml  versions for every build
build-logic/            convention plugins (bench.java, bench.implementation)
common/                 schema, seed, scenario SPI, JMH base classes, tools, correctness checks
frameworks/<name>/      one Gradle build per framework, one subproject per variant
runner/bench.py         database, JMH, statement counts, startup, results
```
