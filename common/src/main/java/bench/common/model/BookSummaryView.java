package bench.common.model;

import java.math.BigDecimal;

/**
 * DTO projection of a book: only {@code id}, {@code title} and {@code price} are selected.
 */
public interface BookSummaryView {

    Long getId();

    String getTitle();

    BigDecimal getPrice();
}
