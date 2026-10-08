package bench.spring.jpa;

import bench.common.dataset.DbSettings;
import bench.common.spi.BenchmarkTarget;
import bench.common.spi.BlockingScenarios;
import bench.common.spi.Category;
import bench.common.spi.ImplementationInfo;
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
 * Starts a Spring Boot application context with Spring Data JPA (Hibernate). Configuration files:
 * {@code application.properties} (default) and {@code application-tuned.properties} (tuned, loaded
 * through the {@code tuned} profile).
 */
public final class SpringJpaFactory implements TargetFactory {

    @Override
    public ImplementationInfo info() {
        return new ImplementationInfo(
            "spring-data-jpa",
            "Spring Data JPA (Hibernate)",
            Category.ORM_JPA,
            false,
            List.of(ImplementationInfo.DEFAULT, ImplementationInfo.TUNED),
            Set.of(),
            List.of("org.springframework.data:spring-data-jpa", "org.springframework.boot:spring-boot",
                "org.springframework:spring-core", "org.hibernate.orm:hibernate-core", "com.zaxxer:HikariCP"));
    }

    @Override
    public BenchmarkTarget start(String configuration, DbSettings db) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.datasource.url", db.jdbcUrl());
        properties.put("spring.datasource.username", db.user());
        properties.put("spring.datasource.password", db.password());
        properties.put("spring.datasource.hikari.maximum-pool-size", db.poolSize());
        properties.put("spring.datasource.hikari.minimum-idle", db.poolSize());
        ConfigurableApplicationContext context = new SpringApplicationBuilder(SpringJpaApplication.class)
            .web(WebApplicationType.NONE)
            .bannerMode(Banner.Mode.OFF)
            .logStartupInfo(false)
            .profiles(ImplementationInfo.TUNED.equals(configuration) ? new String[]{ImplementationInfo.TUNED} : new String[0])
            .properties(properties)
            .run();
        BlockingScenarios scenarios = context.getBean(SpringJpaScenarios.class);
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
