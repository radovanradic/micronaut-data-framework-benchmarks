package bench.common.model;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/**
 * Read view of a {@code purchase_order} row with its lines. Line order is unspecified.
 */
public interface OrderView {

    Long getId();

    UUID getReference();

    String getCustomer();

    Instant getCreatedAt();

    Collection<? extends OrderLineView> getLines();
}
