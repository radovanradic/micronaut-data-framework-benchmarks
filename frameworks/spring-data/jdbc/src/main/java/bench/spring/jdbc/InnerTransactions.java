package bench.spring.jdbc;

import bench.common.model.Inputs.NewBook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The inner side of the nested transaction scenarios: a separate bean, so the declarative boundary
 * applies (Spring proxies do not intercept self-invocation).
 */
@Service
public class InnerTransactions {

    private final BookRepository books;

    InnerTransactions(BookRepository books) {
        this.books = books;
    }

    @Transactional
    public Long saveRequired(NewBook book) {
        return books.save(Book.from(book)).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long saveRequiresNew(NewBook book) {
        return books.save(Book.from(book)).getId();
    }
}
