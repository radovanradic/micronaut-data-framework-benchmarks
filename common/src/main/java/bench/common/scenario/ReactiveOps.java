package bench.common.scenario;

import bench.common.spi.IntentionalRollbackException;
import bench.common.spi.ReactiveScenarios;

import static bench.common.scenario.ScenarioInputs.LIST_10000_AUTHOR_ID;
import static bench.common.scenario.ScenarioInputs.LIST_100_AUTHOR_ID;
import static bench.common.scenario.ScenarioInputs.LIST_10_AUTHOR_ID;

/**
 * One operation per reactive scenario; the reactive counterpart of {@link BlockingOps}. Each operation
 * subscribes and blocks the calling (benchmark) thread until the publisher terminates.
 */
public final class ReactiveOps {

    private ReactiveOps() {
    }

    public static Object findById(ReactiveScenarios s, Workload w) {
        return Publishers.last(s.findBookById(w.nextReadBookId()));
    }

    public static long stream10(ReactiveScenarios s) {
        return Publishers.drain(s.streamBooksByAuthor(LIST_10_AUTHOR_ID));
    }

    public static long stream100(ReactiveScenarios s) {
        return Publishers.drain(s.streamBooksByAuthor(LIST_100_AUTHOR_ID));
    }

    public static long stream10000(ReactiveScenarios s) {
        return Publishers.drain(s.streamBooksByAuthor(LIST_10000_AUTHOR_ID));
    }

    public static long saveAll100(ReactiveScenarios s, Workload w) {
        return Publishers.drain(s.saveBooks(w.newBooks(100)));
    }

    public static long saveAll1000(ReactiveScenarios s, Workload w) {
        return Publishers.drain(s.saveBooks(w.newBooks(1_000)));
    }

    public static Object txUpdate(ReactiveScenarios s, Workload w) {
        return Publishers.last(s.txUpdateBookPrice(w.nextMutableBookId(), w.nextPrice()));
    }

    public static Object txRollback(ReactiveScenarios s, Workload w) {
        Throwable error = Publishers.error(s.txRollback(w.newBook()));
        if (error instanceof IntentionalRollbackException) {
            return error;
        }
        throw new IllegalStateException("reactiveTxRollback did not signal IntentionalRollbackException", error);
    }

    /**
     * Runs a scenario by id.
     */
    public static Object run(Scenario scenario, ReactiveScenarios s, Workload w) {
        return switch (scenario) {
            case REACTIVE_FIND_BY_ID -> findById(s, w);
            case REACTIVE_STREAM_10 -> stream10(s);
            case REACTIVE_STREAM_100 -> stream100(s);
            case REACTIVE_STREAM_10000 -> stream10000(s);
            case REACTIVE_SAVE_ALL_100 -> saveAll100(s, w);
            case REACTIVE_SAVE_ALL_1000 -> saveAll1000(s, w);
            case REACTIVE_TX_UPDATE -> txUpdate(s, w);
            case REACTIVE_TX_ROLLBACK -> txRollback(s, w);
            default -> throw new IllegalArgumentException("Not a reactive scenario: " + scenario);
        };
    }
}
