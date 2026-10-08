#!/usr/bin/env python3
"""Benchmark runner: database lifecycle, JMH runs, statement counts, startup/RSS, result merging.

Standard library only (Python 3.11+). See README.md for usage; SPEC.md for what is measured.

    python3 runner/bench.py db start
    python3 runner/bench.py run --impl plain-jdbc,micronaut-data-jdbc --profile smoke
    python3 runner/bench.py startup --impl all --runs 10
    python3 runner/bench.py db stop
"""

import argparse
import csv
import datetime
import glob
import json
import os
import platform
import re
import shutil
import subprocess
import sys
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONTAINER = "bench-postgres"
DB_PORT = 55432
DB_NAME = DB_USER = DB_PASSWORD = "bench"
JMH_JVM_ARGS = ["-Xms2g", "-Xmx2g", "-XX:+UseG1GC"]
STARTUP_JVM_ARGS = ["-Xmx512m", "-XX:+UseG1GC"]

GROUPS = {
    "reads": ["findById", "findByIsbn", "list10", "list100", "list10000", "projection", "pageWithCount",
              "sort", "keysetPage", "inList1000", "criteria", "joinFetch"],
    "writes": ["save", "saveAll100", "saveAll1000", "update", "delete", "cascadeInsert", "optimisticUpdate"],
    "tx": ["txReadOnly", "txReadWrite", "txNestedRequired", "txRequiresNew", "txRollback"],
    "reactive": ["reactiveFindById", "reactiveStream10", "reactiveStream100", "reactiveStream10000",
                 "reactiveSaveAll100", "reactiveSaveAll1000", "reactiveTxUpdate", "reactiveTxRollback"],
}
GROUPS["all"] = [s for g in ("reads", "writes", "tx", "reactive") for s in GROUPS[g]]
# SPEC.md section 4.5
GROUPS["concurrency"] = ["findById", "list100", "update", "txReadWrite", "reactiveFindById", "reactiveTxUpdate"]

PROFILES = {
    # development only: one short fork, numbers are not meaningful
    "smoke": dict(forks=1, warmup_iterations=1, warmup_time="1s", iterations=2, time="1s",
                  modes=["thrpt"], threads=[1], virtual=False, statements=True, scenarios="all"),
    # the minimum for published results (SPEC.md fairness rule 5)
    "full": dict(forks=3, warmup_iterations=5, warmup_time="2s", iterations=5, time="2s",
                 modes=["thrpt", "sample"], threads=[1], virtual=False, statements=True, scenarios="all"),
    # SPEC.md section 4.5
    "concurrency": dict(forks=3, warmup_iterations=3, warmup_time="2s", iterations=5, time="2s",
                        modes=["thrpt", "sample"], threads=[1, 16, 64], virtual=True, statements=False,
                        scenarios="concurrency"),
}


# --- helpers ------------------------------------------------------------------------------------

def log(message):
    print(f"[bench] {message}", flush=True)


def capture(cmd, env=None, check=True):
    result = subprocess.run(cmd, capture_output=True, text=True, env=env)
    if check and result.returncode != 0:
        raise SystemExit(f"Command failed ({result.returncode}): {' '.join(cmd)}\n{result.stdout}\n{result.stderr}")
    return result


def java():
    home = os.environ.get("JAVA_HOME")
    return os.path.join(home, "bin", "java") if home else shutil.which("java") or "java"


def postgres_image():
    with open(os.path.join(ROOT, "gradle", "libs.versions.toml"), encoding="utf-8") as f:
        match = re.search(r'^postgres-image\s*=\s*"([^"]+)"', f.read(), re.MULTILINE)
    if not match:
        raise SystemExit("postgres-image not found in gradle/libs.versions.toml")
    return match.group(1)


def db_env():
    """BENCH_* settings: from the environment if BENCH_JDBC_URL is set, else the runner's container."""
    env = dict(os.environ)
    if not env.get("BENCH_JDBC_URL"):
        env["BENCH_JDBC_URL"] = f"jdbc:postgresql://127.0.0.1:{DB_PORT}/{DB_NAME}"
        env["BENCH_DB_USER"] = DB_USER
        env["BENCH_DB_PASSWORD"] = DB_PASSWORD
    return env


