package bench.common.model;

/**
 * Read view of a {@code stock_item} row, the optimistically locked entity. The version is boxed
 * because some frameworks (Spring Data) treat a primitive version of 0 as "new entity".
 */
public interface StockItemView {

    Long getId();

    String getSku();

    int getQuantity();

    Integer getVersion();
}
