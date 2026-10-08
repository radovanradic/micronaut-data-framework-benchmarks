package bench.spring.jpa;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {

    Optional<Book> findByIsbn(String isbn);

    List<Book> findByAuthorIdOrderById(Long authorId);

    List<BookSummary> findSummaryByAuthorIdOrderById(Long authorId);

    Page<Book> findByAuthorId(Long authorId, Pageable pageable);

    List<Book> findAllByAuthorId(Long authorId, Sort sort);

    Window<Book> findByAuthorIdOrderById(Long authorId, ScrollPosition position, Limit limit);

    List<Book> findByIdIn(Collection<Long> ids);
}
