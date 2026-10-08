package bench.spring.jdbc;

import bench.common.model.BookSummaryView;

import java.math.BigDecimal;

/**
 * DTO (class-based) projection: Spring Data selects only the constructor's properties.
 */
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
