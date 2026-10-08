# CLAUDE.md

Cross-framework data-access benchmark: the same scenarios, schema and dataset implemented on several
frameworks, measured with JMH. `SPEC.md` is the contract; read it before changing any implementation.

## Layout

- `SPEC.md`: schema, dataset, scenarios, metrics and fairness rules. Every implementation follows it.
- `common/`: shared Java module (schema SQL, deterministic seed data, scenario interfaces, result model,
  correctness checks, JMH base classes). Changing it affects every framework; keep it framework-neutral.
- `frameworks/<name>/`: one standalone Gradle build per framework, each including `common` as an included
  build and reading versions from `gradle/libs.versions.toml`.
- `runner/`: wrapper scripts (JMH run, startup/RSS harness, result merging).
- `results/`: runner output, git-ignored.
- `DESIGN_NOTE.md`: decisions, open questions and known non-equivalences.

## Rules

- Equivalence over speed: an implementation must produce exactly the results `SPEC.md` defines and pass
  the shared correctness check before it is benchmarked. Never special-case a framework in `common/`.
- Shared infrastructure versions (JDBC/R2DBC driver, connection pool, Postgres image) come from
  `gradle/libs.versions.toml` and are pinned in every framework build.
- Run benchmarks only on a quiet, dedicated machine for anything published. Laptop runs are smoke tests.
- No AI attribution in commits or PRs. Stage files by explicit path.
