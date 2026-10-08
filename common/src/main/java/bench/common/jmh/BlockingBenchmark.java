package bench.common.jmh;

import bench.common.dataset.Database;
import bench.common.dataset.DbSettings;
import bench.common.scenario.BlockingOps;
import bench.common.spi.BenchmarkTarget;
import bench.common.spi.BlockingScenarios;
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
 * JMH benchmarks for the blocking scenarios. Each implementation declares an empty concrete subclass;
 * the implementation itself is found with {@link TargetFactory#load()}. Method names are the scenario
 * ids of SPEC.md. Iterations, forks, modes and threads are set by the runner.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public abstract class BlockingBenchmark {

    /** "default" or "tuned" (SPEC.md fairness rule 6). */
    @Param("default")
    public String config;

    private BenchmarkTarget target;
    private BlockingScenarios s;

    @Setup(Level.Trial)
    public void startTarget() {
        DbSettings db = DbSettings.fromEnvironment();
        new Database(db).prepare();
        target = TargetFactory.load().start(config, db);
        s = target.blocking();
    }

    @TearDown(Level.Trial)
    public void stopTarget() {
        target.close();
    }

    // --- reads ---

    @Benchmark
    public Object findById(ThreadState t) {
        return BlockingOps.findById(s, t.workload);
    }

    @Benchmark
    public Object findByIsbn(ThreadState t) {
        return BlockingOps.findByIsbn(s, t.workload);
    }

    @Benchmark
    public Object list10() {
        return BlockingOps.list10(s);
    }

    @Benchmark
    public Object list100() {
        return BlockingOps.list100(s);
    }

    @Benchmark
    public Object list10000() {
        return BlockingOps.list10000(s);
    }

    @Benchmark
    public Object projection() {
        return BlockingOps.projection(s);
    }

    @Benchmark
    public Object pageWithCount() {
        return BlockingOps.pageWithCount(s);
    }

    @Benchmark
    public Object sort() {
        return BlockingOps.sort(s);
    }

    @Benchmark
    public Object keysetPage() {
        return BlockingOps.keysetPage(s);
    }

    @Benchmark
    public Object inList1000() {
        return BlockingOps.inList1000(s);
    }

    @Benchmark
    public Object criteria() {
        return BlockingOps.criteria(s);
    }

    @Benchmark
    public Object joinFetch() {
        return BlockingOps.joinFetch(s);
    }

    // --- writes ---

    @Benchmark
    public Object save(ThreadState t) {
        return BlockingOps.save(s, t.workload);
    }

    @Benchmark
    public Object saveAll100(ThreadState t) {
        return BlockingOps.saveAll100(s, t.workload);
    }

    @Benchmark
    public Object saveAll1000(ThreadState t) {
        return BlockingOps.saveAll1000(s, t.workload);
    }

    @Benchmark
    public Object update(ThreadState t) {
        return BlockingOps.update(s, t.workload);
    }

    @Benchmark
    public void delete(DeleteFixture fixture) {
        BlockingOps.delete(s, fixture.bookId);
    }

    @Benchmark
    public Object cascadeInsert(ThreadState t) {
        return BlockingOps.cascadeInsert(s, t.workload);
    }

    @Benchmark
    public Object optimisticUpdate(ThreadState t) {
        return BlockingOps.optimisticUpdate(s, t.workload);
    }

    // --- transactions ---

    @Benchmark
    public Object txReadOnly(ThreadState t) {
        return BlockingOps.txReadOnly(s, t.workload);
    }

    @Benchmark
    public Object txReadWrite(ThreadState t) {
        return BlockingOps.txReadWrite(s, t.workload);
    }

    @Benchmark
    public Object txNestedRequired(ThreadState t) {
        return BlockingOps.txNestedRequired(s, t.workload);
    }

    @Benchmark
    public Object txRequiresNew(ThreadState t) {
        return BlockingOps.txRequiresNew(s, t.workload);
    }

    @Benchmark
    public Object txRollback(ThreadState t) {
        return BlockingOps.txRollback(s, t.workload);
    }
}
