package bench.common.check;

import bench.common.dataset.Database;
import bench.common.dataset.Seed.SeedBook;
import bench.common.dataset.Seed.SeedOrder;
import bench.common.dataset.Seed.SeedOrderLine;
import bench.common.dataset.Seed.SeedStockItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads rows back with plain JDBC, independently of the implementation under test.
 */
final class Rows {

    private final Database db;

    Rows(Database db) {
        this.db = db;
    }

    Optional<SeedBook> book(long id) {
        return books("WHERE id = ?", ps -> ps.setLong(1, id)).stream().findFirst();
    }

    Optional<SeedBook> bookByIsbn(String isbn) {
        return books("WHERE isbn = ?", ps -> ps.setString(1, isbn)).stream().findFirst();
    }

    Optional<SeedOrder> order(long id) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement(Database.ADMIN_MARKER
                 + "SELECT id, reference, customer, created_at FROM purchase_order WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new SeedOrder(
                    rs.getLong(1),
                    rs.getObject(2, UUID.class),
                    rs.getString(3),
                    rs.getObject(4, OffsetDateTime.class).toInstant(),
                    lines(c, id)));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<SeedOrderLine> lines(Connection c, long orderId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(Database.ADMIN_MARKER
            + "SELECT id, line_no, product, quantity, unit_price FROM order_line WHERE order_id = ? ORDER BY line_no")) {
            ps.setLong(1, orderId);
            List<SeedOrderLine> lines = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lines.add(new SeedOrderLine(rs.getLong(1), rs.getInt(2), rs.getString(3), rs.getInt(4), rs.getBigDecimal(5)));
                }
            }
            return lines;
        }
    }

    SeedStockItem stockItem(long id) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement(Database.ADMIN_MARKER
                 + "SELECT id, sku, quantity, version FROM stock_item WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new SeedStockItem(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getInt(4));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private List<SeedBook> books(String where, Binder binder) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement(Database.ADMIN_MARKER
                 + "SELECT id, author_id, title, isbn, pages, price, published_on FROM book " + where)) {
            binder.bind(ps);
            List<SeedBook> books = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    books.add(new SeedBook(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                        rs.getInt(5), rs.getBigDecimal(6), rs.getDate(7).toLocalDate()));
                }
            }
            return books;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
