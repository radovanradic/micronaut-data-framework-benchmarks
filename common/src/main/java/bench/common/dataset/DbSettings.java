package bench.common.dataset;

import java.net.URI;

/**
 * Database connection settings shared by every implementation.
 *
 * <p>Benchmarks read them from the environment: {@code BENCH_JDBC_URL} (required),
 * {@code BENCH_DB_USER}, {@code BENCH_DB_PASSWORD} and {@code BENCH_POOL_SIZE} (default 16, SPEC.md
 * fairness rule 3). R2DBC implementations derive their URL from the JDBC URL.
 *
 * @param jdbcUrl  {@code jdbc:postgresql://host:port/database}, without driver parameters
 * @param user     user name
 * @param password password
 * @param poolSize fixed connection pool size (minimum idle = maximum)
 */
public record DbSettings(String jdbcUrl, String user, String password, int poolSize) {

    public static final int DEFAULT_POOL_SIZE = 16;

    public static DbSettings fromEnvironment() {
        String url = System.getenv("BENCH_JDBC_URL");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("BENCH_JDBC_URL is not set; start the database with runner/bench.py or point it at one");
        }
        String pool = System.getenv("BENCH_POOL_SIZE");
        return new DbSettings(
            url,
            envOrDefault("BENCH_DB_USER", "bench"),
            envOrDefault("BENCH_DB_PASSWORD", "bench"),
            pool == null || pool.isBlank() ? DEFAULT_POOL_SIZE : Integer.parseInt(pool));
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private URI uri() {
        if (!jdbcUrl.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException("Only jdbc:postgresql:// URLs are supported: " + jdbcUrl);
        }
        return URI.create(jdbcUrl.substring("jdbc:".length()));
    }

    public String host() {
        return uri().getHost();
    }

    public int port() {
        int port = uri().getPort();
        return port == -1 ? 5432 : port;
    }

    public String database() {
        return uri().getPath().substring(1);
    }

    /**
     * @return {@code r2dbc:postgresql://host:port/database}
     */
    public String r2dbcUrl() {
        return "r2dbc:postgresql://%s:%d/%s".formatted(host(), port(), database());
    }
}
