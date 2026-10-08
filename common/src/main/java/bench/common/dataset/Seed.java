package bench.common.dataset;

import bench.common.model.BookView;
import bench.common.model.OrderLineView;
import bench.common.model.OrderView;
import bench.common.model.StockItemView;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;

/**
 * The deterministic seed dataset (SPEC.md section 3). Pure functions: the same id always yields the
 * same row. Used both to load the database and to compute expected results in the correctness check.
 */
public final class Seed {

    /** Bump when the dataset or schema changes; a database seeded with another version is re-created. */
    public static final int VERSION = 1;

    public static final int AUTHORS = 100;
    public static final int BOOKS = 12_070;
    public static final int ORDERS = 1_000;
    public static final int LINES_PER_ORDER = 5;
    public static final int STOCK_ITEMS = 1_000;
    public static final int ORDERS_PER_CUSTOMER = 10;
    public static final int STOCK_QUANTITY = 1_000_000;

    /** Author of all books written by the write scenarios and owner of the mutable seed books. */
    public static final long WRITE_AUTHOR_ID = 100;
    public static final long FIRST_MUTABLE_BOOK_ID = 11_071;
    public static final int MUTABLE_BOOKS = 1_000;

    /** Customer of all orders written by the write scenarios. */
    public static final String WRITE_CUSTOMER = "bench-writer";

    private static final String[] COUNTRIES = {"US", "GB", "DE", "FR", "RS"};
    private static final LocalDate FIRST_PUBLISHED = LocalDate.of(2000, 1, 1);
    private static final Instant FIRST_ORDER = Instant.parse("2024-01-01T00:00:00Z");

    private Seed() {
    }

    // --- authors ---

    public static String authorName(long id) {
        return "Author %03d".formatted(id);
    }

    public static String authorCountry(long id) {
        return COUNTRIES[(int) ((id - 1) % COUNTRIES.length)];
    }

    // --- books ---

    public static long firstBookIdOfAuthor(long authorId) {
        if (authorId == 1) {
            return 1;
        } else if (authorId == 2) {
            return 11;
        } else if (authorId == 3) {
            return 111;
        } else if (authorId <= 99) {
            return 10_111 + (authorId - 4) * 10;
        } else if (authorId == WRITE_AUTHOR_ID) {
            return FIRST_MUTABLE_BOOK_ID;
        }
        throw new IllegalArgumentException("No such author: " + authorId);
    }

    public static int bookCountOfAuthor(long authorId) {
        if (authorId == 2) {
            return 100;
        } else if (authorId == 3) {
            return 10_000;
        } else if (authorId == WRITE_AUTHOR_ID) {
            return MUTABLE_BOOKS;
        } else if (authorId >= 1 && authorId <= 99) {
            return 10;
        }
        throw new IllegalArgumentException("No such author: " + authorId);
    }

    public static long authorOfBook(long id) {
        if (id < 1 || id > BOOKS) {
            throw new IllegalArgumentException("No such seed book: " + id);
        } else if (id <= 10) {
            return 1;
        } else if (id <= 110) {
            return 2;
        } else if (id <= 10_110) {
            return 3;
        } else if (id < FIRST_MUTABLE_BOOK_ID) {
            return 4 + (id - 10_111) / 10;
        }
        return WRITE_AUTHOR_ID;
    }

    public static String isbn(long bookId) {
        return "ISBN-%08d".formatted(bookId);
    }

    public static SeedBook book(long id) {
        return new SeedBook(
            id,
            authorOfBook(id),
            "Book %05d".formatted(id),
            isbn(id),
            50 + (int) ((id * 37) % 950),
            BigDecimal.valueOf(500 + (id * 53) % 4500, 2),
            FIRST_PUBLISHED.plusDays((id * 17) % 9000));
    }

    public static List<SeedBook> booksOfAuthor(long authorId) {
        long first = firstBookIdOfAuthor(authorId);
        return LongStream.range(first, first + bookCountOfAuthor(authorId)).mapToObj(Seed::book).toList();
    }

    // --- orders ---

    public static String customer(int index) {
        return "customer-%03d".formatted(index);
    }

    public static String customerOfOrder(long orderId) {
        return customer((int) ((orderId - 1) / ORDERS_PER_CUSTOMER));
    }

    public static UUID orderReference(long orderId) {
        return UUID.nameUUIDFromBytes(("order-" + orderId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static SeedOrder order(long id) {
        if (id < 1 || id > ORDERS) {
            throw new IllegalArgumentException("No such seed order: " + id);
        }
        List<SeedOrderLine> lines = new ArrayList<>(LINES_PER_ORDER);
        for (int lineNo = 0; lineNo < LINES_PER_ORDER; lineNo++) {
            lines.add(new SeedOrderLine(
                (id - 1) * LINES_PER_ORDER + lineNo + 1,
                lineNo,
                "product-%03d".formatted((id * 7 + lineNo) % 200),
                1 + (int) ((id + lineNo) % 9),
                BigDecimal.valueOf(100 + (id * 31 + lineNo * 17) % 1000, 2)));
        }
        return new SeedOrder(id, orderReference(id), customerOfOrder(id), FIRST_ORDER.plus(id, ChronoUnit.HOURS), List.copyOf(lines));
    }

    public static List<SeedOrder> ordersOfCustomer(int customerIndex) {
        long first = (long) customerIndex * ORDERS_PER_CUSTOMER + 1;
        return LongStream.range(first, first + ORDERS_PER_CUSTOMER).mapToObj(Seed::order).toList();
    }

    // --- stock ---

    public static SeedStockItem stockItem(long id) {
        if (id < 1 || id > STOCK_ITEMS) {
            throw new IllegalArgumentException("No such stock item: " + id);
        }
        return new SeedStockItem(id, "SKU-%04d".formatted(id), STOCK_QUANTITY, 0);
    }

    // --- row types (they implement the view interfaces so expected and actual compare directly) ---

    /** A seed book. */
    public record SeedBook(long id, long authorId, String title, String isbn, int pages, BigDecimal price, LocalDate publishedOn)
        implements BookView {

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

    /** A seed order. */
    public record SeedOrder(long id, UUID reference, String customer, Instant createdAt, List<SeedOrderLine> lines)
        implements OrderView {

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
        public List<SeedOrderLine> getLines() {
            return lines;
        }
    }

    /** A seed order line. */
    public record SeedOrderLine(long id, int lineNo, String product, int quantity, BigDecimal unitPrice)
        implements OrderLineView {

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

    /** A seed stock item. */
    public record SeedStockItem(long id, String sku, int quantity, int version) implements StockItemView {

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
