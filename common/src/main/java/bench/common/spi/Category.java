package bench.common.spi;

/**
 * Comparison categories (SPEC.md section 5). Results are compared only within a category.
 */
public enum Category {

    BASELINE_JDBC("baseline-jdbc"),
    SQL_MAPPING("sql-mapping"),
    ORM_JPA("orm-jpa"),
    REACTIVE("reactive"),
    BASELINE_R2DBC("baseline-r2dbc"),
    KOTLIN("kotlin"),
    DOCUMENT("document");

    private final String id;

    Category(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
