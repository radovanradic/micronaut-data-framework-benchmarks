# Benchmark machine setup (Linux)

How to prepare a Linux machine for runs worth keeping, run the suite there and bring the results back.
The commands are for **Oracle Linux 9 / RHEL 9** (`dnf`) and **Ubuntu 22.04/24.04** (`apt`); where they
differ, both are given.

## 0. The machine

- **Dedicated CPUs.** A VM is acceptable only with dedicated vCPUs. Avoid burstable, shared-core and
  "baseline < 100%" shapes, because their CPU time depends on other tenants.
- **Size:** at least 16 vCPUs and 32 GB RAM, and 40 GB of free disk for Gradle caches, Docker images and
  results. Each JMH fork uses a 2 GB heap; the concurrency profile runs 64 threads.
- **Nothing else running** during a run: no CI agents, builds or other users' work.
- **Outbound HTTPS** to GitHub, Maven Central, `central.sonatype.com` (Micronaut Data snapshots),
  Docker Hub and `get.sdkman.io`.

Write down the VM shape, the region and whether the vCPUs are dedicated. `run.json` records the CPU
model, core count and memory, but not those.

## 1. Packages

Oracle Linux 9 / RHEL 9:

```bash
sudo dnf install -y git curl zip unzip tar tmux time python3.12 util-linux
```

Ubuntu:

```bash
sudo apt update
sudo apt install -y git curl zip unzip tmux time python3 util-linux
```

The runner needs Python 3.11 or later. Oracle Linux 9's default `python3` is 3.9, so use `python3.12`
there wherever this guide says `python3`. Ubuntu 24.04 ships 3.12. `time` provides `/usr/bin/time`,
which the startup harness uses to read peak RSS. `util-linux` provides `taskset` and `lscpu`.

## 2. Docker

The runner starts Postgres in Docker, and the correctness checks use Testcontainers.

Oracle Linux 9 / RHEL 9:

```bash
sudo dnf config-manager --add-repo https://download.docker.com/linux/rhel/docker-ce.repo
sudo dnf install -y docker-ce docker-ce-cli containerd.io
```

If `dnf` reports conflicts with `podman`, `buildah` or `runc`, remove those packages first, but only if
nothing else on the machine uses them.

Ubuntu:

```bash
sudo apt install -y docker.io
```

Then, on both:

```bash
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"
```

Log out and back in so the group change applies, then check that `docker run --rm hello-world` works
without `sudo`.

## 3. JDK (SDKMAN)

```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
```

Optional: run `sdk config` and set `sdkman_auto_env=true`, so entering the repository selects the JDK
automatically.

## 4. The repository

```bash
git clone -b phase1 https://github.com/radovanradic/micronaut-data-framework-benchmarks.git
cd micronaut-data-framework-benchmarks
sdk env install
java -version
```

`sdk env install` installs the JDK named in `.sdkmanrc`. Run `sdk env` in every new shell, including
each new tmux window, unless you enabled auto env. If the repository is private, authenticate first,
with `gh auth login` or an SSH key added to GitHub, and clone the `git@github.com:` URL.

## 5. Build and verify

```bash
./gradlew check
./gradlew benchDist
python3 runner/bench.py list
```

`check` runs every correctness check against a Testcontainers Postgres and takes a few minutes the first
time. `list` should show all seven implementations with their resolved versions.

## 6. Quiet the machine

Look at the CPU layout first:

```bash
lscpu -e
```

The `CORE` column shows which vCPUs are hyperthread siblings of the same physical core. Keep siblings
together when you split CPUs below.

Stop background jobs that can wake up during a run, where present:

```bash
sudo systemctl stop dnf-makecache.timer packagekit
sudo systemctl stop unattended-upgrades apt-daily.timer apt-daily-upgrade.timer snapd
```

The first line is for Oracle Linux, the second for Ubuntu. They come back on reboot.

Optional:

- `sudo swapoff -a` for the duration of the run.
- On Oracle Linux, `sudo dnf install -y tuned`, then `sudo tuned-adm profile throughput-performance`.
- On bare metal only, pin the CPU frequency, for example with `sudo cpupower frequency-set -g performance`.
  VMs usually don't expose frequency control, so skip it there.

Record whatever you changed.

## 7. Database and CPU pinning

One machine runs both Postgres and the benchmarks, so give each its own CPUs. The examples below assume
16 vCPUs: Postgres gets 0–5, the benchmarks get 6–15. Adjust the ranges to your `lscpu -e` output.

```bash
python3 runner/bench.py db start
docker update --cpuset-cpus=0-5 bench-postgres
docker inspect -f '{{.HostConfig.CpusetCpus}}' bench-postgres
```

Prefix every benchmark command with `taskset -c 6-15`. The JVMs the runner starts inherit the CPU
affinity, and the JVM sizes its GC and compiler threads from it.

With a second machine for Postgres, skip `db start`. Point the runner at that server with
`BENCH_JDBC_URL`, `BENCH_DB_USER` and `BENCH_DB_PASSWORD`; the README lists the server settings it needs.

## 8. Check that the machine is stable enough

Do this before committing to a long run:

```bash
taskset -c 6-15 python3 runner/bench.py run --impl plain-jdbc,micronaut-data-jdbc --scenarios findById,list100,list10000 --config default --profile full
```

Run the same command twice and compare the two `results.csv` files. As a rule of thumb, the same
scenario should agree within about 3%, and the error column should stay below about 5% of the score.
Much larger differences mean the machine is too noisy for published numbers.

## 9. Full runs

Use tmux, so the run survives a dropped SSH connection:

```bash
tmux new -s bench
```

Inside tmux:

```bash
sdk env
taskset -c 6-15 python3 runner/bench.py run --impl all --scenarios all --profile full
```

Detach with `Ctrl-b d`; reattach with `tmux attach -t bench`. Add `--dry-run` to see the JMH command
lines without running them.

Rough durations, estimated from the profile settings rather than measured:

| Command | Duration |
|---|---|
| `--profile full`, all implementations, both configurations | about 10 hours |
| `--profile concurrency` | about 6 hours |
| `startup --impl all --runs 10` | a few minutes |

Run them one at a time:

```bash
taskset -c 6-15 python3 runner/bench.py run --impl all --profile concurrency
taskset -c 6-15 python3 runner/bench.py startup --impl all --runs 10
```

Afterwards:

```bash
python3 runner/bench.py db stop
```

## 10. Bring the results back

Each run writes `results/<timestamp>-<profile>/` (`run.json`, `results.csv`, `raw/`). Next to it, add a
`notes.txt` with the VM shape, the CPU split, the `lscpu -e` output and any OS changes from step 6.
Then copy the directory to your own machine:

```bash
scp -r user@vm:micronaut-data-framework-benchmarks/results/20261010-120000-full results/
```

`results/` is git-ignored. Nothing is published unless you do it deliberately.

## Benchmarking a local Micronaut Data build

To measure a micronaut-data change before it is merged: on the VM, clone micronaut-data, check out the
branch and run `./gradlew publishToMavenLocal`. Then rebuild the benchmarks against it:

```bash
python3 runner/bench.py build -P micronautDataVersion=5.3.0-SNAPSHOT -P useMavenLocal
```

Rerun only the affected scenarios with `--impl micronaut-data-jdbc --scenarios list10000`. `run.json`
records the exact resolved versions, so before/after runs can be told apart.
