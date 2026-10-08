package bench.common.spi;

import bench.common.dataset.DbSettings;

import java.util.ServiceLoader;

/**
 * Entry point of an implementation, registered in
 * {@code META-INF/services/bench.common.spi.TargetFactory}. Each benchmark distribution contains
 * exactly one.
 */
public interface TargetFactory {

    /**
     * @return static description of the implementation; must not start anything
     */
    ImplementationInfo info();

    /**
     * Starts the framework against the database.
     *
     * @param configuration "default" or "tuned"
     * @param db            database connection settings
     * @return the started target
     */
    BenchmarkTarget start(String configuration, DbSettings db);

    /**
     * @return the single factory on the classpath
     */
    static TargetFactory load() {
        var factories = ServiceLoader.load(TargetFactory.class).stream().map(ServiceLoader.Provider::get).toList();
        if (factories.size() != 1) {
            throw new IllegalStateException("Expected exactly one TargetFactory on the classpath, found " + factories);
        }
        return factories.getFirst();
    }
}