# --- implementations ----------------------------------------------------------------------------

class Dist:
    """A benchmark distribution (build/bench of an implementation)."""

    def __init__(self, path):
        self.path = path
        self.classpath = os.path.join(path, "lib", "*")
        info = json.loads(capture([java(), "-cp", self.classpath, "bench.common.tools.Describe"]).stdout)
        self.info = info
        self.id = info["id"]
        versions_file = os.path.join(path, "versions.txt")
        with open(versions_file, encoding="utf-8") as f:
            self.versions = [line.strip() for line in f if line.strip()]

    def key_versions(self):
        result = {}
        for module in self.info["keyModules"]:
            for line in self.versions:
                if line.startswith(module + ":"):
                    result[module] = line[len(module) + 1:]
        return result


def discover():
    paths = sorted(set(os.path.dirname(p) for p in glob.glob(os.path.join(ROOT, "frameworks", "**", "build", "bench", "versions.txt"), recursive=True)))
    return [Dist(p) for p in paths]


def select_dists(spec):
    dists = discover()
    if not dists:
        raise SystemExit("No benchmark distributions found; run `./gradlew benchDist` first (or `bench.py build`)")
    if spec == "all":
        return dists
    wanted = spec.split(",")
    by_id = {d.id: d for d in dists}
    missing = [w for w in wanted if w not in by_id]
    if missing:
        raise SystemExit(f"Unknown implementation(s) {missing}; available: {sorted(by_id)}")
    return [by_id[w] for w in wanted]


def select_scenarios(spec, dist):
    names = []
    for part in spec.split(","):
        names.extend(GROUPS.get(part, [part]))
    supported = set(dist.info["supported"])
    return [n for n in dict.fromkeys(names) if n in supported]


# --- environment --------------------------------------------------------------------------------

def environment(env, dist, jvm_args):
    info = {
        "timestamp": datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds"),
        "os": platform.platform(),
        "machine": platform.machine(),
        "cpuModel": cpu_model(),
        "logicalCores": os.cpu_count(),
        "memoryBytes": memory_bytes(),
        "loadAverageAtStart": list(os.getloadavg()) if hasattr(os, "getloadavg") else None,
        "jdk": jdk_properties(),
        "jvmArgs": jvm_args,
        "git": git_info(),
        "dbUrl": env["BENCH_JDBC_URL"],
        "dbImage": postgres_image() if env.get("BENCH_JDBC_URL", "").endswith(f":{DB_PORT}/{DB_NAME}") else "external",
        "poolSize": int(env.get("BENCH_POOL_SIZE", "16")),
    }
    if dist is not None:
        info["database"] = json.loads(capture([java(), "-cp", dist.classpath, "bench.common.tools.DbAdmin", "info"], env=env).stdout)
    return info


def cpu_model():
    if sys.platform == "darwin":
        return capture(["sysctl", "-n", "machdep.cpu.brand_string"], check=False).stdout.strip()
    try:
        with open("/proc/cpuinfo", encoding="utf-8") as f:
            for line in f:
                if line.startswith("model name"):
                    return line.split(":", 1)[1].strip()
    except OSError:
        pass
    return platform.processor()


def memory_bytes():
    if sys.platform == "darwin":
        out = capture(["sysctl", "-n", "hw.memsize"], check=False).stdout.strip()
        return int(out) if out.isdigit() else None
    try:
        with open("/proc/meminfo", encoding="utf-8") as f:
            for line in f:
                if line.startswith("MemTotal:"):
                    return int(line.split()[1]) * 1024
    except OSError:
        pass
    return None


def jdk_properties():
    out = capture([java(), "-XshowSettings:properties", "-version"], check=False).stderr
    props = {}
    for key in ("java.version", "java.vendor", "java.vm.name", "java.vm.version", "java.runtime.version"):
        match = re.search(rf"^\s*{re.escape(key)} = (.*)$", out, re.MULTILINE)
        if match:
            props[key] = match.group(1).strip()
    return props


def git_info():
    sha = capture(["git", "-C", ROOT, "rev-parse", "HEAD"], check=False).stdout.strip()
    dirty = bool(capture(["git", "-C", ROOT, "status", "--porcelain"], check=False).stdout.strip())
    return {"sha": sha, "dirty": dirty}


