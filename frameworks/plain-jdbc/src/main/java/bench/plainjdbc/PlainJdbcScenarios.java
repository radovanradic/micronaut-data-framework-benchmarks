package bench.plainjdbc;

import bench.common.model.BookSummaryView;
import bench.common.model.BookView;
import bench.common.model.Inputs.BookFilter;
import bench.common.model.Inputs.BookPage;
import bench.common.model.Inputs.NewBook;
import bench.common.model.Inputs.NewOrder;
import bench.common.model.Inputs.NewOrderLine;
import bench.common.model.Inputs.SortOrder;
import bench.common.model.OrderView;
import bench.common.model.StockItemView;
import bench.common.spi.BlockingScenarios;
import bench.common.spi.IntentionalRollbackException;
import bench.common.spi.OptimisticConflictException;
import bench.plainjdbc.JdbcRows.Book;
import bench.plainjdbc.JdbcRows.BookSummary;
import bench.plainjdbc.JdbcRows.Order;
import bench.plainjdbc.JdbcRows.OrderLine;
import bench.plainjdbc.JdbcRows.StockItem;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static bench.plainjdbc.JdbcRows.BOOK_COLUMNS;

/**
 * The baseline: what a careful developer writes by hand with plain JDBC. Single-statement operations
 * run in auto-commit; multi-statement ones in an explicit transaction.
 */
final class PlainJdbcScenarios implements BlockingScenarios {

    private static final Map<String, String> SORTABLE_COLUMNS = Map.of(
        "id", "id", "title", "title", "price", "price", "pages", "pages", "publishedOn", "published_on");

    private final DataSource dataSource;

    PlainJdbcScenarios(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @FunctionalInterface
    private interface Work<T> {
        T run(Connection c) throws SQLException;
    }

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private <T> T withConnection(Work<T> work) {
        try (Connection c = dataSource.getConnection()) {
            return work.run(c);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T inTransaction(boolean readOnly, Work<T> work) {
        return withConnection(c -> inTransaction(c, readOnly, work));
    }

    private static <T> T inTransaction(Connection c, boolean readOnly, Work<T> work) throws SQLException {
        c.setAutoCommit(false);
        if (readOnly) {
            c.setReadOnly(true);
        }
        try {
            T result = work.run(c);
            c.commit();
            return result;
        } catch (SQLException | RuntimeException e) {
            c.rollback();
            throw e;
        } finally {
            if (readOnly) {
                c.setReadOnly(false);
            }
            c.setAutoCommit(true);
        }
    }

    private static List<Book> books(Connection c, String sql, Binder binder) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                List<Book> books = new ArrayList<>();
                while (rs.next()) {
                    books.add(JdbcRows.book(rs));
                }
                return books;
            }
        }
    }

    private static Book bookById(Connection c, long id) throws SQLException {
        List<Book> books = books(c, "SELECT " + BOOK_COLUMNS + " FROM book WHERE id = ?", ps -> ps.setLong(1, id));
        return books.isEmpty() ? null : books.getFirst();
    }

