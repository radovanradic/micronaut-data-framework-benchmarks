package bench.common.check;

import bench.common.dataset.DbSettings;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Database for the correctness checks: the one in {@code BENCH_JDBC_URL} if set, otherwise a
 * Testcontainers PostgreSQL started once per test JVM with the same server settings the runner uses.
 */
public final class TestDatabase {

    private static DbSettings settings;

    private TestDatabase() {
    }

    public static synchronized DbSettings settings() {
        if (settings == null) {
            String url = System.getenv("BENCH_JDBC_URL");
            settings = url != null && !url.isBlank() ? DbSettings.fromEnvironment() : startContainer();
        }
        return settings;
    }

    private static DbSettings startContainer() {
        String image = System.getProperty("bench.postgres.image", "postgres:18.6");
        PostgreSQLContainer container = new PostgreSQLContainer(DockerImageName.parse(image))
            .withDatabaseName("bench")
            .withUsername("bench")
            .withPassword("bench")
            .withCommand("postgres",
                "-c", "shared_preload_libraries=pg_stat_statements",
                "-c", "max_connections=200",
                "-c", "synchronous_commit=off");
        // stopped by Testcontainers' resource reaper when the test JVM exits
        container.start();
        String jdbcUrl = "jdbc:postgresql://%s:%d/bench".formatted(container.getHost(), container.getMappedPort(5432));
        return new DbSettings(jdbcUrl, "bench", "bench", DbSettings.DEFAULT_POOL_SIZE);
    }
}
