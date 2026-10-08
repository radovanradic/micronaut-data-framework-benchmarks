package bench.common.spi;

/**
 * Framework-neutral signal that a versioned update was rejected. Implementations translate their
 * framework's optimistic-locking exception into this one.
 */
public class OptimisticConflictException extends RuntimeException {

    public OptimisticConflictException(Throwable cause) {
        super("optimistic locking conflict", cause);
    }
}
