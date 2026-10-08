package bench.micronaut.jdbc;

import bench.common.model.Inputs.NewBook;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

/**
 * The inner side of the nested transaction scenarios: a separate bean, so the declarative boundary
 * applies.
 */
@Singleton
public class InnerTransactions {

    private final BookRepository books;

    InnerTransactions(BookRepository books) {
        this.books = books;
    }

    @Transactional
    public Long saveRequired(NewBook book) {
        return books.save(Book.from(book)).getId();
    }

    @Transactional(propagation = TransactionDefinition.Propagation.REQUIRES_NEW)
    public Long saveRequiresNew(NewBook book) {
        return books.save(Book.from(book)).getId();
    }
}
