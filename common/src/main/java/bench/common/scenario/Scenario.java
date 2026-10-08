package bench.common.scenario;

import java.util.Arrays;
import java.util.List;

/**
 * Every benchmarked scenario (SPEC.md section 4). The id is also the JMH benchmark method name.
 */
public enum Scenario {

    FIND_BY_ID("findById", Group.READS),
    FIND_BY_ISBN("findByIsbn", Group.READS),
    LIST_10("list10", Group.READS),
    LIST_100("list100", Group.READS),
    LIST_10000("list10000", Group.READS),
    PROJECTION("projection", Group.READS),
    PAGE_WITH_COUNT("pageWithCount", Group.READS),
    SORT("sort", Group.READS),
    KEYSET_PAGE("keysetPage", Group.READS),
    IN_LIST_1000("inList1000", Group.READS),
    CRITERIA("criteria", Group.READS),
    JOIN_FETCH("joinFetch", Group.READS),

    SAVE("save", Group.WRITES),
    SAVE_ALL_100("saveAll100", Group.WRITES),
    SAVE_ALL_1000("saveAll1000", Group.WRITES),
    UPDATE("update", Group.WRITES),
    DELETE("delete", Group.WRITES),
    CASCADE_INSERT("cascadeInsert", Group.WRITES),
    OPTIMISTIC_UPDATE("optimisticUpdate", Group.WRITES),

    TX_READ_ONLY("txReadOnly", Group.TX),
    TX_READ_WRITE("txReadWrite", Group.TX),
    TX_NESTED_REQUIRED("txNestedRequired", Group.TX),
    TX_REQUIRES_NEW("txRequiresNew", Group.TX),
    TX_ROLLBACK("txRollback", Group.TX),

    REACTIVE_FIND_BY_ID("reactiveFindById", Group.REACTIVE),
    REACTIVE_STREAM_10("reactiveStream10", Group.REACTIVE),
    REACTIVE_STREAM_100("reactiveStream100", Group.REACTIVE),
    REACTIVE_STREAM_10000("reactiveStream10000", Group.REACTIVE),
    REACTIVE_SAVE_ALL_100("reactiveSaveAll100", Group.REACTIVE),
    REACTIVE_SAVE_ALL_1000("reactiveSaveAll1000", Group.REACTIVE),
    REACTIVE_TX_UPDATE("reactiveTxUpdate", Group.REACTIVE),
    REACTIVE_TX_ROLLBACK("reactiveTxRollback", Group.REACTIVE);

    /**
     * Scenario groups, selectable in the runner.
     */
    public enum Group {
        READS, WRITES, TX, REACTIVE;

        public boolean isReactive() {
            return this == REACTIVE;
        }
    }

    private final String id;
    private final Group group;

    Scenario(String id, Group group) {
        this.id = id;
        this.group = group;
    }

    public String id() {
        return id;
    }

    public Group group() {
        return group;
    }

    public boolean isReactive() {
        return group.isReactive();
    }

    public static Scenario byId(String id) {
        return Arrays.stream(values())
            .filter(s -> s.id.equals(id))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown scenario: " + id));
    }

    public static List<Scenario> blocking() {
        return Arrays.stream(values()).filter(s -> !s.isReactive()).toList();
    }

    public static List<Scenario> reactive() {
        return Arrays.stream(values()).filter(Scenario::isReactive).toList();
    }
}