# --- commands: db -------------------------------------------------------------------------------

def cmd_db(args):
    if args.action == "start":
        existing = capture(["docker", "ps", "-a", "--filter", f"name=^{CONTAINER}$", "--format", "{{.Status}}"]).stdout.strip()
        if existing.startswith("Up"):
            log(f"{CONTAINER} is already running")
        else:
            if existing:
                capture(["docker", "rm", "-f", CONTAINER])
            image = postgres_image()
            log(f"starting {image} as {CONTAINER} on port {DB_PORT}")
            capture(["docker", "run", "-d", "--name", CONTAINER,
                     "-e", f"POSTGRES_USER={DB_USER}", "-e", f"POSTGRES_PASSWORD={DB_PASSWORD}", "-e", f"POSTGRES_DB={DB_NAME}",
                     "-p", f"127.0.0.1:{DB_PORT}:5432", "--shm-size=1g", image,
                     "-c", "shared_preload_libraries=pg_stat_statements",
                     "-c", "max_connections=200",
                     "-c", "synchronous_commit=off"])
        for _ in range(60):
            ready = capture(["docker", "exec", CONTAINER, "pg_isready", "-h", "127.0.0.1", "-U", DB_USER, "-d", DB_NAME], check=False)
            if ready.returncode == 0:
                log(f"ready: BENCH_JDBC_URL=jdbc:postgresql://127.0.0.1:{DB_PORT}/{DB_NAME} (user/password {DB_USER}/{DB_PASSWORD})")
                return
            time.sleep(1)
        raise SystemExit("Postgres did not become ready in 60 s")
    elif args.action == "stop":
        capture(["docker", "rm", "-f", CONTAINER], check=False)
        log(f"{CONTAINER} removed")
    else:
        print(capture(["docker", "ps", "-a", "--filter", f"name=^{CONTAINER}$"], check=False).stdout)


# --- commands: build / list ---------------------------------------------------------------------

def cmd_build(args):
    gradlew = os.path.join(ROOT, "gradlew")
    cmd = [gradlew, "-p", ROOT, "benchDist"] + [f"-P{p}" for p in args.gradle_property]
    log(" ".join(cmd))
    subprocess.run(cmd, check=True)


def cmd_list(args):
    for d in discover():
        print(f"{d.id:24} {d.info['category']:14} {d.info['style']:9} unsupported={d.info['unsupported']} {d.key_versions()}")


# --- commands: run ------------------------------------------------------------------------------

def jmh_command(dist, scenarios, config, mode, threads, virtual, opts, out_json):
    regex = r".*Benchmark\.(" + "|".join(scenarios) + r")$"
    jvm_args = list(JMH_JVM_ARGS) + (["-Djmh.executor=VIRTUAL"] if virtual else [])
    cmd = [java(), "-cp", dist.classpath, "org.openjdk.jmh.Main", regex,
           "-p", f"config={config}",
           "-f", str(opts["forks"]),
           "-wi", str(opts["warmup_iterations"]), "-w", opts["warmup_time"],
           "-i", str(opts["iterations"]), "-r", opts["time"],
           "-bm", mode, "-tu", "s" if mode == "thrpt" else "us",
           "-t", str(threads),
           "-rf", "json", "-rff", out_json,
           "-jvmArgs", " ".join(jvm_args)]
    if opts["gc"]:
        cmd += ["-prof", "gc"]
    return cmd, jvm_args


