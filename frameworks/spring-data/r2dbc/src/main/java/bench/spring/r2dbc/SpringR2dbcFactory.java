package bench.spring.r2dbc;

import bench.common.dataset.DbSettings;
import bench.common.spi.BenchmarkTarget;
import bench.common.spi.Category;
import bench.common.spi.ImplementationInfo;
import bench.common.spi.ReactiveScenarios;
import bench.common.spi.TargetFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Starts a Spring Boot application context with Spring Data R2DBC over r2dbc-pool. Configuration
 * files: {@code application.properties} (default) and {@code application-tuned.properties} (tuned,
 * loaded through the {@code tuned} profile).
 */
public final class SpringR2dbcFactory implements TargetFactory {

    @Override
    public ImplementationInfo info() {
        return new ImplementationInfo(
            "spring-data-r2dbc",
            "Spring Data R2DBC",
            Category.REACTIVE,
            true,
            List.of(ImplementationInfo.DEFAULT, ImplementationInfo.TUNED),
            Set.of(),
            List.of("org.springframework.data:spring-data-r2dbc", "org.springframework.boot:spring-boot",
                "org.springframework:spring-core", "org.postgresql:r2dbc-postgresql", "io.r2dbc:r2dbc-pool"));
    }

    @Override
    public BenchmarkTarget start(String configuration, DbSettings db) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.r2dbc.url", db.r2dbcUrl());
        properties.put("spring.r2dbc.username", db.user());
        properties.put("spring.r2dbc.password", db.password());
        // r2dbc-pool with a fixed size, the R2DBC equivalent of the JDBC pool settings
        properties.put("spring.r2dbc.pool.initial-size", db.poolSize());
        properties.put("spring.r2dbc.pool.max-size", db.poolSize());
        ConfigurableApplicationContext context = new SpringApplicationBuilder(SpringR2dbcApplication.class)
            .web(WebApplicationType.NONE)
            .bannerMode(Banner.Mode.OFF)
            .logStartupInfo(false)
            .profiles(ImplementationInfo.TUNED.equals(configuration) ? new String[]{ImplementationInfo.TUNED} : new String[0])
            .properties(properties)
            .run();
        ReactiveScenarios scenarios = context.getBean(SpringR2dbcScenarios.class);
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
