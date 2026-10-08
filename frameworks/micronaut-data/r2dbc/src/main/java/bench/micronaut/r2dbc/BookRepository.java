package bench.micronaut.r2dbc;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import reactor.core.publisher.Flux;

@R2dbcRepository(dialect = Dialect.POSTGRES)
public interface BookRepository extends ReactorCrudRepository<Book, Long> {

    Flux<Book> findByAuthorIdOrderById(Long authorId);
}
