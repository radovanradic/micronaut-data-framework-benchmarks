package bench.common.jmh;

import bench.common.scenario.Scenario;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Benchmark;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The JMH method names are the scenario ids; the runner and the result files rely on it.
 */
class BenchmarkContractTest {

    @Test
    void blockingBenchmarksCoverExactlyTheBlockingScenarios() {
        assertEquals(sorted(Scenario.blocking()), benchmarkMethods(BlockingBenchmark.class));
    }

    @Test
    void reactiveBenchmarksCoverExactlyTheReactiveScenarios() {
        assertEquals(sorted(Scenario.reactive()), benchmarkMethods(ReactiveBenchmark.class));
    }

    private static List<String> sorted(List<Scenario> scenarios) {
        return scenarios.stream().map(Scenario::id).sorted().toList();
    }

    private static List<String> benchmarkMethods(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
            .filter(m -> m.isAnnotationPresent(Benchmark.class))
            .map(Method::getName)
            .sorted()
            .toList();
    }
}
