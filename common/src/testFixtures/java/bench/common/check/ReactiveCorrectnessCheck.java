package bench.common.check;

import bench.common.dataset.Database;
import bench.common.dataset.DbSettings;
import bench.common.dataset.Seed;
import bench.common.dataset.Seed.SeedBook;
import bench.common.model.BookView;
import bench.common.model.Inputs.NewBook;
import bench.common.scenario.Publishers;
import bench.common.scenario.ReactiveOps;
import bench.common.scenario.Scenario;
import bench.common.scenario.ScenarioInputs;
import bench.common.scenario.Workload;
import bench.common.spi.BenchmarkTarget;
import bench.common.spi.ImplementationInfo;
import bench.common.spi.IntentionalRollbackException;
import bench.common.spi.ReactiveScenarios;
import bench.common.spi.TargetFactory;
import bench.common.spi.UnsupportedScenarioException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.function.Executable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shared correctness check for reactive implementations; see {@link BlockingCorrectnessCheck}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class ReactiveCorrectnessCheck {

    private final Map<String, ReactiveScenarios> scenarios = new LinkedHashMap<>();
    private final Map<String, Workload> workloads = new LinkedHashMap<>();
    private final List<BenchmarkTarget> targets = new ArrayList<>();
    private Rows rows;
    private ImplementationInfo info;

    @BeforeAll
    void startTargets() {
        DbSettings settings = TestDatabase.settings();
        Database db = new Database(settings);
        db.prepare();
        rows = new Rows(db);
        TargetFactory factory = TargetFactory.load();
        info = factory.info();
        assertTrue(info.reactive(), "a blocking implementation must use BlockingCorrectnessCheck");
        char namespace = 'r';
        for (String config : info.configurations()) {
            BenchmarkTarget target = factory.start(config, settings);
            targets.add(target);
            scenarios.put(config, target.reactive());
            workloads.put(config, new Workload(0, 1, namespace++));
        }
    }

    @AfterAll
    void stopTargets() {
        targets.forEach(BenchmarkTarget::close);
    }

    private void check(Scenario scenario, CheckBody check) {
        List<Executable> perConfig = new ArrayList<>();
        scenarios.forEach((config, s) -> perConfig.add(() -> {
            Workload w = workloads.get(config);
            if (info.unsupported().contains(scenario)) {
                assertThrows(UnsupportedScenarioException.class, () -> ReactiveOps.run(scenario, s, w),
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
        void run(ReactiveScenarios s, Workload w) throws Exception;
    }

    @Test
    void implementationInfo() {
        assertTrue(info.configurations().contains(ImplementationInfo.DEFAULT), "has a default configuration");
        assertTrue(info.configurations().contains(ImplementationInfo.TUNED), "has a tuned configuration");
        assertTrue(info.unsupported().stream().allMatch(Scenario::isReactive), "unsupported lists only reactive scenarios");
        assertFalse(info.keyModules().isEmpty(), "names its key modules");
    }

    @Test
    void benchmarkOperationsRun() {
        char namespace = 'M';
        for (var e : scenarios.entrySet()) {
            Workload w = new Workload(0, 1, namespace++);
            for (Scenario scenario : info.supportedScenarios()) {
                try {
                    ReactiveOps.run(scenario, e.getValue(), w);
                } catch (RuntimeException ex) {
                    throw new AssertionError("[" + e.getKey() + "] " + scenario.id() + " failed", ex);
                }
            }
        }
    }

    @Test
    void reactiveFindById() {
        check(Scenario.REACTIVE_FIND_BY_ID, (s, w) -> {
            for (long id : new long[]{1, 111, 5_000, Seed.BOOKS}) {
                Expect.book(Seed.book(id), Publishers.last(s.findBookById(id)));
            }
            assertNull(Publishers.last(s.findBookById(999_999_999L)), "unknown id completes empty");
        });
    }

    @Test
    void reactiveStream10() {
        check(Scenario.REACTIVE_STREAM_10, (s, w) ->
            Expect.books(Seed.booksOfAuthor(1), Publishers.list(s.streamBooksByAuthor(ScenarioInputs.LIST_10_AUTHOR_ID))));
    }

    @Test
    void reactiveStream100() {
        check(Scenario.REACTIVE_STREAM_100, (s, w) ->
            Expect.books(Seed.booksOfAuthor(2), Publishers.list(s.streamBooksByAuthor(ScenarioInputs.LIST_100_AUTHOR_ID))));
    }

    @Test
    void reactiveStream10000() {
        check(Scenario.REACTIVE_STREAM_10000, (s, w) ->
            Expect.books(Seed.booksOfAuthor(3), Publishers.list(s.streamBooksByAuthor(ScenarioInputs.LIST_10000_AUTHOR_ID))));
    }

    @Test
    void reactiveSaveAll100() {
        check(Scenario.REACTIVE_SAVE_ALL_100, (s, w) -> checkSaveAll(s, w.newBooks(100)));
    }

    @Test
    void reactiveSaveAll1000() {
        check(Scenario.REACTIVE_SAVE_ALL_1000, (s, w) -> checkSaveAll(s, w.newBooks(1_000)));
    }

    private void checkSaveAll(ReactiveScenarios s, List<NewBook> books) {
        List<Long> ids = Publishers.list(s.saveBooks(books));
        assertEquals(books.size(), ids.size(), "one id per book");
        assertEquals(books.size(), new HashSet<>(ids).size(), "ids are distinct");
        for (int i = 0; i < books.size(); i += 37) {
            long id = ids.get(i);
            assertTrue(id > Seed.BOOKS, "generated id");
            Expect.newBook(books.get(i), rows.book(id).orElse(null), id);
        }
    }

    @Test
    void reactiveTxUpdate() {
        check(Scenario.REACTIVE_TX_UPDATE, (s, w) -> {
            long id = Seed.FIRST_MUTABLE_BOOK_ID + 9;
            BigDecimal price = new BigDecimal("13.57");
            BookView updated = Publishers.last(s.txUpdateBookPrice(id, price));
            SeedBook seed = Seed.book(id);
            SeedBook expected = new SeedBook(id, seed.authorId(), seed.title(), seed.isbn(), seed.pages(), price, seed.publishedOn());
            Expect.book(expected, updated);
            Expect.book(expected, rows.book(id).orElse(null));
        });
    }

    @Test
    void reactiveTxRollback() {
        check(Scenario.REACTIVE_TX_ROLLBACK, (s, w) -> {
            NewBook book = w.newBook();
            assertInstanceOf(IntentionalRollbackException.class, Publishers.error(s.txRollback(book)), "signals the rollback");
            assertTrue(rows.bookByIsbn(book.isbn()).isEmpty(), "insert rolled back");
        });
    }
}
