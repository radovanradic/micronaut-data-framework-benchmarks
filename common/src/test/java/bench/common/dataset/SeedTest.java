package bench.common.dataset;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeedTest {

    @Test
    void bookLayoutMatchesTheSpec() {
        long total = LongStream.rangeClosed(1, Seed.AUTHORS).map(Seed::bookCountOfAuthor).sum();
        assertEquals(Seed.BOOKS, total);
        assertEquals(10, Seed.bookCountOfAuthor(1));
        assertEquals(100, Seed.bookCountOfAuthor(2));
        assertEquals(10_000, Seed.bookCountOfAuthor(3));
        for (long author = 1; author <= Seed.AUTHORS; author++) {
            for (Seed.SeedBook b : Seed.booksOfAuthor(author)) {
                assertEquals(author, b.authorId(), "author of book " + b.id());
            }
        }
        assertEquals(Seed.FIRST_MUTABLE_BOOK_ID + Seed.MUTABLE_BOOKS - 1, Seed.BOOKS);
    }

    @Test
    void uniqueColumnsAreUnique() {
        Set<String> isbns = new HashSet<>();
        Set<String> titles = new HashSet<>();
        for (long id = 1; id <= Seed.BOOKS; id++) {
            assertTrue(isbns.add(Seed.book(id).isbn()));
            assertTrue(titles.add(Seed.book(id).title()));
        }
        Set<Object> references = new HashSet<>();
        for (long id = 1; id <= Seed.ORDERS; id++) {
            assertTrue(references.add(Seed.order(id).reference()));
        }
    }

    @Test
    void valuesStayWithinColumnRanges() {
        for (long id = 1; id <= Seed.BOOKS; id++) {
            Seed.SeedBook b = Seed.book(id);
            assertTrue(b.price().scale() == 2 && b.price().signum() > 0);
            assertTrue(b.pages() >= 50 && b.pages() < 1_000);
        }
    }
}
