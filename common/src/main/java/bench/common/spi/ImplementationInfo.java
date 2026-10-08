package bench.common.spi;

import bench.common.scenario.Scenario;

import java.util.List;
import java.util.Set;

/**
 * Static description of an implementation.
 *
 * @param id             unique id, e.g. {@code micronaut-data-jdbc}
 * @param framework      human-readable framework name, e.g. {@code Micronaut Data JDBC}
 * @param category       comparison category (SPEC.md section 5)
 * @param reactive       {@code true} for an implementation of {@link ReactiveScenarios}
 * @param configurations configuration names, always {@code default} and {@code tuned}
 * @param unsupported    scenarios the framework can't express idiomatically
 * @param keyModules     {@code group:artifact} of the modules whose versions identify the framework
 */
public record ImplementationInfo(
    String id,
    String framework,
    Category category,
    boolean reactive,
    List<String> configurations,
    Set<Scenario> unsupported,
    List<String> keyModules) {

    public static final String DEFAULT = "default";
    public static final String TUNED = "tuned";

    public ImplementationInfo {
        configurations = List.copyOf(configurations);
        unsupported = Set.copyOf(unsupported);
        keyModules = List.copyOf(keyModules);
    }

    /**
     * @return the scenarios of this implementation's style (blocking or reactive) that it supports
     */
    public List<Scenario> supportedScenarios() {
        return (reactive ? Scenario.reactive() : Scenario.blocking()).stream()
            .filter(s -> !unsupported.contains(s))
            .toList();
    }
}
