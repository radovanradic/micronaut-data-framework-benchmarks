package bench.spring.r2dbc;

import bench.common.model.Inputs.NewBook;
import bench.common.spi.IntentionalRollbackException;
import bench.common.spi.ReactiveScenarios;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The reactive scenarios with Spring Data R2DBC; {@code @Transactional} on a method returning a
 * Reactor type runs it in a reactive transaction (R2dbcTransactionManager).
 */
@Service
public class SpringR2dbcScenarios implements ReactiveScenarios {

    private final BookRepository books;

    SpringR2dbcScenarios(BookRepository books) {
        this.books = books;
    }

    @Override
    public Mono<Book> findBookById(long id) {
        return books.findById(id);
    }

    @Override
    public Flux<Book> streamBooksByAuthor(long authorId) {
        return books.findByAuthorIdOrderById(authorId);
    }

    @Override
    @Transactional
    public Flux<Long> saveBooks(List<NewBook> newBooks) {
        List<Book> entities = new ArrayList<>(newBooks.size());
        for (NewBook b : newBooks) {
            entities.add(Book.from(b));
        }
        return books.saveAll(entities).map(Book::getId);
    }

    @Override
    @Transactional
    public Mono<Book> txUpdateBookPrice(long id, BigDecimal newPrice) {
        return books.findById(id).flatMap(book -> {
            book.setPrice(newPrice);
            return books.save(book);
        });
    }

    @Override
    @Transactional
    public Mono<Void> txRollback(NewBook book) {
        return books.save(Book.from(book)).then(Mono.error(new IntentionalRollbackException()));
    }
}
