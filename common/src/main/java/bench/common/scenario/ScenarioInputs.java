package bench.common.scenario;

import bench.common.dataset.Seed;
import bench.common.model.Inputs.BookFilter;
import bench.common.model.Inputs.SortOrder;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.LongStream;

/**
 * Fixed scenario inputs (SPEC.md section 4).
 */
public final class ScenarioInputs {

    /** Author whose books {@code findById}/{@code findByIsbn} rotate over. */
    public static final long READ_AUTHOR_ID = 3;

    public static final long LIST_10_AUTHOR_ID = 1;
    public static final long LIST_100_AUTHOR_ID = 2;
    public static final long LIST_10000_AUTHOR_ID = 3;

    public static final long PROJECTION_AUTHOR_ID = 2;

    public static final long PAGE_AUTHOR_ID = 3;
    public static final int PAGE_INDEX = 2;
    public static final int PAGE_SIZE = 20;

    public static final long SORT_AUTHOR_ID = 2;
    public static final List<SortOrder> SORT = List.of(new SortOrder("price", false), new SortOrder("title", true));

    public static final long KEYSET_AUTHOR_ID = 3;
    /** The 1,000th book of author 3. */
    public static final long KEYSET_AFTER_ID = 1_110;
    public static final int KEYSET_SIZE = 20;

    /** 1,000 ids: 111 + 10k for k = 0..999. */
    public static final List<Long> IN_LIST_IDS = LongStream.range(0, 1_000).map(k -> 111 + 10 * k).boxed().toList();

    public static final BookFilter CRITERIA_FILTER = new BookFilter(3L, 500, 520, new BigDecimal("20.00"));

    public static final String JOIN_FETCH_CUSTOMER = Seed.customer(7);
    public static final int JOIN_FETCH_CUSTOMER_INDEX = 7;

    public static final int NEW_ORDER_LINES = 5;

    private ScenarioInputs() {
    }
}
