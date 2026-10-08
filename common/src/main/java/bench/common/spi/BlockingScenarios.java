package bench.common.spi;

import bench.common.model.BookSummaryView;
import bench.common.model.BookView;
import bench.common.model.Inputs.BookFilter;
import bench.common.model.Inputs.BookPage;
import bench.common.model.Inputs.NewBook;
import bench.common.model.Inputs.NewOrder;
import bench.common.model.Inputs.SortOrder;
import bench.common.model.OrderView;
import bench.common.model.StockItemView;

import java.math.BigDecimal;
import java.util.List;

/**
 * The blocking scenarios of SPEC.md sections 4.1 to 4.3, implemented with a framework's idiomatic API.
 * Methods return the framework's own objects (entities implement the view interfaces).
 *
 * <p>A scenario the framework can't express idiomatically throws {@link UnsupportedScenarioException}
 * and is listed in {@link ImplementationInfo#unsupported()}.
 */
public interface BlockingScenarios {

    // --- reads ---

    /** {@code findById}: book by primary key, or {@code null}. */
    BookView findBookById(long id);

    /** {@code findByIsbn}: book by its unique isbn, or {@code null}. */
    BookView findBookByIsbn(String isbn);

    /** {@code list10/100/10000}: all books of the author, ordered by id. */
    List<? extends BookView> listBooksByAuthor(long authorId);

    /** {@code projection}: (id, title, price) DTOs of the author's books, ordered by id. */
    List<? extends BookSummaryView> bookSummariesByAuthor(long authorId);

    /** {@code pageWithCount}: offset page of the author's books ordered by id, with total count. */
    BookPage pageBooksByAuthor(long authorId, int pageIndex, int pageSize);

    /** {@code sort}: the author's books sorted by a sort definition only known at runtime. */
    List<? extends BookView> sortedBooksByAuthor(long authorId, List<SortOrder> sort);

    /** {@code keysetPage}: {@code size} books of the author with id greater than {@code afterId}, by id, using the framework's keyset/cursor API, no count. */
    List<? extends BookView> keysetPageBooksByAuthor(long authorId, long afterId, int size);

    /** {@code inList1000}: books whose id is in the list, any order. */
    List<? extends BookView> findBooksByIds(List<Long> ids);

    /** {@code criteria}: books matching the filter (null fields skipped), ordered by id, via the criteria/specification API. */
    List<? extends BookView> findBooksByFilter(BookFilter filter);

    /** {@code joinFetch}: orders of the customer ordered by id, with all lines loaded. */
    List<? extends OrderView> findOrdersWithLines(String customer);

    // --- writes ---

    /** {@code save}: inserts a book, returns the generated id. */
    Long saveBook(NewBook book);

    /** {@code saveAll100/1000}: inserts the books atomically, returns the generated ids in input order. */
    List<Long> saveBooks(List<NewBook> books);

    /** {@code update}: in one transaction loads the book, sets the price, saves; returns the updated book. */
    BookView updateBookPrice(long id, BigDecimal newPrice);

    /** {@code delete}: deletes the book by id through the repository. */
    void deleteBook(long id);

    /** {@code cascadeInsert}: inserts the order and its lines with one cascading save, returns the order id. */
    Long saveOrder(NewOrder order);

    /** {@code optimisticUpdate}: in one transaction loads the item, decrements quantity by 1, saves (versioned). */
    StockItemView decrementStock(long itemId);

    // --- transactions ---

    /** {@code txReadOnly}: read-only transaction loading two books. */
    List<? extends BookView> txReadOnly(long id1, long id2);

    /** {@code txReadWrite}: read-write transaction: load a book, save a new one; returns its id. */
    Long txReadWrite(long readId, NewBook book);

    /** {@code txNestedRequired}: outer REQUIRED loads a book, inner REQUIRED (other bean) saves; returns the id. */
    Long txNestedRequired(long readId, NewBook book);

    /** {@code txRequiresNew}: outer REQUIRED loads a book, inner REQUIRES_NEW (other bean) saves; returns the id. */
    Long txRequiresNew(long readId, NewBook book);

    /** {@code txRollback}: read-write transaction saves the book, then throws {@link IntentionalRollbackException}. */
    void txRollback(NewBook book);

    // --- semantics checks (correctness check only, not benchmarked) ---

    /** Outer REQUIRED saves {@code outer}, inner REQUIRES_NEW saves {@code inner}, then outer throws {@link IntentionalRollbackException}. */
    void txRequiresNewThenRollback(NewBook outer, NewBook inner);

    /** Outer REQUIRED saves {@code outer}, inner REQUIRED saves {@code inner}, then outer throws {@link IntentionalRollbackException}. */
    void txNestedRequiredThenRollback(NewBook outer, NewBook inner);

    /** Saves the item with quantity - 1 and the given (stale) version; must throw {@link OptimisticConflictException}. */
    void decrementStockWithStaleVersion(long itemId, int staleVersion);
}
