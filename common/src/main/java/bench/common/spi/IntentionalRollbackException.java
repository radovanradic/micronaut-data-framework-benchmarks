package bench.common.spi;

/**
 * Thrown by the rollback scenarios to make the transaction roll back. It is unchecked, so declarative
 * transaction managers roll back on it by default.
 */
public class IntentionalRollbackException extends RuntimeException {

    public IntentionalRollbackException() {
        super("intentional rollback", null, false, false);
    }
}