    private static Long insertBook(Connection c, NewBook b) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
            "INSERT INTO book (author_id, title, isbn, pages, price, published_on) VALUES (?, ?, ?, ?, ?, ?)", new String[]{"id"})) {
            bindNewBook(ps, b);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static void bindNewBook(PreparedStatement ps, NewBook b) throws SQLException {
        ps.setLong(1, b.authorId());
        ps.setString(2, b.title());
        ps.setString(3, b.isbn());
        ps.setInt(4, b.pages());
        ps.setBigDecimal(5, b.price());
        ps.setObject(6, b.publishedOn());
    }

    // --- reads ---

    @Override
    public BookView findBookById(long id) {
        return withConnection(c -> bookById(c, id));
    }

    @Override
    public BookView findBookByIsbn(String isbn) {
        return withConnection(c -> {
            List<Book> books = books(c, "SELECT " + BOOK_COLUMNS + " FROM book WHERE isbn = ?", ps -> ps.setString(1, isbn));
            return books.isEmpty() ? null : books.getFirst();
        });
    }

    @Override
    public List<? extends BookView> listBooksByAuthor(long authorId) {
        return withConnection(c -> books(c, "SELECT " + BOOK_COLUMNS + " FROM book WHERE author_id = ? ORDER BY id",
            ps -> ps.setLong(1, authorId)));
    }

    @Override
    public List<? extends BookSummaryView> bookSummariesByAuthor(long authorId) {
        return withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT id, title, price FROM book WHERE author_id = ? ORDER BY id")) {
                ps.setLong(1, authorId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<BookSummary> result = new ArrayList<>();
                    while (rs.next()) {
                        result.add(new BookSummary(rs.getLong(1), rs.getString(2), rs.getBigDecimal(3)));
                    }
                    return result;
                }
            }
        });
    }

    @Override
    public BookPage pageBooksByAuthor(long authorId, int pageIndex, int pageSize) {
        return withConnection(c -> {
            List<Book> content = books(c, "SELECT " + BOOK_COLUMNS + " FROM book WHERE author_id = ? ORDER BY id LIMIT ? OFFSET ?", ps -> {
                ps.setLong(1, authorId);
                ps.setInt(2, pageSize);
                ps.setLong(3, (long) pageIndex * pageSize);
            });
            try (PreparedStatement ps = c.prepareStatement("SELECT count(*) FROM book WHERE author_id = ?")) {
                ps.setLong(1, authorId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return new BookPage(content, rs.getLong(1));
                }
            }
        });
    }

    @Override
    public List<? extends BookView> sortedBooksByAuthor(long authorId, List<SortOrder> sort) {
        StringBuilder orderBy = new StringBuilder();
        for (SortOrder order : sort) {
            String column = SORTABLE_COLUMNS.get(order.property());
            if (column == null) {
                throw new IllegalArgumentException("Not sortable: " + order.property());
            }
            orderBy.append(orderBy.isEmpty() ? " ORDER BY " : ", ").append(column).append(order.ascending() ? " ASC" : " DESC");
        }
        String sql = "SELECT " + BOOK_COLUMNS + " FROM book WHERE author_id = ?" + orderBy;
        return withConnection(c -> books(c, sql, ps -> ps.setLong(1, authorId)));
    }

    @Override
    public List<? extends BookView> keysetPageBooksByAuthor(long authorId, long afterId, int size) {
        return withConnection(c -> books(c, "SELECT " + BOOK_COLUMNS + " FROM book WHERE author_id = ? AND id > ? ORDER BY id LIMIT ?", ps -> {
            ps.setLong(1, authorId);
            ps.setLong(2, afterId);
            ps.setInt(3, size);
        }));
    }

    @Override
    public List<? extends BookView> findBooksByIds(List<Long> ids) {
        return withConnection(c -> books(c, "SELECT " + BOOK_COLUMNS + " FROM book WHERE id = ANY (?)",
            ps -> ps.setArray(1, c.createArrayOf("bigint", ids.toArray()))));
    }

    @Override
    public List<? extends BookView> findBooksByFilter(BookFilter filter) {
        StringBuilder sql = new StringBuilder("SELECT " + BOOK_COLUMNS + " FROM book WHERE TRUE");
        List<Object> args = new ArrayList<>();
        if (filter.authorId() != null) {
            sql.append(" AND author_id = ?");
            args.add(filter.authorId());
        }
        if (filter.minPages() != null) {
            sql.append(" AND pages >= ?");
            args.add(filter.minPages());
        }
        if (filter.maxPages() != null) {
            sql.append(" AND pages <= ?");
            args.add(filter.maxPages());
        }
        if (filter.maxPrice() != null) {
            sql.append(" AND price < ?");
            args.add(filter.maxPrice());
        }
        sql.append(" ORDER BY id");
        return withConnection(c -> books(c, sql.toString(), ps -> {
            for (int i = 0; i < args.size(); i++) {
                ps.setObject(i + 1, args.get(i));
            }
        }));
    }

    @Override
    public List<? extends OrderView> findOrdersWithLines(String customer) {
        return withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                "SELECT o.id, o.reference, o.customer, o.created_at, l.id, l.line_no, l.product, l.quantity, l.unit_price "
                    + "FROM purchase_order o LEFT JOIN order_line l ON l.order_id = o.id WHERE o.customer = ? ORDER BY o.id, l.line_no")) {
                ps.setString(1, customer);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Order> orders = new ArrayList<>();
                    Order current = null;
                    while (rs.next()) {
                        long orderId = rs.getLong(1);
                        if (current == null || current.id() != orderId) {
                            current = new Order(orderId, rs.getObject(2, UUID.class), rs.getString(3),
                                rs.getObject(4, OffsetDateTime.class).toInstant());
                            orders.add(current);
                        }
                        long lineId = rs.getLong(5);
                        if (!rs.wasNull()) {
                            current.lines().add(new OrderLine(lineId, rs.getInt(6), rs.getString(7), rs.getInt(8), rs.getBigDecimal(9)));
                        }
                    }
                    return orders;
                }
            }
        });
    }

    // --- writes ---

    @Override
    public Long saveBook(NewBook book) {
        return withConnection(c -> insertBook(c, book));
    }

    @Override
    public List<Long> saveBooks(List<NewBook> books) {
        return inTransaction(false, c -> {
            try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO book (author_id, title, isbn, pages, price, published_on) VALUES (?, ?, ?, ?, ?, ?)", new String[]{"id"})) {
                for (NewBook b : books) {
                    bindNewBook(ps, b);
                    ps.addBatch();
                }
                ps.executeBatch();
                List<Long> ids = new ArrayList<>(books.size());
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    while (keys.next()) {
                        ids.add(keys.getLong(1));
                    }
                }
                return ids;
            }
        });
    }

    @Override
    public BookView updateBookPrice(long id, BigDecimal newPrice) {
        return inTransaction(false, c -> {
            Book book = bookById(c, id);
            try (PreparedStatement ps = c.prepareStatement("UPDATE book SET price = ? WHERE id = ?")) {
                ps.setBigDecimal(1, newPrice);
                ps.setLong(2, id);
                ps.executeUpdate();
            }
            return book.withPrice(newPrice);
        });
    }

    @Override
    public void deleteBook(long id) {
        withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM book WHERE id = ?")) {
                ps.setLong(1, id);
                return ps.executeUpdate();
            }
        });
    }

    @Override
    public Long saveOrder(NewOrder order) {
        return inTransaction(false, c -> {
            long orderId;
            try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO purchase_order (reference, customer, created_at) VALUES (?, ?, ?)", new String[]{"id"})) {
                ps.setObject(1, order.reference());
                ps.setString(2, order.customer());
                ps.setTimestamp(3, Timestamp.from(order.createdAt()));
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    orderId = keys.getLong(1);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO order_line (order_id, line_no, product, quantity, unit_price) VALUES (?, ?, ?, ?, ?)")) {
                for (NewOrderLine line : order.lines()) {
                    ps.setLong(1, orderId);
                    ps.setInt(2, line.lineNo());
                    ps.setString(3, line.product());
                    ps.setInt(4, line.quantity());
                    ps.setBigDecimal(5, line.unitPrice());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            return orderId;
        });
    }

    @Override
    public StockItemView decrementStock(long itemId) {
        return inTransaction(false, c -> {
            StockItem item = stockItem(c, itemId);
            return saveStockItem(c, item.id(), item.sku(), item.quantity() - 1, item.version());
        });
    }

    @Override
    public void decrementStockWithStaleVersion(long itemId, int staleVersion) {
        inTransaction(false, c -> {
            StockItem item = stockItem(c, itemId);
            return saveStockItem(c, item.id(), item.sku(), item.quantity() - 1, staleVersion);
        });
    }

    private static StockItem stockItem(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id, sku, quantity, version FROM stock_item WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new StockItem(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getInt(4));
            }
        }
    }

    private static StockItem saveStockItem(Connection c, long id, String sku, int quantity, int expectedVersion) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE stock_item SET quantity = ?, version = ? WHERE id = ? AND version = ?")) {
            ps.setInt(1, quantity);
            ps.setInt(2, expectedVersion + 1);
            ps.setLong(3, id);
            ps.setInt(4, expectedVersion);
            if (ps.executeUpdate() != 1) {
                throw new OptimisticConflictException(null);
            }
        }
        return new StockItem(id, sku, quantity, expectedVersion + 1);
    }

    // --- transactions ---

    @Override
    public List<? extends BookView> txReadOnly(long id1, long id2) {
        return inTransaction(true, c -> List.of(bookById(c, id1), bookById(c, id2)));
    }

    @Override
    public Long txReadWrite(long readId, NewBook book) {
        return inTransaction(false, c -> {
            bookById(c, readId);
            return insertBook(c, book);
        });
    }

    @Override
    public Long txNestedRequired(long readId, NewBook book) {
        return inTransaction(false, c -> {
            bookById(c, readId);
            // nested REQUIRED joins the outer transaction: same connection
            return insertBook(c, book);
        });
    }

    @Override
    public Long txRequiresNew(long readId, NewBook book) {
        return inTransaction(false, c -> {
            bookById(c, readId);
            // REQUIRES_NEW: a second connection and transaction while the outer one stays open
            return inTransaction(false, inner -> insertBook(inner, book));
        });
    }

    @Override
    public void txRollback(NewBook book) {
        inTransaction(false, c -> {
            insertBook(c, book);
            throw new IntentionalRollbackException();
        });
    }

    @Override
    public void txRequiresNewThenRollback(NewBook outer, NewBook inner) {
        inTransaction(false, c -> {
            insertBook(c, outer);
            inTransaction(false, innerConnection -> insertBook(innerConnection, inner));
            throw new IntentionalRollbackException();
        });
    }

    @Override
    public void txNestedRequiredThenRollback(NewBook outer, NewBook inner) {
        inTransaction(false, c -> {
            insertBook(c, outer);
            insertBook(c, inner);
            throw new IntentionalRollbackException();
        });
    }
}
