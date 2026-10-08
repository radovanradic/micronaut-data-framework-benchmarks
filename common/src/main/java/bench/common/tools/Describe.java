package bench.common.tools;

import bench.common.scenario.Scenario;
import bench.common.spi.ImplementationInfo;
import bench.common.spi.TargetFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Prints the {@link ImplementationInfo} of the implementation on the classpath as JSON. Starts nothing.
 */
public final class Describe {

    private Describe() {
    }

    public static void main(String[] args) {
        ImplementationInfo info = TargetFactory.load().info();
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", info.id());
        json.put("framework", info.framework());
        json.put("category", info.category().id());
        json.put("style", info.reactive() ? "reactive" : "blocking");
        json.put("configurations", info.configurations());
        json.put("supported", info.supportedScenarios().stream().map(Scenario::id).toList());
        json.put("unsupported", info.unsupported().stream().map(Scenario::id).sorted().toList());
        json.put("keyModules", info.keyModules());
        System.out.println(Json.write(json));
    }
}
