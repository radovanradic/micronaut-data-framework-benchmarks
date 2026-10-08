package bench.common.scenario;

import bench.common.spi.BlockingScenarios;
import bench.common.spi.IntentionalRollbackException;

import static bench.common.scenario.ScenarioInputs.*;

/**
 * One operation per blocking scenario: picks the inputs from the {@link Workload} and calls the
 * implementation. The JMH benchmarks, the statement counter and the correctness check all run these,
 * so every tool measures exactly the same work.
 */
public final class BlockingOps {

    private BlockingOps() {
    }

    // --- reads ---

    public static Object findById(BlockingScenarios s, Workload w) {
        return s.findBookById(w.nextReadBookId());
    }

    public static Object findByIsbn(BlockingScenarios s, Workload w) {
        return s.findBookByIsbn(w.nextReadIsbn());
    }

    public static Object list10(BlockingScenarios s) {
        return s.listBooksByAuthor(LIST_10_AUTHOR_ID);
    }

    public static Object list100(BlockingScenarios s) {
        return s.listBooksByAuthor(LIST_100_AUTHOR_ID);
    }

    public static Object list10000(BlockingScenarios s) {
        return s.listBooksByAuthor(LIST_10000_AUTHOR_ID);
    }

    public static Object projection(BlockingScenarios s) {
        return s.bookSummariesByAuthor(PROJECTION_AUTHOR_ID);
    }

    public static Object pageWithCount(BlockingScenarios s) {
        return s.pageBooksByAuthor(PAGE_AUTHOR_ID, PAGE_INDEX, PAGE_SIZE);
    }

    public static Object sort(BlockingScenarios s) {
        return s.sortedBooksByAuthor(SORT_AUTHOR_ID, SORT);
    }

    public static Object keysetPage(BlockingScenarios s) {
        return s.keysetPageBooksByAuthor(KEYSET_AUTHOR_ID, KEYSET_AFTER_ID, KEYSET_SIZE);
    }

    public static Object inList1000(BlockingScenarios s) {
        return s.findBooksByIds(IN_LIST_IDS);
    }

    public static Object criteria(BlockingScenarios s) {
        return s.findBooksByFilter(CRITERIA_FILTER);
    }

    public static Object joinFetch(BlockingScenarios s) {
        return s.findOrdersWithLines(JOIN_FETCH_CUSTOMER);
    }

    // --- writes ---

    public static Object save(BlockingScenarios s, Workload w) {
        return s.saveBook(w.newBook());
    }

    public static Object saveAll100(BlockingScenarios s, Workload w) {
        return s.saveBooks(w.newBooks(100));
    }

    public static Object saveAll1000(BlockingScenarios s, Workload w) {
        return s.saveBooks(w.newBooks(1_000));
    }

    public static Object update(BlockingScenarios s, Workload w) {
        return s.updateBookPrice(w.nextMutableBookId(), w.nextPrice());
    }

    /** {@code id} is a row inserted by the harness beforehand. */
    public static void delete(BlockingScenarios s, long id) {
        s.deleteBook(id);
    }

    public static Object cascadeInsert(BlockingScenarios s, Workload w) {
        return s.saveOrder(w.newOrder());
    }

    public static Object optimisticUpdate(BlockingScenarios s, Workload w) {
        return s.decrementStock(w.nextStockItemId());
    }

    // --- transactions ---

    public static Object txReadOnly(BlockingScenarios s, Workload w) {
        return s.txReadOnly(w.nextReadBookId(), w.nextReadBookId());
    }

    public static Object txReadWrite(BlockingScenarios s, Workload w) {
        return s.txReadWrite(w.nextReadBookId(), w.newBook());
    }

    public static Object txNestedRequired(BlockingScenarios s, Workload w) {
        return s.txNestedRequired(w.nextReadBookId(), w.newBook());
    }

    public static Object txRequiresNew(BlockingScenarios s, Workload w) {
        return s.txRequiresNew(w.nextReadBookId(), w.newBook());
    }

    public static Object txRollback(BlockingScenarios s, Workload w) {
        try {
            s.txRollback(w.newBook());
        } catch (IntentionalRollbackException expected) {
            return expected;
        }
        throw new IllegalStateException("txRollback did not throw IntentionalRollbackException");
    }

    /**
     * Runs a scenario by id. {@code fixtureBookId} is only used by {@link Scenario#DELETE}.
     */
    public static Object run(Scenario scenario, BlockingScenarios s, Workload w, long fixtureBookId) {
        return switch (scenario) {
            case FIND_BY_ID -> findById(s, w);
            case FIND_BY_ISBN -> findByIsbn(s, w);
            case LIST_10 -> list10(s);
            case LIST_100 -> list100(s);
            case LIST_10000 -> list10000(s);
            case PROJECTION -> projection(s);
            case PAGE_WITH_COUNT -> pageWithCount(s);
            case SORT -> sort(s);
            case KEYSET_PAGE -> keysetPage(s);
            case IN_LIST_1000 -> inList1000(s);
            case CRITERIA -> criteria(s);
            case JOIN_FETCH -> joinFetch(s);
            case SAVE -> save(s, w);
            case SAVE_ALL_100 -> saveAll100(s, w);
            case SAVE_ALL_1000 -> saveAll1000(s, w);
            case UPDATE -> update(s, w);
            case DELETE -> {
                delete(s, fixtureBookId);
                yield null;
            }
            case CASCADE_INSERT -> cascadeInsert(s, w);
            case OPTIMISTIC_UPDATE -> optimisticUpdate(s, w);
            case TX_READ_ONLY -> txReadOnly(s, w);
            case TX_READ_WRITE -> txReadWrite(s, w);
            case TX_NESTED_REQUIRED -> txNestedRequired(s, w);
            case TX_REQUIRES_NEW -> txRequiresNew(s, w);
            case TX_ROLLBACK -> txRollback(s, w);
            default -> throw new IllegalArgumentException("Not a blocking scenario: " + scenario);
        };
    }

    /**
     * @return {@code true} if the scenario needs a harness-inserted row per operation
     */
    public static boolean needsFixtureRow(Scenario scenario) {
        return scenario == Scenario.DELETE;
    }
}
