package bench.common.tools;

import bench.common.dataset.DbSettings;
import bench.common.dataset.Seed;
import bench.common.scenario.Publishers;
import bench.common.spi.BenchmarkTarget;
import bench.common.spi.TargetFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;

/**
 * Startup probe for the process-level harness (SPEC.md section 4.6): starts the framework, executes
 * the first query, prints {@code BENCH_READY <ms since process start> <pid>}, then waits for a line on stdin
 * (so the harness can sample RSS) before shutting down.
 *
 * <p>Usage: {@code Startup <config>}.
 */
public final class Startup {

    private Startup() {
    }

    public static void main(String[] args) throws Exception {
        String config = args.length > 0 ? args[0] : "default";
        TargetFactory factory = TargetFactory.load();
        try (BenchmarkTarget target = factory.start(config, DbSettings.fromEnvironment())) {
            Object first = factory.info().reactive()
                ? Publishers.last(target.reactive().findBookById(1))
                : target.blocking().findBookById(1);
            if (first == null) {
                throw new IllegalStateException("Book 1 not found; is the database seeded? (" + Seed.BOOKS + " books expected)");
            }
            Instant processStart = ProcessHandle.current().info().startInstant().orElseThrow();
            System.out.println("BENCH_READY " + Duration.between(processStart, Instant.now()).toMillis() + " " + ProcessHandle.current().pid());
            System.out.flush();
            new BufferedReader(new InputStreamReader(System.in)).readLine();
        }
        System.exit(0);
    }
}
