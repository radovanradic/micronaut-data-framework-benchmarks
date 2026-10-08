package bench.common.spi;

/**
 * A started framework instance (application context, connection pool) ready to run scenarios.
 */
public interface BenchmarkTarget extends AutoCloseable {

    /**
     * @return the blocking scenarios, if the implementation is blocking
     */
    default BlockingScenarios blocking() {
        throw new UnsupportedOperationException("Not a blocking implementation");
    }

    /**
     * @return the reactive scenarios, if the implementation is reactive
     */
    default ReactiveScenarios reactive() {
        throw new UnsupportedOperationException("Not a reactive implementation");
    }

    /**
     * Stops the framework and closes the pool.
     */
    @Override
    void close();
}
