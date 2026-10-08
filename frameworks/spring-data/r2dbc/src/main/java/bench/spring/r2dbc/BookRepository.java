package bench.spring.r2dbc;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface BookRepository extends ReactiveCrudRepository<Book, Long> {

    Flux<Book> findByAuthorIdOrderById(Long authorId);
}
