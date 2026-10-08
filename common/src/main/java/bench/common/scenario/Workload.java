package bench.common.scenario;

import bench.common.dataset.Seed;
import bench.common.model.Inputs.NewBook;
import bench.common.model.Inputs.NewOrder;
import bench.common.model.Inputs.NewOrderLine;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Deterministic per-thread scenario inputs. Not thread-safe: one instance per benchmark thread.
 *
 * <p>Read ids rotate over author 3's 10,000 books with a stride, so consecutive operations hit
 * different rows. Mutable rows (books for {@code update}, stock items for {@code optimisticUpdate})
 * are partitioned between threads, so concurrent threads never update the same row. New rows get a
 * unique isbn / order reference built from the process id, the thread index and a counter.
 */
public final class Workload {

    private static final int READ_RANGE = Seed.bookCountOfAuthor(ScenarioInputs.READ_AUTHOR_ID);
    private static final long FIRST_READ_ID = Seed.firstBookIdOfAuthor(ScenarioInputs.READ_AUTHOR_ID);
    private static final int STRIDE = 7_919;
    private static final BigDecimal NEW_BOOK_PRICE = new BigDecimal("9.99");
    private static final LocalDate NEW_BOOK_PUBLISHED = LocalDate.of(2024, 1, 1);
    private static final Instant NEW_ORDER_CREATED = Instant.parse("2024-06-01T00:00:00Z");
    private static final String PROCESS = Long.toString(ProcessHandle.current().pid(), 36);

    private final long referencePrefix;
    private final String keyPrefix;
    private final long mutableBookFirst;
    private final int mutableBookCount;
    private final long stockFirst;
    private final int stockCount;

    private long readCounter;
    private long mutableBookCounter;
    private long stockCounter;
    private long newRowCounter;
    private long priceCounter;

    /**
     * @param threadIndex index of this benchmark thread
     * @param threadCount number of benchmark threads
     * @param namespace   distinguishes independent generators in the same thread (e.g. a fixture)
     */
    public Workload(int threadIndex, int threadCount, char namespace) {
        this.referencePrefix = ProcessHandle.current().pid() << 32 | (long) namespace << 16 | threadIndex;
        this.keyPrefix = namespace + PROCESS + "-" + threadIndex + "-";
        this.mutableBookCount = Math.max(1, Seed.MUTABLE_BOOKS / threadCount);
        this.mutableBookFirst = Seed.FIRST_MUTABLE_BOOK_ID + (long) threadIndex * mutableBookCount;
        this.stockCount = Math.max(1, Seed.STOCK_ITEMS / threadCount);
        this.stockFirst = 1 + (long) threadIndex * stockCount;
        this.readCounter = (long) threadIndex * 1_000;
    }

    public Workload(int threadIndex, int threadCount) {
        this(threadIndex, threadCount, 'N');
    }

    /** Next id among author 3's books. */
    public long nextReadBookId() {
        return FIRST_READ_ID + (readCounter++ * STRIDE) % READ_RANGE;
    }

    /** Isbn of the next read book. */
    public String nextReadIsbn() {
        return Seed.isbn(nextReadBookId());
    }

    /** Next mutable book of this thread's slice. */
    public long nextMutableBookId() {
        return mutableBookFirst + mutableBookCounter++ % mutableBookCount;
    }

    /** Next stock item of this thread's slice. */
    public long nextStockItemId() {
        return stockFirst + stockCounter++ % stockCount;
    }

    /** A price between 10.00 and 19.99. */
    public BigDecimal nextPrice() {
        return BigDecimal.valueOf(1_000 + priceCounter++ % 1_000, 2);
    }

    /** A new book for author 100 with a unique isbn. */
    public NewBook newBook() {
        String key = keyPrefix + newRowCounter++;
        return new NewBook(Seed.WRITE_AUTHOR_ID, "New " + key, key, 123, NEW_BOOK_PRICE, NEW_BOOK_PUBLISHED);
    }

    public List<NewBook> newBooks(int count) {
        List<NewBook> books = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            books.add(newBook());
        }
        return books;
    }

    /** A new order of {@link Seed#WRITE_CUSTOMER} with 5 lines and a unique reference. */
    public NewOrder newOrder() {
        long n = newRowCounter++;
        UUID reference = new UUID(referencePrefix, n);
        List<NewOrderLine> lines = new ArrayList<>(ScenarioInputs.NEW_ORDER_LINES);
        for (int i = 0; i < ScenarioInputs.NEW_ORDER_LINES; i++) {
            lines.add(new NewOrderLine(i, "product-%03d".formatted(i), i + 1, BigDecimal.valueOf(250 + i, 2)));
        }
        return new NewOrder(reference, Seed.WRITE_CUSTOMER, NEW_ORDER_CREATED, lines);
    }
}
