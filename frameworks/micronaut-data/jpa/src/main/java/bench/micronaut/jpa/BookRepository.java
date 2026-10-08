package bench.micronaut.jpa;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookRepository extends CrudRepository<Book, Long>, JpaSpecificationExecutor<Book> {

    Optional<Book> findByIsbn(String isbn);

    List<Book> findByAuthorIdOrderById(Long authorId);

    List<BookSummary> queryByAuthorIdOrderById(Long authorId);

    Page<Book> findByAuthorId(Long authorId, Pageable pageable);

    List<Book> listByAuthorId(Long authorId, Sort sort);

    List<Book> findByIdIn(Collection<Long> ids);
}