def run_logged(cmd, env, log_file):
    with open(log_file, "w", encoding="utf-8") as logf:
        logf.write(" ".join(cmd) + "\n")
        process = subprocess.Popen(cmd, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        for line in process.stdout:
            logf.write(line)
            # the full output is in the log file; the console gets progress and results only
            if line.startswith(("# Benchmark:", "# Run progress", "Iteration", "Result ")) or " statements/op" in line \
                    or re.match(r"^\S+Benchmark\.\S+\s", line) or line.startswith("Benchmark "):
                sys.stdout.write("    " + line)
        return process.wait()


def parse_jmh(path, dist, config, executor):
    if not os.path.exists(path):
        return []
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    records = []
    for entry in data:
        scenario = entry["benchmark"].rsplit(".", 1)[1]
        primary = entry["primaryMetric"]
        secondary = entry.get("secondaryMetrics", {})
        percentiles = primary.get("scorePercentiles", {})
        mode = entry["mode"]
        record = base_record(dist, config, scenario)
        record.update({
            "mode": mode,
            "threads": entry["threads"],
            "executor": executor,
            "forks": entry["forks"],
            "score": primary["score"],
            "error": primary.get("scoreError"),
            "unit": primary["scoreUnit"],
            "p50": percentiles.get("50.0") if mode == "sample" else None,
            "p99": percentiles.get("99.0") if mode == "sample" else None,
            "allocBytesPerOp": secondary.get("gc.alloc.rate.norm", {}).get("score"),
        })
        records.append(record)
    return records


def base_record(dist, config, scenario):
    group = next((g for g in ("reads", "writes", "tx", "reactive") if scenario in GROUPS[g]), "")
    return {"implementation": dist.id, "framework": dist.info["framework"], "category": dist.info["category"],
            "config": config, "scenario": scenario, "group": group}


def cmd_run(args):
    profile = dict(PROFILES[args.profile])
    for key in ("forks", "warmup_iterations", "warmup_time", "iterations", "time"):
        if getattr(args, key) is not None:
            profile[key] = getattr(args, key)
    if args.modes:
        profile["modes"] = args.modes.split(",")
    if args.threads:
        profile["threads"] = [int(t) for t in args.threads.split(",")]
    if args.virtual is not None:
        profile["virtual"] = args.virtual
    if args.no_statements:
        profile["statements"] = False
    profile["gc"] = not args.no_gc
    scenario_spec = args.scenarios or profile["scenarios"]

    env = db_env()
    dists = select_dists(args.impl)
    out_dir = args.out or os.path.join(ROOT, "results", datetime.datetime.now().strftime("%Y%m%d-%H%M%S") + f"-{args.profile}")
    raw_dir = os.path.join(out_dir, "raw")
    os.makedirs(raw_dir, exist_ok=True)

    capture([java(), "-cp", dists[0].classpath, "bench.common.tools.DbAdmin", "prepare"], env=env)
    run = {
        "environment": environment(env, dists[0], JMH_JVM_ARGS),
        "options": {"profile": args.profile, "scenarios": scenario_spec, "configs": args.config, **profile},
        "implementations": {},
        "results": [],
    }
    if args.profile == "smoke" or profile["forks"] < 3:
        run["environment"]["warning"] = "NOT REPRESENTATIVE: fewer than 3 forks / short iterations (smoke run)"

    for dist in dists:
        scenarios = select_scenarios(scenario_spec, dist)
        run["implementations"][dist.id] = {"info": dist.info, "keyVersions": dist.key_versions(), "resolvedVersions": dist.versions}
        if not scenarios:
            log(f"{dist.id}: no selected scenario is supported, skipping")
            continue
        configs = [c for c in args.config.split(",") if c in dist.info["configurations"]]
        for config in configs:
            statements = {}
            if profile["statements"]:
                out = os.path.join(raw_dir, f"{dist.id}-{config}-statements.json")
                log(f"{dist.id} [{config}]: counting statements")
                rc = run_logged([java(), *JMH_JVM_ARGS, "-cp", dist.classpath, "bench.common.tools.StatementCounter",
                                 config, out, str(args.count_warmup), str(args.count_ops), ",".join(scenarios)],
                                env, os.path.join(raw_dir, f"{dist.id}-{config}-statements.log"))
                if rc == 0:
                    with open(out, encoding="utf-8") as f:
                        counted = json.load(f)
                    statements = {k: (v["statementsPerOp"], v["txControlPerOp"]) for k, v in counted["scenarios"].items()}
                    run["implementations"][dist.id].setdefault("statementSource", counted["source"])
                else:
                    log(f"{dist.id} [{config}]: statement counting failed (exit {rc}), see raw log")
            variants = [(mode, t, "platform") for mode in profile["modes"] for t in profile["threads"]]
            if profile["virtual"]:
                variants += [(mode, max(profile["threads"]), "virtual") for mode in profile["modes"]]
            for mode, threads, executor in variants:
                name = f"{dist.id}-{config}-{mode}-t{threads}-{executor}"
                out_json = os.path.join(raw_dir, name + ".json")
                cmd, _ = jmh_command(dist, scenarios, config, mode, threads, executor == "virtual", profile, out_json)
                log(f"{dist.id} [{config}] {mode}, {threads} {executor} thread(s): {len(scenarios)} scenario(s)")
                if args.dry_run:
                    print(" ".join(cmd))
                    continue
                rc = run_logged(cmd, env, os.path.join(raw_dir, name + ".log"))
                if rc != 0:
                    log(f"JMH exited with {rc}; see {name}.log")
                for record in parse_jmh(out_json, dist, config, executor):
                    record["statementsPerOp"], record["txControlPerOp"] = statements.get(record["scenario"], (None, None))
                    run["results"].append(record)
            # statement counts are recorded even when no JMH result exists for a scenario
            for scenario, (count, tx_count) in statements.items():
                if not any(r["implementation"] == dist.id and r["config"] == config and r["scenario"] == scenario for r in run["results"]):
                    record = base_record(dist, config, scenario)
                    record.update({"mode": "count", "statementsPerOp": count, "txControlPerOp": tx_count})
                    run["results"].append(record)

    write_results(out_dir, run)


def write_results(out_dir, run):
    with open(os.path.join(out_dir, "run.json"), "w", encoding="utf-8") as f:
        json.dump(run, f, indent=2)
    columns = ["implementation", "framework", "category", "config", "scenario", "group", "mode", "threads", "executor",
               "score", "error", "unit", "p50", "p99", "allocBytesPerOp", "statementsPerOp", "txControlPerOp",
               "timeToFirstQueryMs", "rssMb", "peakRssMb", "forks"]
    with open(os.path.join(out_dir, "results.csv"), "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=columns, extrasaction="ignore")
        writer.writeheader()
        for record in run["results"]:
            writer.writerow(record)
    log(f"results written to {out_dir}")


# --- commands: startup --------------------------------------------------------------------------

def measure_startup(dist, config, env):
    """One process: launch, wait for BENCH_READY, sample RSS, let it exit, read peak RSS."""
    time_cmd = ["/usr/bin/time", "-l"] if sys.platform == "darwin" else ["/usr/bin/time", "-v"]
    cmd = time_cmd + [java(), *STARTUP_JVM_ARGS, "-cp", dist.classpath, "bench.common.tools.Startup", config]
    start = time.monotonic()
    process = subprocess.Popen(cmd, env=env, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    ready_ms = jvm_ms = pid = None
    for line in process.stdout:
        if line.startswith("BENCH_READY"):
            ready_ms = (time.monotonic() - start) * 1000
            parts = line.split()
            jvm_ms = int(parts[1])
            pid = int(parts[2])
            break
    rss_mb = None
    if pid:
        out = capture(["ps", "-o", "rss=", "-p", str(pid)], check=False).stdout.strip()
        rss_mb = int(out) / 1024 if out.isdigit() else None
    _, stderr = process.communicate("\n", timeout=60)
    if ready_ms is None:
        raise SystemExit(f"{dist.id}: startup probe failed:\n{stderr}")
    peak = None
    if sys.platform == "darwin":
        match = re.search(r"(\d+)\s+maximum resident set size", stderr)
        peak = int(match.group(1)) / (1024 * 1024) if match else None
    else:
        match = re.search(r"Maximum resident set size \(kbytes\): (\d+)", stderr)
        peak = int(match.group(1)) / 1024 if match else None
    return {"wallMs": ready_ms, "jvmMs": jvm_ms, "rssMb": rss_mb, "peakRssMb": peak}


def cmd_startup(args):
    env = db_env()
    dists = select_dists(args.impl)
    out_dir = args.out or os.path.join(ROOT, "results", datetime.datetime.now().strftime("%Y%m%d-%H%M%S") + "-startup")
    raw_dir = os.path.join(out_dir, "raw")
    os.makedirs(raw_dir, exist_ok=True)
    capture([java(), "-cp", dists[0].classpath, "bench.common.tools.DbAdmin", "prepare"], env=env)
    run = {"environment": environment(env, dists[0], STARTUP_JVM_ARGS),
           "options": {"runs": args.runs, "configs": args.config}, "implementations": {}, "results": []}
    for dist in dists:
        run["implementations"][dist.id] = {"info": dist.info, "keyVersions": dist.key_versions(), "resolvedVersions": dist.versions}
        for config in [c for c in args.config.split(",") if c in dist.info["configurations"]]:
            log(f"{dist.id} [{config}]: {args.runs} startup run(s)")
            samples = [measure_startup(dist, config, env) for _ in range(args.runs)]
            with open(os.path.join(raw_dir, f"{dist.id}-{config}-startup.json"), "w", encoding="utf-8") as f:
                json.dump(samples, f, indent=2)
            record = base_record(dist, config, "startup")
            record.update({
                "mode": "startup", "threads": 1, "executor": "process",
                "timeToFirstQueryMs": median([s["wallMs"] for s in samples]),
                "rssMb": median([s["rssMb"] for s in samples if s["rssMb"] is not None]),
                "peakRssMb": median([s["peakRssMb"] for s in samples if s["peakRssMb"] is not None]),
                "score": median([s["wallMs"] for s in samples]), "error": max(s["wallMs"] for s in samples), "unit": "ms (median; error = max)",
            })
            run["results"].append(record)
            log(f"  time to first query {record['timeToFirstQueryMs']:.0f} ms, RSS {record['rssMb'] or 0:.0f} MB, peak {record['peakRssMb'] or 0:.0f} MB")
    write_results(out_dir, run)


def median(values):
    values = sorted(v for v in values if v is not None)
    if not values:
        return None
    mid = len(values) // 2
    return values[mid] if len(values) % 2 else (values[mid - 1] + values[mid]) / 2


# --- main ---------------------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)

    db = sub.add_parser("db", help="start/stop/status of the benchmark Postgres container")
    db.add_argument("action", choices=["start", "stop", "status"])
    db.set_defaults(func=cmd_db)

    build = sub.add_parser("build", help="./gradlew benchDist")
    build.add_argument("-P", dest="gradle_property", action="append", default=[],
                       help="Gradle property, e.g. -P micronautDataVersion=5.3.0-SNAPSHOT -P useMavenLocal")
    build.set_defaults(func=cmd_build)

    lst = sub.add_parser("list", help="list built implementations")
    lst.set_defaults(func=cmd_list)

    run = sub.add_parser("run", help="run JMH benchmarks and count statements")
    run.add_argument("--impl", default="all", help="comma-separated implementation ids, or 'all'")
    run.add_argument("--scenarios", help="comma-separated scenario ids or groups (reads, writes, tx, reactive, concurrency, all)")
    run.add_argument("--config", default="default,tuned", help="configurations to run (default,tuned)")
    run.add_argument("--profile", choices=sorted(PROFILES), default="smoke")
    run.add_argument("--forks", type=int)
    run.add_argument("--warmup-iterations", type=int)
    run.add_argument("--warmup-time")
    run.add_argument("--iterations", type=int)
    run.add_argument("--time")
    run.add_argument("--modes", help="JMH modes, e.g. thrpt,sample")
    run.add_argument("--threads", help="thread counts, e.g. 1,16,64")
    run.add_argument("--virtual", action=argparse.BooleanOptionalAction, default=None, help="also run with virtual threads")
    run.add_argument("--no-statements", action="store_true", help="skip the pg_stat_statements counting pass")
    run.add_argument("--no-gc", action="store_true", help="skip -prof gc")
    run.add_argument("--count-warmup", type=int, default=20)
    run.add_argument("--count-ops", type=int, default=50)
    run.add_argument("--out", help="output directory (default results/<timestamp>-<profile>)")
    run.add_argument("--dry-run", action="store_true", help="print the JMH commands only")
    run.set_defaults(func=cmd_run)

    startup = sub.add_parser("startup", help="process-level time to first query and RSS")
    startup.add_argument("--impl", default="all")
    startup.add_argument("--config", default="default")
    startup.add_argument("--runs", type=int, default=10)
    startup.add_argument("--out")
    startup.set_defaults(func=cmd_startup)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
