# Design note: phase 1

For review before phase 2. Phase 1 built the shared foundation (`SPEC.md`, `common/`, `build-logic/`,
`runner/`) and seven implementations: plain JDBC, Micronaut Data JDBC/JPA/R2DBC and Spring Data
JDBC/JPA/R2DBC. All seven pass the shared correctness check in both configurations
(`./gradlew check`: 26 checks per blocking implementation, 10 per reactive one).

## Decisions

1. **Versions.** Micronaut platform 5.2.2 with micronaut-data overridden to **5.3.0-SNAPSHOT** (from
   Central snapshots). The brief asked for the latest Micronaut 4.x, but Micronaut Data 5.3 is on the
   Micronaut 5 line, so we chose 5.x so that 5.3 can be measured (and so that local builds can be
   swapped in with `-PmicronautDataVersion` / `-PuseMavenLocal`). Spring Boot 4.1.1 (Spring Data
   2026.0.1, Spring Framework 7.0.9). JDK 25 (Oracle GraalVM 25.0.3 locally, GraalVM 25 in CI),
   PostgreSQL 18.6.
2. **One Gradle build per framework** under `frameworks/<name>/`, one subproject per variant, sharing
   `common/` and `build-logic/` as included builds; the root build only aggregates. Neither the
   Micronaut nor the Spring Boot Gradle plugin is used: dependencies come from the platform BOMs, and
   the benchmark runs from a plain classpath (`build/bench/lib`), not a fat jar. That avoids shading
   problems (Spring Boot 4's many `AutoConfiguration.imports` files, Micronaut service files) and keeps
   the classpath identical for JMH, the statement counter and the startup harness.
3. **Pinned shared infrastructure** (fairness rule 2): pgjdbc 42.7.13, HikariCP 7.1.0,
   r2dbc-postgresql 1.1.3, r2dbc-pool 1.0.2, Netty 4.2.19 and Hibernate 7.4.10 are forced onto every
   implementation, overriding the BOMs (Boot alone would use HikariCP 7.0.2, r2dbc-postgresql 1.1.2,
   Netty 4.2.17 and Hibernate 7.4.5). Every result records the resolved versions, including the
   snapshot timestamp (`5.3.0-20261008.031039-21` for the smoke run below).
4. **No copying of results.** Entities implement small view interfaces from `common`, so scenario
   methods return the framework's own objects and allocation per op reflects the framework, not the
   harness. Correctness checks compare those views with values computed from the seed formulas.
5. **One source of truth for the work.** `BlockingOps`/`ReactiveOps` define each operation's inputs; the
   JMH benchmarks, the statement counter and the correctness check all call them. JMH benchmark
   classes live in `common` and are inherited by empty per-implementation subclasses.
6. **Equal starting state.** Every JMH fork resets the database (delete rows created by writes, restore
   mutable rows, `VACUUM ANALYZE`). Write scenarios touch only author 100's books, a dedicated customer's
   orders and per-thread slices of mutable rows, so reads and concurrent writers don't interfere.
7. **`synchronous_commit=off`** so write scenarios measure the framework rather than WAL flushes on the
   test machine's disk.
8. **Database identity ids everywhere.** This is the natural mapping in every framework, but it
   disables insert batching in Hibernate; see open question 3.
9. **Statement counts from `pg_stat_statements`** in a separate pass (not during timing). Data
   statements are reliable. Transaction control is reported separately and is only a lower bound:
   the server records `BEGIN READ ONLY` and most `BEGIN`s, but not the `COMMIT` pgjdbc sends through
   the extended protocol. Driver connection set-up statements are excluded.
10. **Transactions.** The spec requires write scenarios to be atomic; each framework keeps its own
    default transactional behaviour for repository calls (that is part of what is measured). Nested
    transaction scenarios go through a separate bean, so declarative proxies apply in both frameworks.
11. **Runner** in Python (standard library only); virtual threads via JMH's `-Djmh.executor=VIRTUAL`.

## Known non-equivalences

These follow from what each framework offers idiomatically. They are visible in the results (as
`n/a` or in the statement counts) rather than worked around.

| Area | What differs |
|---|---|
| `keysetPage` | Unsupported in **Micronaut Data JPA** (cursored pagination is documented for JDBC and R2DBC only) and **Spring Data JDBC** (no `Window`/`ScrollPosition` support in 4.1; open PR spring-data-relational#2149). Supported with each framework's own API in Micronaut Data JDBC (`CursoredPage`), Spring Data JPA (`Window`) and plain JDBC. |
| `joinFetch` | Spring Data JDBC loads the aggregate with 1 + N queries (11 statements/op). Its single-query loading applies only to `CrudRepository` find methods, not derived queries. The others use one `LEFT JOIN`: Micronaut `@Join(LEFT_FETCH)` (plain `FETCH` would be an inner join), JPA entity graph / join fetch, and plain SQL in the baseline. |
| `criteria` | Spring Data JDBC has no Specification API; it uses `JdbcAggregateTemplate` with `Criteria`, its documented dynamic query API. The others use JPA-style criteria. |
| `saveAll` | Micronaut Data JDBC, Spring Data JDBC and plain JDBC use JDBC batches. Both JPA variants insert one row at a time (IDENTITY ids). Both R2DBC variants issue one INSERT per row. |
| Reads outside a transaction | Spring Data repositories wrap `CrudRepository` methods (`findById`, `save`, `deleteById`) in a transaction (`@Transactional(readOnly = true)` on the repository class), so they send `BEGIN`/`COMMIT` that Micronaut Data JDBC and plain JDBC don't. Derived queries run without one. Micronaut Data JPA runs every repository call in a transaction. |
| `delete` | Spring Data JPA's `deleteById` loads the entity first (2 statements); Micronaut Data JPA issues one `DELETE`. |
| `inList1000` | Plain JDBC binds one array (`id = ANY(?)`); the frameworks expand `IN (?, …)` with 1,000 parameters. |
| Tuned configs | Plain JDBC, Micronaut Data JDBC/R2DBC and Spring Data JDBC/R2DBC have nothing meaningful to tune, so `tuned` = `default` (the reason is in each file). Both JPA variants get the same Hibernate settings (batch size, ordered inserts/updates, IN padding). Spring Data JPA also gets `hikari.auto-commit=false` (Boot then sets `provider_disables_autocommit`). I did not verify a Micronaut equivalent, so it isn't applied there. |
| R2DBC pool config | Same r2dbc-pool and size (initial = max = 16), configured differently: a `r2dbc:pool:` URL for Micronaut, `spring.r2dbc.pool.*` for Spring. |

## Open questions

1. **Snapshot vs release.** Measuring 5.3.0-SNAPSHOT means the default build changes daily. For any
   published run, pin a release or record and reuse one timestamped snapshot.
2. **Plain R2DBC baseline.** The `baseline-r2dbc` category is empty; the reactive numbers have no
   lower bound yet. Suggested for phase 2.
3. **ID generation for JPA.** Should the tuned JPA configuration be allowed to switch to pooled
   sequences (which enables insert batching)? That needs a schema change (sequences) and per-config
   mappings; the spec currently forbids it.
4. **R2DBC connection churn.** During steady state, connection set-up statements keep appearing every
   10–25 operations with Micronaut Data R2DBC, so r2dbc-pool seems to open new connections. Check the
   pool settings (max idle/life time, validation) and whether Spring shows the same.
5. **Round trips.** `pg_stat_statements` counts executions, not round trips, and under-counts
   transaction control. A counting proxy (datasource-proxy / r2dbc-proxy) would give exact client-side
   numbers.
6. **Hibernate version.** Hibernate is pinned to the same version for both JPA variants, so the
   comparison isolates the Data layer. Should a second configuration use each framework's own
   managed Hibernate version?
7. **Native image** startup and RSS aren't implemented. The startup harness accepts any command, so
   adding native executables is mostly build work.
8. **Database placement.** The smoke run used Postgres in Docker (Rancher Desktop VM) on the same
   laptop. Published runs need a decision: same host with CPU pinning, or a dedicated database host.
9. **Embedded Postgres (zonky)** as a fallback isn't implemented; Testcontainers and the runner's
   container cover development and CI, and zonky binaries lag behind Postgres releases.
10. **More scenarios to consider.** Count/exists queries, bulk update/delete by criteria, `@Query`
    native SQL, and second-level/query caches (off by default everywhere).

## Smoke run (NOT representative)

> [!CAUTION]
> These numbers are **not representative** and must not be quoted or compared. Apple M2 Max laptop
> (12 cores, 32 GB), macOS 26.7, Oracle GraalVM 25.0.3, Postgres 18.6 in Docker under Rancher Desktop
> on the same machine, other applications running, **1 fork, 1 × 1 s warm-up, 1 × 1 s measurement**.
> The run only shows that every implementation runs end to end and what the statement counts look like.

Command: `python3 runner/bench.py run --impl all --scenarios all --config default --profile smoke --iterations 1`.
Configuration `default`, 1 thread, throughput (ops/s, higher is better), with data statements per
operation from `pg_stat_statements` in parentheses.

Blocking:

| Scenario | plain-jdbc | micronaut-data-jdbc | spring-data-jdbc | micronaut-data-jpa | spring-data-jpa |
|---|---:|---:|---:|---:|---:|
| `findById` | 1,862 (1) | 1,761 (1) | 717 (1) | 837 (1) | 868 (1) |
| `findByIsbn` | 1,849 (1) | 1,338 (1) | 985 (1) | 829 (1) | 1,610 (1) |
| `list10` | 1,656 (1) | 1,596 (1) | 1,015 (1) | 740 (1) | 1,565 (1) |
| `list100` | 1,563 (1) | 1,415 (1) | 715 (1) | 573 (1) | 1,255 (1) |
| `list10000` | 122 (1) | 82 (1) | 27 (1) | 53 (1) | 55 (1) |
| `projection` | 1,562 (1) | 1,444 (1) | 1,013 (1) | 729 (1) | 1,505 (1) |
| `pageWithCount` | 548 (2) | 540 (2) | 415 (2) | 249 (2) | 383 (2) |
| `sort` | 1,261 (1) | 1,352 (1) | 740 (1) | 470 (1) | 1,100 (1) |
| `keysetPage` | 1,597 (1) | 1,479 (1) | n/a | n/a | 1,163 (1) |
| `inList1000` | 559 (1) | 298 (1) | 82 (1) | 191 (1) | 218 (1) |
| `criteria` | 478 (1) | 429 (1) | 380 (1) | 301 (1) | 309 (1) |
| `joinFetch` | 1,060 (1) | 901 (1) | 117 (11) | 541 (1) | 640 (1) |
| `save` | 1,649 (1) | 1,613 (1) | 715 (1) | 622 (1) | 738 (1) |
| `saveAll100` | 238 (100) | 206 (100) | 172 (100) | 13 (100) | 17 (100) |
| `saveAll1000` | 28 (1000) | 24 (1000) | 20 (1000) | 1 (1000) | 2 (1000) |
| `update` | 577 (2) | 546 (2) | 472 (2) | 340 (2) | 466 (2) |
| `delete` | 1,747 (1) | 1,711 (1) | 611 (1) | 781 (1) | 516 (2) |
| `cascadeInsert` | 539 (6) | 499 (6) | 316 (6) | 206 (6) | 226 (6) |
| `optimisticUpdate` | 622 (2) | 544 (2) | 470 (2) | 423 (2) | 551 (2) |
| `txReadOnly` | 497 (2) | 596 (2) | 583 (2) | 506 (2) | 549 (2) |
| `txReadWrite` | 472 (2) | 530 (2) | 417 (2) | 381 (2) | 485 (2) |
| `txNestedRequired` | 537 (2) | 550 (2) | 476 (2) | 427 (2) | 504 (2) |
| `txRequiresNew` | 372 (2) | 407 (2) | 293 (2) | 201 (2) | 353 (2) |
| `txRollback` | 733 (1) | 792 (1) | 630 (1) | 381 (1) | 684 (1) |

Reactive:

| Scenario | micronaut-data-r2dbc | spring-data-r2dbc |
|---|---:|---:|
| `reactiveFindById` | 1,001 (1) | 1,168 (1) |
| `reactiveStream10` | 663 (1) | 867 (1) |
| `reactiveStream100` | 591 (1) | 614 (1) |
| `reactiveStream10000` | 42 (1) | 28 (1) |
| `reactiveSaveAll100` | 12 (100) | 14 (100) |
| `reactiveSaveAll1000` | 2 (1000) | 1 (1000) |
| `reactiveTxUpdate` | 343 (2) | 312 (2) |
| `reactiveTxRollback` | 400 (1) | 395 (1) |

Startup (3 runs, median, `-Xmx512m`; time to first query is wall time from process launch):

| Implementation | Time to first query (ms) | RSS after first query (MB) | Peak RSS (MB) |
|---|---:|---:|---:|
| plain-jdbc | 325 | 144 | 145 |
| micronaut-data-jdbc | 679 | 199 | 203 |
| micronaut-data-r2dbc | 975 | 216 | 218 |
| micronaut-data-jpa | 1,500 | 271 | 271 |
| spring-data-jdbc | 1,016 | 232 | 232 |
| spring-data-r2dbc | 1,202 | 241 | 241 |
| spring-data-jpa | 1,719 | 275 | 275 |

The concurrency profile (16 platform and 16 virtual threads, sample mode with p50/p99) was checked
on two scenarios only; 64 threads haven't been run yet.
