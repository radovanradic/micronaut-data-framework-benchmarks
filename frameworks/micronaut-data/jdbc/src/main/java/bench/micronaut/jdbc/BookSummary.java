package bench.micronaut.jdbc;

import bench.common.model.BookSummaryView;
import io.micronaut.core.annotation.Introspected;

import java.math.BigDecimal;

/**
 * DTO projection: Micronaut Data selects only the record's columns.
 */
@Introspected
public record BookSummary(Long id, String title, BigDecimal price) implements BookSummaryView {

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public BigDecimal getPrice() {
        return price;
    }
}
