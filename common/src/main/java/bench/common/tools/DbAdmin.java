package bench.common.tools;

import bench.common.dataset.Database;
import bench.common.dataset.DbSettings;

/**
 * Database administration for the runner: {@code prepare} creates/resets the dataset, {@code info}
 * prints the server version and settings as JSON. Uses {@code BENCH_JDBC_URL} etc.
 */
public final class DbAdmin {

    private DbAdmin() {
    }

    public static void main(String[] args) {
        Database db = new Database(DbSettings.fromEnvironment());
        String command = args.length == 0 ? "info" : args[0];
        switch (command) {
            case "prepare" -> db.prepare();
            case "info" -> System.out.println(Json.write(db.serverInfo()));
            default -> throw new IllegalArgumentException("Unknown command: " + command + " (prepare | info)");
        }
    }
}
