package bench.micronaut.jpa;

import bench.common.dataset.DbSettings;
import bench.common.scenario.Scenario;
import bench.common.spi.BenchmarkTarget;
import bench.common.spi.BlockingScenarios;
import bench.common.spi.Category;
import bench.common.spi.ImplementationInfo;
import bench.common.spi.TargetFactory;
import io.micronaut.context.ApplicationContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Starts a Micronaut application context with Micronaut Data JPA (Hibernate). Configuration files:
 * {@code application.properties} (default) and {@code application-tuned.properties} (tuned, loaded
 * through the {@code tuned} environment).
 */
public final class MicronautJpaFactory implements TargetFactory {

    @Override
    public ImplementationInfo info() {
        return new ImplementationInfo(
            "micronaut-data-jpa",
            "Micronaut Data JPA (Hibernate)",
            Category.ORM_JPA,
            false,
            List.of(ImplementationInfo.DEFAULT, ImplementationInfo.TUNED),
            Set.of(Scenario.KEYSET_PAGE),
            List.of("io.micronaut.data:micronaut-data-hibernate-jpa", "io.micronaut:micronaut-core",
                "org.hibernate.orm:hibernate-core", "io.micronaut.sql:micronaut-jdbc-hikari"));
    }

    @Override
    public BenchmarkTarget start(String configuration, DbSettings db) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("datasources.default.url", db.jdbcUrl());
        properties.put("datasources.default.username", db.user());
        properties.put("datasources.default.password", db.password());
        properties.put("datasources.default.maximum-pool-size", db.poolSize());
        properties.put("datasources.default.minimum-idle", db.poolSize());
        ApplicationContext context = ApplicationContext.builder()
            .deduceEnvironment(false)
            .environments(ImplementationInfo.TUNED.equals(configuration) ? new String[]{ImplementationInfo.TUNED} : new String[0])
            .properties(properties)
            .start();
        BlockingScenarios scenarios = context.getBean(MicronautJpaScenarios.class);
        return new BenchmarkTarget() {
            @Override
            public BlockingScenarios blocking() {
                return scenarios;
            }

            @Override
            public void close() {
                context.close();
            }
        };
    }
}
