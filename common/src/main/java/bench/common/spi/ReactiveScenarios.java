package bench.common.spi;

import bench.common.model.BookView;
import bench.common.model.Inputs.NewBook;
import org.reactivestreams.Publisher;

import java.math.BigDecimal;
import java.util.List;

/**
 * The reactive scenarios of SPEC.md section 4.4. Results are streamed; implementations never collect
 * them into a list.
 */
public interface ReactiveScenarios {

    /** {@code reactiveFindById}: emits the book, or completes empty. */
    Publisher<? extends BookView> findBookById(long id);

    /** {@code reactiveStream10/100/10000}: streams all books of the author, ordered by id. */
    Publisher<? extends BookView> streamBooksByAuthor(long authorId);

    /** {@code reactiveSaveAll100/1000}: inserts the books atomically, emits the generated ids in input order. */
    Publisher<Long> saveBooks(List<NewBook> books);

    /** {@code reactiveTxUpdate}: in one reactive transaction loads the book, sets the price, saves; emits it. */
    Publisher<? extends BookView> txUpdateBookPrice(long id, BigDecimal newPrice);

    /** {@code reactiveTxRollback}: in a reactive transaction saves the book, then signals {@link IntentionalRollbackException}. */
    Publisher<?> txRollback(NewBook book);
}
