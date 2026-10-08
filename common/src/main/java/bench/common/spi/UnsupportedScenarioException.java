package bench.common.spi;

/**
 * Thrown by a scenario method the framework can't express idiomatically.
 */
public class UnsupportedScenarioException extends RuntimeException {

    public UnsupportedScenarioException(String reason) {
        super(reason);
    }
}
