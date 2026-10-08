package bench.micronaut.r2dbc;

import bench.common.dataset.DbSettings;
import bench.common.spi.BenchmarkTarget;
import bench.common.spi.Category;
import bench.common.spi.ImplementationInfo;
import bench.common.spi.ReactiveScenarios;
import bench.common.spi.TargetFactory;
import io.micronaut.context.ApplicationContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Starts a Micronaut application context with Micronaut Data R2DBC over r2dbc-pool. Configuration
 * files: {@code application.properties} (default) and {@code application-tuned.properties} (tuned,
 * loaded through the {@code tuned} environment).
 */
public final class MicronautR2dbcFactory implements TargetFactory {

    @Override
    public ImplementationInfo info() {
        return new ImplementationInfo(
            "micronaut-data-r2dbc",
            "Micronaut Data R2DBC",
            Category.REACTIVE,
            true,
            List.of(ImplementationInfo.DEFAULT, ImplementationInfo.TUNED),
            Set.of(),
            List.of("io.micronaut.data:micronaut-data-r2dbc", "io.micronaut:micronaut-core",
                "io.micronaut.r2dbc:micronaut-r2dbc-core", "org.postgresql:r2dbc-postgresql", "io.r2dbc:r2dbc-pool"));
    }

    @Override
    public BenchmarkTarget start(String configuration, DbSettings db) {
        Map<String, Object> properties = new LinkedHashMap<>();
        // r2dbc-pool with a fixed size, the R2DBC equivalent of the JDBC pool settings
        properties.put("r2dbc.datasources.default.url", "r2dbc:pool:postgresql://%s:%d/%s?initialSize=%d&maxSize=%d"
            .formatted(db.host(), db.port(), db.database(), db.poolSize(), db.poolSize()));
        properties.put("r2dbc.datasources.default.username", db.user());
        properties.put("r2dbc.datasources.default.password", db.password());
        ApplicationContext context = ApplicationContext.builder()
            .deduceEnvironment(false)
            .environments(ImplementationInfo.TUNED.equals(configuration) ? new String[]{ImplementationInfo.TUNED} : new String[0])
            .properties(properties)
            .start();
        ReactiveScenarios scenarios = context.getBean(MicronautR2dbcScenarios.class);
        return new BenchmarkTarget() {
            @Override
            public ReactiveScenarios reactive() {
                return scenarios;
            }

            @Override
            public void close() {
                context.close();
            }
        };
    }
}
