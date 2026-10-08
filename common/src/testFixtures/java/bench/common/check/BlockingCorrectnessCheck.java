package bench.common.check;

import bench.common.dataset.Database;
import bench.common.dataset.DbSettings;
import bench.common.dataset.Seed;
import bench.common.dataset.Seed.SeedBook;
import bench.common.dataset.Seed.SeedStockItem;
import bench.common.model.BookView;
import bench.common.model.Inputs.BookFilter;
import bench.common.model.Inputs.BookPage;
import bench.common.model.Inputs.NewBook;
import bench.common.model.Inputs.NewOrder;
import bench.common.model.OrderView;
import bench.common.model.StockItemView;
import bench.common.scenario.BlockingOps;
import bench.common.scenario.Scenario;
import bench.common.scenario.ScenarioInputs;
import bench.common.scenario.Workload;
import bench.common.spi.BenchmarkTarget;
import bench.common.spi.BlockingScenarios;
import bench.common.spi.ImplementationInfo;
import bench.common.spi.IntentionalRollbackException;
import bench.common.spi.OptimisticConflictException;
import bench.common.spi.TargetFactory;
import bench.common.spi.UnsupportedScenarioException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.function.Executable;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shared correctness check for blocking implementations (SPEC.md fairness rule 8). An
 * implementation runs it by declaring an empty subclass in its tests. Every check runs against every
 * configuration of the implementation, and compares results with values derived from {@link Seed}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BlockingCorrectnessCheck {

    private final Map<String, BlockingScenarios> scenarios = new LinkedHashMap<>();
    private final Map<String, Workload> workloads = new LinkedHashMap<>();
    private final List<BenchmarkTarget> targets = new ArrayList<>();
    private Database db;
    private Rows rows;
    private ImplementationInfo info;

    @BeforeAll
    void startTargets() {
        DbSettings settings = TestDatabase.settings();
        db = new Database(settings);
        db.prepare();
        rows = new Rows(db);
        TargetFactory factory = TargetFactory.load();
        info = factory.info();
        assertFalse(info.reactive(), "a reactive implementation must use ReactiveCorrectnessCheck");
        char namespace = 'a';
        for (String config : info.configurations()) {
            BenchmarkTarget target = factory.start(config, settings);
            targets.add(target);
            scenarios.put(config, target.blocking());
            workloads.put(config, new Workload(0, 1, namespace++));
        }
    }

    @AfterAll
    void stopTargets() {
        targets.forEach(BenchmarkTarget::close);
    }

    /**
     * Runs {@code check} for every configuration; for an unsupported scenario, verifies instead that the
     * implementation throws {@link UnsupportedScenarioException}.
     */
    private void check(Scenario scenario, CheckBody check) {
        List<Executable> perConfig = new ArrayList<>();
        scenarios.forEach((config, s) -> perConfig.add(() -> {
            Workload w = workloads.get(config);
            if (info.unsupported().contains(scenario)) {
                assertThrows(UnsupportedScenarioException.class, () -> BlockingOps.run(scenario, s, w, -1),
                    () -> "[" + config + "] " + scenario.id() + " is declared unsupported but did not throw UnsupportedScenarioException");
            } else {
                try {
                    check.run(s, w);
                } catch (AssertionError e) {
                    throw new AssertionError("[" + config + "] " + scenario.id() + ": " + e.getMessage(), e);
                }
            }
        }));
        assertAll(scenario.id(), perConfig);
    }

    @FunctionalInterface
    private interface CheckBody {
        void run(BlockingScenarios s, Workload w) throws Exception;
    }

    // --- implementation ---

    @Test
    void implementationInfo() {
        assertTrue(info.configurations().contains(ImplementationInfo.DEFAULT), "has a default configuration");
        assertTrue(info.configurations().contains(ImplementationInfo.TUNED), "has a tuned configuration");
        assertTrue(info.unsupported().stream().noneMatch(Scenario::isReactive), "unsupported lists only blocking scenarios");
        assertFalse(info.keyModules().isEmpty(), "names its key modules");
    }

    @Test
    void benchmarkOperationsRun() throws SQLException {
        // exactly what the JMH benchmarks execute, once per supported scenario
        try (Connection c = db.connect()) {
            char namespace = 'm';
            for (var e : scenarios.entrySet()) {
                Workload w = new Workload(0, 1, namespace++);
                for (Scenario scenario : info.supportedScenarios()) {
                    long fixture = BlockingOps.needsFixtureRow(scenario) ? Database.insertFixtureBook(c, w.newBook()) : -1;
                    try {
                        BlockingOps.run(scenario, e.getValue(), w, fixture);
                    } catch (RuntimeException ex) {
                        throw new AssertionError("[" + e.getKey() + "] " + scenario.id() + " failed", ex);
                    }
                }
            }
        }
    }

    // --- reads ---

    @Test
    void findById() {
        check(Scenario.FIND_BY_ID, (s, w) -> {
            for (long id : new long[]{1, 111, 5_000, 10_110, Seed.BOOKS}) {
                Expect.book(Seed.book(id), s.findBookById(id));
            }
            assertNull(s.findBookById(999_999_999L), "unknown id returns null");
        });
    }

    @Test
    void findByIsbn() {
        check(Scenario.FIND_BY_ISBN, (s, w) -> {
            for (long id : new long[]{2, 222, 9_999}) {
                Expect.book(Seed.book(id), s.findBookByIsbn(Seed.isbn(id)));
            }
            assertNull(s.findBookByIsbn("no-such-isbn"), "unknown isbn returns null");
        });
    }

    @Test
    void list10() {
        check(Scenario.LIST_10, (s, w) -> Expect.books(Seed.booksOfAuthor(1), s.listBooksByAuthor(ScenarioInputs.LIST_10_AUTHOR_ID)));
    }

    @Test
    void list100() {
        check(Scenario.LIST_100, (s, w) -> Expect.books(Seed.booksOfAuthor(2), s.listBooksByAuthor(ScenarioInputs.LIST_100_AUTHOR_ID)));
    }

    @Test
    void list10000() {
        check(Scenario.LIST_10000, (s, w) -> Expect.books(Seed.booksOfAuthor(3), s.listBooksByAuthor(ScenarioInputs.LIST_10000_AUTHOR_ID)));
    }

    @Test
    void projection() {
        check(Scenario.PROJECTION, (s, w) ->
            Expect.summaries(Seed.booksOfAuthor(2), s.bookSummariesByAuthor(ScenarioInputs.PROJECTION_AUTHOR_ID)));
    }

    @Test
    void pageWithCount() {
        check(Scenario.PAGE_WITH_COUNT, (s, w) -> {
            BookPage page = s.pageBooksByAuthor(ScenarioInputs.PAGE_AUTHOR_ID, ScenarioInputs.PAGE_INDEX, ScenarioInputs.PAGE_SIZE);
            assertNotNull(page, "page");
            Expect.books(Seed.booksOfAuthor(3).subList(40, 60), page.content());
            assertEquals(10_000, page.totalSize(), "total size");
        });
    }

    @Test
    void sort() {
        check(Scenario.SORT, (s, w) -> {
            List<SeedBook> expected = Seed.booksOfAuthor(2).stream()
                .sorted(Comparator.comparing(SeedBook::price).reversed().thenComparing(SeedBook::title))
                .toList();
            Expect.books(expected, s.sortedBooksByAuthor(ScenarioInputs.SORT_AUTHOR_ID, ScenarioInputs.SORT));
        });
    }

    @Test
    void keysetPage() {
        check(Scenario.KEYSET_PAGE, (s, w) -> Expect.books(
            Seed.booksOfAuthor(3).subList(1_000, 1_020),
            s.keysetPageBooksByAuthor(ScenarioInputs.KEYSET_AUTHOR_ID, ScenarioInputs.KEYSET_AFTER_ID, ScenarioInputs.KEYSET_SIZE)));
    }

    @Test
    void inList1000() {
        check(Scenario.IN_LIST_1000, (s, w) -> Expect.booksAnyOrder(
            ScenarioInputs.IN_LIST_IDS.stream().map(Seed::book).toList(),
            s.findBooksByIds(ScenarioInputs.IN_LIST_IDS)));
    }

    @Test
    void criteria() {
        check(Scenario.CRITERIA, (s, w) -> {
            BookFilter f = ScenarioInputs.CRITERIA_FILTER;
            List<SeedBook> expected = filter(f);
            assertFalse(expected.isEmpty(), "the benchmark filter matches rows");
            Expect.books(expected, s.findBooksByFilter(f));
            // the query must be dynamic: null filter fields add no predicate
            BookFilter authorOnly = new BookFilter(1L, null, null, null);
            Expect.books(filter(authorOnly), s.findBooksByFilter(authorOnly));
            BookFilter priceOnly = new BookFilter(2L, null, null, new BigDecimal("15.00"));
            Expect.books(filter(priceOnly), s.findBooksByFilter(priceOnly));
        });
    }

    private static List<SeedBook> filter(BookFilter f) {
        Predicate<SeedBook> p = b -> b.authorId() == f.authorId();
        if (f.minPages() != null) {
            p = p.and(b -> b.pages() >= f.minPages());
        }
        if (f.maxPages() != null) {
            p = p.and(b -> b.pages() <= f.maxPages());
        }
        if (f.maxPrice() != null) {
            p = p.and(b -> b.price().compareTo(f.maxPrice()) < 0);
        }
        return Seed.booksOfAuthor(f.authorId()).stream().filter(p).toList();
    }

    @Test
    void joinFetch() {
        check(Scenario.JOIN_FETCH, (s, w) -> {
            List<Seed.SeedOrder> expected = Seed.ordersOfCustomer(ScenarioInputs.JOIN_FETCH_CUSTOMER_INDEX);
            List<? extends OrderView> actual = s.findOrdersWithLines(ScenarioInputs.JOIN_FETCH_CUSTOMER);
            assertNotNull(actual, "orders");
            assertEquals(expected.stream().map(OrderView::getId).toList(), actual.stream().map(OrderView::getId).toList(), "order ids in order");
            for (int i = 0; i < expected.size(); i++) {
                // lines are read after the scenario returned: they must be fully loaded
                Expect.order(expected.get(i), actual.get(i));
            }
        });
    }

    // --- writes ---

    @Test
    void save() {
        check(Scenario.SAVE, (s, w) -> {
            NewBook book = w.newBook();
            Long id = s.saveBook(book);
            assertNotNull(id, "generated id");
            assertTrue(id > Seed.BOOKS, "id generated by the database identity");
            Expect.newBook(book, rows.book(id).orElse(null), id);
        });
    }

    @Test
    void saveAll100() {
        check(Scenario.SAVE_ALL_100, (s, w) -> checkSaveAll(s, w.newBooks(100)));
    }

    @Test
    void saveAll1000() {
        check(Scenario.SAVE_ALL_1000, (s, w) -> checkSaveAll(s, w.newBooks(1_000)));
    }

    private void checkSaveAll(BlockingScenarios s, List<NewBook> books) {
        List<Long> ids = s.saveBooks(books);
        assertNotNull(ids, "ids");
        assertEquals(books.size(), ids.size(), "one id per book");
        assertEquals(books.size(), new HashSet<>(ids).size(), "ids are distinct");
        for (int i = 0; i < books.size(); i += 37) {
            long id = ids.get(i);
            assertTrue(id > Seed.BOOKS, "generated id");
            Expect.newBook(books.get(i), rows.book(id).orElse(null), id);
        }
        long last = ids.getLast();
        Expect.newBook(books.getLast(), rows.book(last).orElse(null), last);
    }

    @Test
    void update() {
        check(Scenario.UPDATE, (s, w) -> {
            long id = Seed.FIRST_MUTABLE_BOOK_ID + 7;
            BigDecimal price = new BigDecimal("12.34");
            BookView updated = s.updateBookPrice(id, price);
            SeedBook seed = Seed.book(id);
            SeedBook expected = new SeedBook(id, seed.authorId(), seed.title(), seed.isbn(), seed.pages(), price, seed.publishedOn());
            Expect.book(expected, updated);
            Expect.book(expected, rows.book(id).orElse(null));
        });
    }

    @Test
    void delete() {
        check(Scenario.DELETE, (s, w) -> {
            long id;
            try (Connection c = db.connect()) {
                id = Database.insertFixtureBook(c, w.newBook());
            }
            assertTrue(rows.book(id).isPresent(), "fixture row exists");
            s.deleteBook(id);
            assertTrue(rows.book(id).isEmpty(), "row deleted");
        });
    }

    @Test
    void cascadeInsert() {
        check(Scenario.CASCADE_INSERT, (s, w) -> {
            NewOrder order = w.newOrder();
            Long id = s.saveOrder(order);
            assertNotNull(id, "generated order id");
            assertTrue(id > Seed.ORDERS, "id generated by the database identity");
            OrderView stored = rows.order(id).orElse(null);
            assertNotNull(stored, "order stored");
            assertEquals(order.reference(), stored.getReference(), "reference");
            assertEquals(order.customer(), stored.getCustomer(), "customer");
            assertEquals(order.createdAt(), stored.getCreatedAt(), "createdAt");
            assertEquals(order.lines().size(), stored.getLines().size(), "lines cascaded");
            var storedLines = List.copyOf(stored.getLines());
            for (int i = 0; i < order.lines().size(); i++) {
                var expected = order.lines().get(i);
                var actual = storedLines.get(i);
                assertEquals(expected.lineNo(), actual.getLineNo(), "lineNo");
                assertEquals(expected.product(), actual.getProduct(), "product");
                assertEquals(expected.quantity(), actual.getQuantity(), "quantity");
                Expect.decimal(expected.unitPrice(), actual.getUnitPrice(), "unitPrice");
            }
        });
    }

    @Test
    void optimisticUpdate() {
        check(Scenario.OPTIMISTIC_UPDATE, (s, w) -> {
            long id = 500;
            SeedStockItem before = rows.stockItem(id);
            StockItemView after = s.decrementStock(id);
            assertNotNull(after, "updated item");
            assertEquals(before.quantity() - 1, after.getQuantity(), "quantity decremented");
            assertEquals(before.version() + 1, after.getVersion(), "version incremented");
            SeedStockItem stored = rows.stockItem(id);
            assertEquals(before.quantity() - 1, stored.quantity(), "stored quantity");
            assertEquals(before.version() + 1, stored.version(), "stored version");
            // a save with the stale version must be rejected and change nothing
            assertThrows(OptimisticConflictException.class, () -> s.decrementStockWithStaleVersion(id, before.version()),
                "stale version rejected");
            assertEquals(stored, rows.stockItem(id), "row unchanged after the conflict");
        });
    }

    // --- transactions ---

    @Test
    void txReadOnly() {
        check(Scenario.TX_READ_ONLY, (s, w) -> Expect.books(List.of(Seed.book(200), Seed.book(300)), s.txReadOnly(200, 300)));
    }

    @Test
    void txReadWrite() {
        check(Scenario.TX_READ_WRITE, (s, w) -> {
            NewBook book = w.newBook();
            Long id = s.txReadWrite(200, book);
            Expect.newBook(book, rows.book(id).orElse(null), id);
        });
    }

    @Test
    void txNestedRequired() {
        check(Scenario.TX_NESTED_REQUIRED, (s, w) -> {
            NewBook book = w.newBook();
            Long id = s.txNestedRequired(200, book);
            Expect.newBook(book, rows.book(id).orElse(null), id);
            // semantics: inner REQUIRED work is rolled back with the outer transaction
            NewBook outer = w.newBook();
            NewBook inner = w.newBook();
            assertThrows(IntentionalRollbackException.class, () -> s.txNestedRequiredThenRollback(outer, inner));
            assertTrue(rows.bookByIsbn(outer.isbn()).isEmpty(), "outer work rolled back");
            assertTrue(rows.bookByIsbn(inner.isbn()).isEmpty(), "inner REQUIRED work rolled back with the outer transaction");
        });
    }

    @Test
    void txRequiresNew() {
        check(Scenario.TX_REQUIRES_NEW, (s, w) -> {
            NewBook book = w.newBook();
            Long id = s.txRequiresNew(200, book);
            Expect.newBook(book, rows.book(id).orElse(null), id);
            // semantics: REQUIRES_NEW work survives the outer rollback
            NewBook outer = w.newBook();
            NewBook inner = w.newBook();
            assertThrows(IntentionalRollbackException.class, () -> s.txRequiresNewThenRollback(outer, inner));
            assertTrue(rows.bookByIsbn(outer.isbn()).isEmpty(), "outer work rolled back");
            assertTrue(rows.bookByIsbn(inner.isbn()).isPresent(), "REQUIRES_NEW work committed independently");
        });
    }

    @Test
    void txRollback() {
        check(Scenario.TX_ROLLBACK, (s, w) -> {
            NewBook book = w.newBook();
            assertThrows(IntentionalRollbackException.class, () -> s.txRollback(book));
            assertTrue(rows.bookByIsbn(book.isbn()).isEmpty(), "insert rolled back");
        });
    }
}
