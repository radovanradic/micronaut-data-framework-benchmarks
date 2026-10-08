package bench.plainjdbc;

import bench.common.model.BookSummaryView;
import bench.common.model.BookView;
import bench.common.model.OrderLineView;
import bench.common.model.OrderView;
import bench.common.model.StockItemView;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Hand-mapped row objects.
 */
final class JdbcRows {

    static final String BOOK_COLUMNS = "id, author_id, title, isbn, pages, price, published_on";

    private JdbcRows() {
    }

    static Book book(ResultSet rs) throws SQLException {
        return new Book(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getInt(5),
            rs.getBigDecimal(6), rs.getObject(7, LocalDate.class));
    }

    record Book(Long id, Long authorId, String title, String isbn, int pages, BigDecimal price, LocalDate publishedOn)
        implements BookView {

        Book withPrice(BigDecimal newPrice) {
            return new Book(id, authorId, title, isbn, pages, newPrice, publishedOn);
        }

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public Long getAuthorId() {
            return authorId;
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public String getIsbn() {
            return isbn;
        }

        @Override
        public int getPages() {
            return pages;
        }

        @Override
        public BigDecimal getPrice() {
            return price;
        }

        @Override
        public LocalDate getPublishedOn() {
            return publishedOn;
        }
    }

    record BookSummary(Long id, String title, BigDecimal price) implements BookSummaryView {

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public BigDecimal getPrice() {
            return price;
        }
    }

    record Order(Long id, UUID reference, String customer, Instant createdAt, List<OrderLine> lines) implements OrderView {

        Order(Long id, UUID reference, String customer, Instant createdAt) {
            this(id, reference, customer, createdAt, new ArrayList<>());
        }

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public UUID getReference() {
            return reference;
        }

        @Override
        public String getCustomer() {
            return customer;
        }

        @Override
        public Instant getCreatedAt() {
            return createdAt;
        }

        @Override
        public List<OrderLine> getLines() {
            return lines;
        }
    }

    record OrderLine(Long id, int lineNo, String product, int quantity, BigDecimal unitPrice) implements OrderLineView {

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public int getLineNo() {
            return lineNo;
        }

        @Override
        public String getProduct() {
            return product;
        }

        @Override
        public int getQuantity() {
            return quantity;
        }

        @Override
        public BigDecimal getUnitPrice() {
            return unitPrice;
        }
    }

    record StockItem(Long id, String sku, int quantity, Integer version) implements StockItemView {

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public String getSku() {
            return sku;
        }

        @Override
        public int getQuantity() {
            return quantity;
        }

        @Override
        public Integer getVersion() {
            return version;
        }
    }
}
