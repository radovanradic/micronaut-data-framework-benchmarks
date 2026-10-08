package bench.common.model;

import java.math.BigDecimal;

/**
 * Read view of an {@code order_line} row.
 */
public interface OrderLineView {

    Long getId();

    int getLineNo();

    String getProduct();

    int getQuantity();

    BigDecimal getUnitPrice();
}
