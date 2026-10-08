package bench.common.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Read view of a {@code book} row. Framework entities implement this interface directly, so scenarios
 * return the framework's own objects without copying.
 */
public interface BookView {

    Long getId();

    Long getAuthorId();

    String getTitle();

    String getIsbn();

    int getPages();

    BigDecimal getPrice();

    LocalDate getPublishedOn();
}
