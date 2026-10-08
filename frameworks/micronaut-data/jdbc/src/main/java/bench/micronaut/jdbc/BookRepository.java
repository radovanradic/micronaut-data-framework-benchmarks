package bench.micronaut.jdbc;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.CursoredPage;
import io.micronaut.data.model.CursoredPageable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface BookRepository extends CrudRepository<Book, Long>, JpaSpecificationExecutor<Book> {

    Optional<Book> findByIsbn(String isbn);

    List<Book> findByAuthorIdOrderById(Long authorId);

    List<BookSummary> queryByAuthorIdOrderById(Long authorId);

    Page<Book> findByAuthorId(Long authorId, Pageable pageable);

    List<Book> listByAuthorId(Long authorId, Sort sort);

    CursoredPage<Book> searchByAuthorId(Long authorId, CursoredPageable pageable);

    List<Book> findByIdIn(Collection<Long> ids);
}
