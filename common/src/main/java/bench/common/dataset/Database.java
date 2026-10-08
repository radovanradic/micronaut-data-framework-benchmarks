package bench.common.dataset;

import bench.common.model.Inputs.NewBook;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plain-JDBC administration of the benchmark database: schema, seed, reset between benchmarks, and
 * {@code pg_stat_statements} access. Independent of any framework.
 *
 * <p>Every statement issued here starts with {@link #ADMIN_MARKER}, so the statement counter can
 * exclude harness statements from the per-operation counts.
 */
public final class Database {

    /** SQL comment prefixed to every harness statement, for readability in server logs. */
    public static final String ADMIN_MARKER = "/* bench-admin */ ";

    /** Table alias of the harness insert for the delete scenario; excluded from statement counts. */
    private static final String FIXTURE_ALIAS = "bench_fixture";

    private static final List<String> TABLES = List.of("author", "book", "purchase_order", "order_line", "stock_item");
    private static final int BATCH = 1_000;

    private final DbSettings settings;

    public Database(DbSettings settings) {
        this.settings = settings;
    }

    public DbSettings settings() {
        return settings;
    }

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(settings.jdbcUrl(), settings.user(), settings.password());
    }

    /**
     * Makes sure the schema and seed data are present and in their initial state: creates and seeds
     * the database if needed, otherwise resets the rows changed by write scenarios.
     */
    public void prepare() {
        try (Connection c = connect()) {
            if (isSeeded(c)) {
                reset(c);
                if (!seedCountsMatch(c)) {
                    createAndSeed(c);
                }
            } else {
                createAndSeed(c);
            }
            vacuum(c);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to prepare the benchmark database", e);
        }
    }

    private boolean isSeeded(Connection c) throws SQLException {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(ADMIN_MARKER + "SELECT value FROM bench_meta WHERE name = 'seed_version'")) {
            return rs.next() && rs.getString(1).equals(String.valueOf(Seed.VERSION));
        } catch (SQLException e) {
            // bench_meta does not exist yet
            return false;
        }
    }

    private boolean seedCountsMatch(Connection c) throws SQLException {
        return count(c, "author") == Seed.AUTHORS
            && count(c, "book") == Seed.BOOKS
            && count(c, "purchase_order") == Seed.ORDERS
            && count(c, "order_line") == (long) Seed.ORDERS * Seed.LINES_PER_ORDER
            && count(c, "stock_item") == Seed.STOCK_ITEMS;
    }

    private static long count(Connection c, String table) throws SQLException {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(ADMIN_MARKER + "SELECT count(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private void createAndSeed(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            try {
                s.execute(ADMIN_MARKER + "CREATE EXTENSION IF NOT EXISTS pg_stat_statements");
            } catch (SQLException e) {
                // statement counting will report "unavailable"
            }
            for (String sql : schemaStatements()) {
                s.execute(ADMIN_MARKER + sql);
            }
        }
        c.setAutoCommit(false);
        try {
            insertAuthors(c);
            insertBooks(c, 1, Seed.BOOKS);
            insertOrders(c);
            insertStock(c);
            try (Statement s = c.createStatement()) {
                for (String table : TABLES) {
                    s.execute(ADMIN_MARKER + "SELECT setval(pg_get_serial_sequence('%s', 'id'), (SELECT max(id) FROM %s))".formatted(table, table));
                }
                s.execute(ADMIN_MARKER + "INSERT INTO bench_meta (name, value) VALUES ('seed_version', '" + Seed.VERSION + "')");
            }
            c.commit();
        } finally {
            c.setAutoCommit(true);
        }
    }

    /**
     * Deletes every row created by write scenarios and restores mutable rows to their seed values.
     */
    private void reset(Connection c) throws SQLException {
        c.setAutoCommit(false);
        try {
            try (Statement s = c.createStatement()) {
                s.executeUpdate(ADMIN_MARKER + "DELETE FROM book WHERE id > " + Seed.BOOKS + " OR author_id = " + Seed.WRITE_AUTHOR_ID);
                s.executeUpdate(ADMIN_MARKER + "DELETE FROM order_line WHERE order_id > " + Seed.ORDERS);
                s.executeUpdate(ADMIN_MARKER + "DELETE FROM purchase_order WHERE id > " + Seed.ORDERS);
                s.executeUpdate(ADMIN_MARKER + "UPDATE stock_item SET quantity = " + Seed.STOCK_QUANTITY + ", version = 0");
            }
            insertBooks(c, Seed.FIRST_MUTABLE_BOOK_ID, Seed.BOOKS);
            c.commit();
        } finally {
            c.setAutoCommit(true);
        }
    }

    private static void vacuum(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute(ADMIN_MARKER + "VACUUM ANALYZE " + String.join(", ", TABLES));
        }
    }

    private static void insertAuthors(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(ADMIN_MARKER + "INSERT INTO author (id, name, country) VALUES (?, ?, ?)")) {
            for (long id = 1; id <= Seed.AUTHORS; id++) {
                ps.setLong(1, id);
                ps.setString(2, Seed.authorName(id));
                ps.setString(3, Seed.authorCountry(id));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void insertBooks(Connection c, long fromId, long toId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(ADMIN_MARKER
            + "INSERT INTO book (id, author_id, title, isbn, pages, price, published_on) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            int pending = 0;
            for (long id = fromId; id <= toId; id++) {
                Seed.SeedBook b = Seed.book(id);
                ps.setLong(1, b.id());
                ps.setLong(2, b.authorId());
                ps.setString(3, b.title());
                ps.setString(4, b.isbn());
                ps.setInt(5, b.pages());
                ps.setBigDecimal(6, b.price());
                ps.setDate(7, Date.valueOf(b.publishedOn()));
                ps.addBatch();
                if (++pending == BATCH) {
                    ps.executeBatch();
                    pending = 0;
                }
            }
            ps.executeBatch();
        }
    }

    private static void insertOrders(Connection c) throws SQLException {
        try (PreparedStatement order = c.prepareStatement(ADMIN_MARKER
                 + "INSERT INTO purchase_order (id, reference, customer, created_at) VALUES (?, ?, ?, ?)");
             PreparedStatement line = c.prepareStatement(ADMIN_MARKER
                 + "INSERT INTO order_line (id, order_id, line_no, product, quantity, unit_price) VALUES (?, ?, ?, ?, ?, ?)")) {
            for (long id = 1; id <= Seed.ORDERS; id++) {
                Seed.SeedOrder o = Seed.order(id);
                order.setLong(1, o.id());
                order.setObject(2, o.reference());
                order.setString(3, o.customer());
                order.setTimestamp(4, Timestamp.from(o.createdAt()));
                order.addBatch();
                for (Seed.SeedOrderLine l : o.lines()) {
                    line.setLong(1, l.id());
                    line.setLong(2, o.id());
                    line.setInt(3, l.lineNo());
                    line.setString(4, l.product());
                    line.setInt(5, l.quantity());
                    line.setBigDecimal(6, l.unitPrice());
                    line.addBatch();
                }
            }
            order.executeBatch();
            line.executeBatch();
        }
    }

    private static void insertStock(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(ADMIN_MARKER + "INSERT INTO stock_item (id, sku, quantity, version) VALUES (?, ?, ?, ?)")) {
            for (long id = 1; id <= Seed.STOCK_ITEMS; id++) {
                Seed.SeedStockItem s = Seed.stockItem(id);
                ps.setLong(1, s.id());
                ps.setString(2, s.sku());
                ps.setInt(3, s.quantity());
                ps.setInt(4, s.version());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static List<String> schemaStatements() {
        try (InputStream in = Database.class.getResourceAsStream("/bench/schema.sql")) {
            if (in == null) {
                throw new IllegalStateException("bench/schema.sql not found");
            }
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            List<String> statements = new ArrayList<>();
            for (String part : sql.split(";")) {
                String statement = part.lines()
                    .filter(l -> !l.trim().startsWith("--"))
                    .reduce("", (a, b) -> a + "\n" + b)
                    .trim();
                if (!statement.isEmpty()) {
                    statements.add(statement);
                }
            }
            return statements;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Inserts a book with plain JDBC (auto-commit) and returns its id. Used to provide rows for the
     * {@code delete} scenario outside the measured time.
     */
    public static long insertFixtureBook(Connection c, NewBook b) throws SQLException {
        // the table alias makes the statement recognizable in pg_stat_statements (comments are not kept)
        try (PreparedStatement ps = c.prepareStatement(ADMIN_MARKER
            + "INSERT INTO book AS " + FIXTURE_ALIAS + " (author_id, title, isbn, pages, price, published_on) VALUES (?, ?, ?, ?, ?, ?) RETURNING id")) {
            ps.setLong(1, b.authorId());
            ps.setString(2, b.title());
            ps.setString(3, b.isbn());
            ps.setInt(4, b.pages());
            ps.setBigDecimal(5, b.price());
            ps.setDate(6, Date.valueOf(b.publishedOn()));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    // --- server information ---

    public Map<String, String> serverInfo() {
        Map<String, String> info = new LinkedHashMap<>();
        try (Connection c = connect(); Statement s = c.createStatement()) {
            for (String name : List.of("server_version", "max_connections", "shared_buffers", "work_mem",
                "synchronous_commit", "fsync", "shared_preload_libraries", "default_transaction_isolation")) {
                try (ResultSet rs = s.executeQuery(ADMIN_MARKER + "SHOW " + name)) {
                    rs.next();
                    info.put(name, rs.getString(1));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read server settings", e);
        }
        return info;
    }

    // --- pg_stat_statements ---

    /**
     * One {@code pg_stat_statements} entry.
     *
     * @param query normalized statement text
     * @param calls number of executions
     */
    public record StatementStat(String query, long calls) {
    }

    public boolean statementStatsAvailable() {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.executeQuery(ADMIN_MARKER + "SELECT count(*) FROM pg_stat_statements").close();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Resets the statistics. Use one long-lived connection for {@link #resetStatementStats(Connection)}
     * and {@link #statementStats(Connection)}: opening a connection executes statements itself.
     */
    public static void resetStatementStats(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.executeQuery(ADMIN_MARKER + "SELECT pg_stat_statements_reset()").close();
        }
    }

    /**
     * @return statements executed in the connection's database since the last reset, excluding the
     * harness's own statements
     */
    public static List<StatementStat> statementStats(Connection c) throws SQLException {
        List<StatementStat> stats = new ArrayList<>();
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(ADMIN_MARKER
                 + "SELECT query, calls FROM pg_stat_statements WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database()) ORDER BY calls DESC")) {
            while (rs.next()) {
                String query = rs.getString(1);
                if (query != null && !query.contains("pg_stat_statements") && !query.contains(FIXTURE_ALIAS)
                    && !isConnectionSetup(query)) {
                    stats.add(new StatementStat(query, rs.getLong(2)));
                }
            }
        }
        return stats;
    }

    /**
     * Statements drivers run when they open a connection, not per operation: pgjdbc's
     * {@code SET application_name}, r2dbc-postgresql's isolation-level and type lookups.
     */
    private static boolean isConnectionSetup(String query) {
        return query.startsWith("SET application_name")
            || query.equals("SHOW TRANSACTION ISOLATION LEVEL")
            || query.startsWith("SELECT oid, * FROM pg_catalog.pg_type WHERE typname IN");
    }
}
