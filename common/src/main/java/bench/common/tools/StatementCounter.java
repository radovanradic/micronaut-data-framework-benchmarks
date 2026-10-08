package bench.common.tools;

import bench.common.dataset.Database;
import bench.common.dataset.Database.StatementStat;
import bench.common.dataset.DbSettings;
import bench.common.scenario.BlockingOps;
import bench.common.scenario.ReactiveOps;
import bench.common.scenario.Scenario;
import bench.common.scenario.Workload;
import bench.common.spi.BenchmarkTarget;
import bench.common.spi.ImplementationInfo;
import bench.common.spi.TargetFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Counts the SQL statements each scenario executes, using {@code pg_stat_statements} (SPEC.md
 * section 6). For every scenario: warm up, reset the statistics, run N operations, divide. Reports data
 * statements ({@code statementsPerOp}) and transaction control ({@code txControlPerOp}) separately.
 *
 * <p>Usage: {@code StatementCounter <config> <output.json> [warmupOps] [measuredOps] [scenario,...]}.
 * Must run alone against the database: the statistics are server-wide.
 */
public final class StatementCounter {

    /**
     * Transaction-control statements are reported separately: pg_stat_statements records some of them
     * (e.g. {@code BEGIN READ ONLY}) but not the {@code BEGIN}/{@code COMMIT} that pgjdbc sends through
     * the extended protocol, so their count is a lower bound and not comparable across drivers.
     */
    private static final Set<String> TX_CONTROL = Set.of("begin", "start", "commit", "rollback", "savepoint", "release");

    private StatementCounter() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: StatementCounter <config> <output.json> [warmupOps] [measuredOps] [scenario,...]");
        }
        String config = args[0];
        Path output = Path.of(args[1]);
        int warmup = args.length > 2 ? Integer.parseInt(args[2]) : 20;
        int measured = args.length > 3 ? Integer.parseInt(args[3]) : 50;
        Set<String> only = args.length > 4 && !args[4].isBlank() ? Set.of(args[4].split(",")) : Set.of();

        DbSettings settings = DbSettings.fromEnvironment();
        Database db = new Database(settings);
        db.prepare();
        TargetFactory factory = TargetFactory.load();
        ImplementationInfo info = factory.info();

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("implementation", info.id());
        json.put("config", config);
        json.put("source", "pg_stat_statements");
        json.put("measuredOps", measured);
        Map<String, Object> scenarios = new LinkedHashMap<>();
        json.put("scenarios", scenarios);

        if (!db.statementStatsAvailable()) {
            json.put("source", "unavailable (pg_stat_statements not loaded)");
            Files.writeString(output, Json.write(json));
            return;
        }

        try (BenchmarkTarget target = factory.start(config, settings);
             Connection fixtures = db.connect();
             Connection stats = db.connect()) {
            Workload w = new Workload(0, 1, 'S');
            for (Scenario scenario : info.supportedScenarios()) {
                if (!only.isEmpty() && !only.contains(scenario.id())) {
                    continue;
                }
                for (int i = 0; i < warmup; i++) {
                    run(scenario, target, w, fixtures);
                }
                Database.resetStatementStats(stats);
                for (int i = 0; i < measured; i++) {
                    run(scenario, target, w, fixtures);
                }
                scenarios.put(scenario.id(), summarize(Database.statementStats(stats), measured));
                System.out.println(scenario.id() + ": " + ((Map<?, ?>) scenarios.get(scenario.id())).get("statementsPerOp") + " statements/op");
            }
        }
        Files.writeString(output, Json.write(json));
    }

    private static void run(Scenario scenario, BenchmarkTarget target, Workload w, Connection fixtures) throws SQLException {
        if (scenario.isReactive()) {
            ReactiveOps.run(scenario, target.reactive(), w);
        } else {
            long fixture = BlockingOps.needsFixtureRow(scenario) ? Database.insertFixtureBook(fixtures, w.newBook()) : -1;
            BlockingOps.run(scenario, target.blocking(), w, fixture);
        }
    }

    private static Map<String, Object> summarize(List<StatementStat> stats, int ops) {
        long data = stats.stream().filter(s -> !TX_CONTROL.contains(kind(s.query()))).mapToLong(StatementStat::calls).sum();
        long txControl = stats.stream().filter(s -> TX_CONTROL.contains(kind(s.query()))).mapToLong(StatementStat::calls).sum();
        Map<String, Double> byKind = new TreeMap<>(stats.stream().collect(Collectors.groupingBy(
            s -> kind(s.query()), Collectors.summingDouble(s -> (double) s.calls() / ops))));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("statementsPerOp", round((double) data / ops));
        result.put("txControlPerOp", round((double) txControl / ops));
        result.put("byKind", byKind.entrySet().stream().collect(Collectors.toMap(
            Map.Entry::getKey, e -> round(e.getValue()), (a, b) -> a, LinkedHashMap::new)));
        result.put("statements", stats.stream().limit(15).map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("perOp", round((double) s.calls() / ops));
            m.put("query", s.query().replaceAll("\\s+", " ").trim());
            return m;
        }).toList());
        return result;
    }

    private static String kind(String query) {
        String q = query.trim();
        while (q.startsWith("/*")) {
            int end = q.indexOf("*/");
            q = end < 0 ? "" : q.substring(end + 2).trim();
        }
        String first = Arrays.stream(q.split("\\s+", 2)).findFirst().orElse("").toLowerCase(Locale.ROOT);
        return switch (first) {
            case "select", "with", "values" -> "select";
            case "insert", "update", "delete", "begin", "commit", "rollback", "set", "savepoint", "release", "start", "show" -> first;
            default -> "other";
        };
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
