package bench.micronaut.jdbc;

import bench.common.model.BookSummaryView;
import bench.common.model.BookView;
import bench.common.model.Inputs.BookFilter;
import bench.common.model.Inputs.BookPage;
import bench.common.model.Inputs.NewBook;
import bench.common.model.Inputs.NewOrder;
import bench.common.model.Inputs.NewOrderLine;
import bench.common.model.Inputs.SortOrder;
import bench.common.model.OrderView;
import bench.common.model.StockItemView;
import bench.common.spi.BlockingScenarios;
import bench.common.spi.IntentionalRollbackException;
import bench.common.spi.OptimisticConflictException;
import io.micronaut.data.exceptions.OptimisticLockException;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import jakarta.persistence.criteria.Predicate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The scenarios with Micronaut Data JDBC repositories and declarative transactions.
 */
@Singleton
public class MicronautJdbcScenarios implements BlockingScenarios {

    private static final Sort BY_ID = Sort.of(Sort.Order.asc("id"));

    private final BookRepository books;
    private final OrderRepository orders;
    private final StockItemRepository stock;
    private final InnerTransactions inner;

    MicronautJdbcScenarios(BookRepository books, OrderRepository orders, StockItemRepository stock, InnerTransactions inner) {
        this.books = books;
        this.orders = orders;
        this.stock = stock;
        this.inner = inner;
    }

    // --- reads ---

    @Override
    public BookView findBookById(long id) {
        return books.findById(id).orElse(null);
    }

    @Override
    public BookView findBookByIsbn(String isbn) {
        return books.findByIsbn(isbn).orElse(null);
    }

    @Override
    public List<? extends BookView> listBooksByAuthor(long authorId) {
        return books.findByAuthorIdOrderById(authorId);
    }

    @Override
    public List<? extends BookSummaryView> bookSummariesByAuthor(long authorId) {
        return books.queryByAuthorIdOrderById(authorId);
    }

    @Override
    public BookPage pageBooksByAuthor(long authorId, int pageIndex, int pageSize) {
        Page<Book> page = books.findByAuthorId(authorId, Pageable.from(pageIndex, pageSize, BY_ID));
        return new BookPage(page.getContent(), page.getTotalSize());
    }

    @Override
    public List<? extends BookView> sortedBooksByAuthor(long authorId, List<SortOrder> sort) {
        List<Sort.Order> orders = new ArrayList<>(sort.size());
        for (SortOrder o : sort) {
            orders.add(o.ascending() ? Sort.Order.asc(o.property()) : Sort.Order.desc(o.property()));
        }
        return books.listByAuthorId(authorId, Sort.of(orders));
    }

    @Override
    public List<? extends BookView> keysetPageBooksByAuthor(long authorId, long afterId, int size) {
        return books.searchByAuthorId(authorId,
            Pageable.afterCursor(Pageable.Cursor.of(afterId), 0, size, BY_ID).withoutTotal()).getContent();
    }

    @Override
    public List<? extends BookView> findBooksByIds(List<Long> ids) {
        return books.findByIdIn(ids);
    }

    @Override
    public List<? extends BookView> findBooksByFilter(BookFilter filter) {
        PredicateSpecification<Book> spec = (root, cb) -> {
            List<Predicate> predicates = new ArrayList<>(4);
            if (filter.authorId() != null) {
                predicates.add(cb.equal(root.get("authorId"), filter.authorId()));
            }
            if (filter.minPages() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("pages"), filter.minPages()));
            }
            if (filter.maxPages() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("pages"), filter.maxPages()));
            }
            if (filter.maxPrice() != null) {
                predicates.add(cb.lessThan(root.get("price"), filter.maxPrice()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return books.findAll(spec, BY_ID);
    }

    @Override
    public List<? extends OrderView> findOrdersWithLines(String customer) {
        return orders.findByCustomerOrderById(customer);
    }

    // --- writes ---

    @Override
    public Long saveBook(NewBook book) {
        return books.save(Book.from(book)).getId();
    }

    @Override
    @Transactional
    public List<Long> saveBooks(List<NewBook> newBooks) {
        List<Book> entities = new ArrayList<>(newBooks.size());
        for (NewBook b : newBooks) {
            entities.add(Book.from(b));
        }
        List<Long> ids = new ArrayList<>(entities.size());
        for (Book saved : books.saveAll(entities)) {
            ids.add(saved.getId());
        }
        return ids;
    }

    @Override
    @Transactional
    public BookView updateBookPrice(long id, BigDecimal newPrice) {
        Book book = books.findById(id).orElseThrow();
        book.setPrice(newPrice);
        return books.update(book);
    }

    @Override
    public void deleteBook(long id) {
        books.deleteById(id);
    }

    @Override
    @Transactional
    public Long saveOrder(NewOrder newOrder) {
        PurchaseOrder order = new PurchaseOrder();
        order.setReference(newOrder.reference());
        order.setCustomer(newOrder.customer());
        order.setCreatedAt(newOrder.createdAt());
        for (NewOrderLine l : newOrder.lines()) {
            OrderLine line = new OrderLine();
            line.setOrder(order);
            line.setLineNo(l.lineNo());
            line.setProduct(l.product());
            line.setQuantity(l.quantity());
            line.setUnitPrice(l.unitPrice());
            order.getLines().add(line);
        }
        return orders.save(order).getId();
    }

    @Override
    @Transactional
    public StockItemView decrementStock(long itemId) {
        StockItem item = stock.findById(itemId).orElseThrow();
        item.setQuantity(item.getQuantity() - 1);
        try {
            return stock.update(item);
        } catch (OptimisticLockException e) {
            throw new OptimisticConflictException(e);
        }
    }

    @Override
    @Transactional
    public void decrementStockWithStaleVersion(long itemId, int staleVersion) {
        StockItem item = stock.findById(itemId).orElseThrow();
        item.setQuantity(item.getQuantity() - 1);
        item.setVersion(staleVersion);
        try {
            stock.update(item);
        } catch (OptimisticLockException e) {
            throw new OptimisticConflictException(e);
        }
    }

    // --- transactions ---

    @Override
    @Transactional(readOnly = true)
    public List<? extends BookView> txReadOnly(long id1, long id2) {
        return List.of(books.findById(id1).orElseThrow(), books.findById(id2).orElseThrow());
    }

    @Override
    @Transactional
    public Long txReadWrite(long readId, NewBook book) {
        books.findById(readId).orElseThrow();
        return books.save(Book.from(book)).getId();
    }

    @Override
    @Transactional
    public Long txNestedRequired(long readId, NewBook book) {
        books.findById(readId).orElseThrow();
        return inner.saveRequired(book);
    }

    @Override
    @Transactional
    public Long txRequiresNew(long readId, NewBook book) {
        books.findById(readId).orElseThrow();
        return inner.saveRequiresNew(book);
    }

    @Override
    @Transactional
    public void txRollback(NewBook book) {
        books.save(Book.from(book));
        throw new IntentionalRollbackException();
    }

    @Override
    @Transactional
    public void txRequiresNewThenRollback(NewBook outer, NewBook innerBook) {
        books.save(Book.from(outer));
        inner.saveRequiresNew(innerBook);
        throw new IntentionalRollbackException();
    }

    @Override
    @Transactional
    public void txNestedRequiredThenRollback(NewBook outer, NewBook innerBook) {
        books.save(Book.from(outer));
        inner.saveRequired(innerBook);
        throw new IntentionalRollbackException();
    }
}
