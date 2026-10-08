package bench.plainjdbc;

import bench.common.dataset.DbSettings;
import bench.common.spi.BenchmarkTarget;
import bench.common.spi.BlockingScenarios;
import bench.common.spi.Category;
import bench.common.spi.ImplementationInfo;
import bench.common.spi.TargetFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.util.List;
import java.util.Set;

/**
 * Plain JDBC over HikariCP. There is nothing to tune beyond the shared pool settings, so the
 * {@code tuned} configuration is identical to {@code default}.
 */
public final class PlainJdbcFactory implements TargetFactory {

    @Override
    public ImplementationInfo info() {
        return new ImplementationInfo(
            "plain-jdbc",
            "Plain JDBC",
            Category.BASELINE_JDBC,
            false,
            List.of(ImplementationInfo.DEFAULT, ImplementationInfo.TUNED),
            Set.of(),
            List.of("org.postgresql:postgresql", "com.zaxxer:HikariCP"));
    }

    @Override
    public BenchmarkTarget start(String configuration, DbSettings db) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("plain-jdbc");
        config.setJdbcUrl(db.jdbcUrl());
        config.setUsername(db.user());
        config.setPassword(db.password());
        config.setMaximumPoolSize(db.poolSize());
        config.setMinimumIdle(db.poolSize());
        HikariDataSource dataSource = new HikariDataSource(config);
        PlainJdbcScenarios scenarios = new PlainJdbcScenarios(dataSource);
        return new BenchmarkTarget() {
            @Override
            public BlockingScenarios blocking() {
                return scenarios;
            }

            @Override
            public void close() {
                dataSource.close();
            }
        };
    }
}
