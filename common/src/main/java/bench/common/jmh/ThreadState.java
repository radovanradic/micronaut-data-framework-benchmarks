package bench.common.jmh;

import bench.common.scenario.Workload;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.ThreadParams;

/**
 * Per-thread scenario inputs.
 */
@State(Scope.Thread)
public class ThreadState {

    public Workload workload;

    @Setup(Level.Trial)
    public void setUp(ThreadParams params) {
        workload = new Workload(params.getThreadIndex(), params.getThreadCount());
    }
}
