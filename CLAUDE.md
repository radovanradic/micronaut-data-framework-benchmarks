# CLAUDE.md

Cross-framework data-access benchmark: the same scenarios, schema and dataset implemented on several
frameworks, measured with JMH. `SPEC.md` is the contract; read it before changing any implementation.

## Layout

- `SPEC.md`: schema, dataset, scenarios, metrics and fairness rules. Every implementation follows it.
- `common/`: shared Java module (schema SQL, deterministic seed data, scenario SPI, JMH base classes,
  tools for the runner; the correctness checks are in its `testFixtures`). Changing it affects every
  framework; keep it framework-neutral and free of heavy dependencies (it is on every classpath).
- `build-logic/`: convention plugins. `bench.implementation` pins the shared drivers/pools/Netty
  versions and builds `build/bench` (classpath + resolved `versions.txt`) for the runner.
- `frameworks/<name>/`: one standalone Gradle build per framework, one subproject per variant, each
  including `common` and `build-logic` and reading versions from `gradle/libs.versions.toml`.
- `runner/bench.py`: database container, JMH runs, statement counts, startup/RSS, result merging.
- `results/`: runner output, git-ignored.
- `DESIGN_NOTE.md`: decisions, open questions and known non-equivalences.

The root build only aggregates: `./gradlew check` runs every correctness check, `./gradlew benchDist`
builds every distribution. `./gradlew -p frameworks/<name> check` works on one framework.

## Rules

- Equivalence over speed: an implementation must produce exactly the results `SPEC.md` defines and pass
  the shared correctness check before it is benchmarked. Never special-case a framework in `common/`.
- Shared infrastructure versions (JDBC/R2DBC driver, connection pool, Netty, Postgres image) come from
  `gradle/libs.versions.toml` and are pinned in every framework build. Hibernate is not pinned.
- Micronaut Data runs the 5.3.0 snapshot by design (`micronautDataVersion` in the catalog).
- Run benchmarks only on a quiet, dedicated machine for anything published. Laptop runs are smoke tests.
- No AI attribution in commits or PRs. Stage files by explicit path.
