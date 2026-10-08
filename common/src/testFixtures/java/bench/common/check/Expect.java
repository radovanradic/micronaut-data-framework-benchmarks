package bench.common.check;

import bench.common.model.BookSummaryView;
import bench.common.model.BookView;
import bench.common.model.Inputs.NewBook;
import bench.common.model.OrderLineView;
import bench.common.model.OrderView;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Field-by-field comparison of views. Decimals compare by value, not scale.
 */
final class Expect {

    private Expect() {
    }

    static void book(BookView expected, BookView actual) {
        assertNotNull(actual, () -> "expected book " + expected.getId() + " but got null");
        String ctx = "book " + expected.getId();
        assertEquals(expected.getId(), actual.getId(), ctx + " id");
        assertEquals(expected.getAuthorId(), actual.getAuthorId(), ctx + " authorId");
        assertEquals(expected.getTitle(), actual.getTitle(), ctx + " title");
        assertEquals(expected.getIsbn(), actual.getIsbn(), ctx + " isbn");
        assertEquals(expected.getPages(), actual.getPages(), ctx + " pages");
        decimal(expected.getPrice(), actual.getPrice(), ctx + " price");
        assertEquals(expected.getPublishedOn(), actual.getPublishedOn(), ctx + " publishedOn");
    }

    static void books(List<? extends BookView> expected, List<? extends BookView> actual) {
        assertNotNull(actual, "result list is null");
        assertEquals(ids(expected), ids(actual), "ids (in order)");
        for (int i = 0; i < expected.size(); i++) {
            book(expected.get(i), actual.get(i));
        }
    }

    static void booksAnyOrder(List<? extends BookView> expected, List<? extends BookView> actual) {
        assertNotNull(actual, "result list is null");
        books(sortedById(expected), sortedById(actual));
    }

    static void summaries(List<? extends BookView> expected, List<? extends BookSummaryView> actual) {
        assertNotNull(actual, "result list is null");
        assertEquals(expected.size(), actual.size(), "size");
        for (int i = 0; i < expected.size(); i++) {
            BookView e = expected.get(i);
            BookSummaryView a = actual.get(i);
            assertEquals(e.getId(), a.getId(), "summary " + i + " id");
            assertEquals(e.getTitle(), a.getTitle(), "summary " + i + " title");
            decimal(e.getPrice(), a.getPrice(), "summary " + i + " price");
        }
    }

    static void newBook(NewBook expected, BookView actual, long id) {
        assertNotNull(actual, () -> "book " + id + " not found");
        assertEquals(id, actual.getId(), "id");
        assertEquals(expected.authorId(), actual.getAuthorId(), "authorId");
        assertEquals(expected.title(), actual.getTitle(), "title");
        assertEquals(expected.isbn(), actual.getIsbn(), "isbn");
        assertEquals(expected.pages(), actual.getPages(), "pages");
        decimal(expected.price(), actual.getPrice(), "price");
        assertEquals(expected.publishedOn(), actual.getPublishedOn(), "publishedOn");
    }

    static void order(OrderView expected, OrderView actual) {
        assertNotNull(actual, () -> "expected order " + expected.getId() + " but got null");
        String ctx = "order " + expected.getId();
        assertEquals(expected.getId(), actual.getId(), ctx + " id");
        assertEquals(expected.getReference(), actual.getReference(), ctx + " reference");
        assertEquals(expected.getCustomer(), actual.getCustomer(), ctx + " customer");
        assertEquals(expected.getCreatedAt(), actual.getCreatedAt(), ctx + " createdAt");
        assertNotNull(actual.getLines(), ctx + " lines");
        List<? extends OrderLineView> e = byLineNo(expected);
        List<? extends OrderLineView> a = byLineNo(actual);
        assertEquals(e.size(), a.size(), ctx + " line count");
        for (int i = 0; i < e.size(); i++) {
            String lctx = ctx + " line " + i;
            assertEquals(e.get(i).getId(), a.get(i).getId(), lctx + " id");
            assertEquals(e.get(i).getLineNo(), a.get(i).getLineNo(), lctx + " lineNo");
            assertEquals(e.get(i).getProduct(), a.get(i).getProduct(), lctx + " product");
            assertEquals(e.get(i).getQuantity(), a.get(i).getQuantity(), lctx + " quantity");
            decimal(e.get(i).getUnitPrice(), a.get(i).getUnitPrice(), lctx + " unitPrice");
        }
    }

    static void decimal(BigDecimal expected, BigDecimal actual, String ctx) {
        assertNotNull(actual, ctx + " is null");
        if (expected.compareTo(actual) != 0) {
            fail(ctx + ": expected " + expected + " but was " + actual);
        }
    }

    private static List<? extends OrderLineView> byLineNo(OrderView order) {
        return order.getLines().stream().sorted(Comparator.comparingInt(OrderLineView::getLineNo)).toList();
    }

    private static List<Long> ids(List<? extends BookView> books) {
        return books.stream().map(BookView::getId).toList();
    }

    private static List<? extends BookView> sortedById(List<? extends BookView> books) {
        return books.stream().sorted(Comparator.comparing(BookView::getId)).toList();
    }
}
