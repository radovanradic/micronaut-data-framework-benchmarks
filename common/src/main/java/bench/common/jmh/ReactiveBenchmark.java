package bench.common.jmh;

import bench.common.dataset.Database;
import bench.common.dataset.DbSettings;
import bench.common.scenario.ReactiveOps;
import bench.common.spi.BenchmarkTarget;
import bench.common.spi.ReactiveScenarios;
import bench.common.spi.TargetFactory;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for the reactive scenarios; see {@link BlockingBenchmark}. The benchmark thread
 * subscribes and blocks until the publisher terminates.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public abstract class ReactiveBenchmark {

    /** "default" or "tuned" (SPEC.md fairness rule 6). */
    @Param("default")
    public String config;

    private BenchmarkTarget target;
    private ReactiveScenarios s;

    @Setup(Level.Trial)
    public void startTarget() {
        DbSettings db = DbSettings.fromEnvironment();
        new Database(db).prepare();
        target = TargetFactory.load().start(config, db);
        s = target.reactive();
    }

    @TearDown(Level.Trial)
    public void stopTarget() {
        target.close();
    }

    @Benchmark
    public Object reactiveFindById(ThreadState t) {
        return ReactiveOps.findById(s, t.workload);
    }

    @Benchmark
    public long reactiveStream10() {
        return ReactiveOps.stream10(s);
    }

    @Benchmark
    public long reactiveStream100() {
        return ReactiveOps.stream100(s);
    }

    @Benchmark
    public long reactiveStream10000() {
        return ReactiveOps.stream10000(s);
    }

    @Benchmark
    public long reactiveSaveAll100(ThreadState t) {
        return ReactiveOps.saveAll100(s, t.workload);
    }

    @Benchmark
    public long reactiveSaveAll1000(ThreadState t) {
        return ReactiveOps.saveAll1000(s, t.workload);
    }

    @Benchmark
    public Object reactiveTxUpdate(ThreadState t) {
        return ReactiveOps.txUpdate(s, t.workload);
    }

    @Benchmark
    public Object reactiveTxRollback(ThreadState t) {
        return ReactiveOps.txRollback(s, t.workload);
    }
}
