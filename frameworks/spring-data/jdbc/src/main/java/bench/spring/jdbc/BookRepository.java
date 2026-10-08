package bench.spring.jdbc;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.ListCrudRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookRepository extends ListCrudRepository<Book, Long> {

    Optional<Book> findByIsbn(String isbn);

    List<Book> findByAuthorIdOrderById(Long authorId);

    List<BookSummary> findSummaryByAuthorIdOrderById(Long authorId);

    Page<Book> findByAuthorId(Long authorId, Pageable pageable);

    List<Book> findAllByAuthorId(Long authorId, Sort sort);

    List<Book> findByIdIn(Collection<Long> ids);
}
