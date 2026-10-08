package bench.spring.jdbc;

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
import bench.common.spi.UnsupportedScenarioException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The scenarios with Spring Data JDBC repositories and declarative transactions.
 */
@Service
public class SpringJdbcScenarios implements BlockingScenarios {

    private static final Sort BY_ID = Sort.by("id");

    private final BookRepository books;
    private final OrderRepository orders;
    private final StockItemRepository stock;
    private final InnerTransactions inner;
    private final JdbcAggregateTemplate template;

    SpringJdbcScenarios(BookRepository books, OrderRepository orders, StockItemRepository stock, InnerTransactions inner,
                        JdbcAggregateTemplate template) {
        this.books = books;
        this.orders = orders;
        this.stock = stock;
        this.inner = inner;
        this.template = template;
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
        return books.findSummaryByAuthorIdOrderById(authorId);
    }

    @Override
    public BookPage pageBooksByAuthor(long authorId, int pageIndex, int pageSize) {
        Page<Book> page = books.findByAuthorId(authorId, PageRequest.of(pageIndex, pageSize, BY_ID));
        return new BookPage(page.getContent(), page.getTotalElements());
    }

    @Override
    public List<? extends BookView> sortedBooksByAuthor(long authorId, List<SortOrder> sort) {
        List<Sort.Order> orders = new ArrayList<>(sort.size());
        for (SortOrder o : sort) {
            orders.add(o.ascending() ? Sort.Order.asc(o.property()) : Sort.Order.desc(o.property()));
        }
        return books.findAllByAuthorId(authorId, Sort.by(orders));
    }

    @Override
    public List<? extends BookView> keysetPageBooksByAuthor(long authorId, long afterId, int size) {
        throw new UnsupportedScenarioException("Spring Data JDBC has no keyset scrolling (Window/ScrollPosition)");
    }

    @Override
    public List<? extends BookView> findBooksByIds(List<Long> ids) {
        return books.findByIdIn(ids);
    }

    @Override
    public List<? extends BookView> findBooksByFilter(BookFilter filter) {
        Criteria criteria = Criteria.empty();
        if (filter.authorId() != null) {
            criteria = criteria.and("authorId").is(filter.authorId());
        }
        if (filter.minPages() != null) {
            criteria = criteria.and("pages").greaterThanOrEquals(filter.minPages());
        }
        if (filter.maxPages() != null) {
            criteria = criteria.and("pages").lessThanOrEquals(filter.maxPages());
        }
        if (filter.maxPrice() != null) {
            criteria = criteria.and("price").lessThan(filter.maxPrice());
        }
        return template.findAll(Query.query(criteria).sort(BY_ID), Book.class);
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
    public List<Long> saveBooks(List<NewBook> newBooks) {
        List<Book> entities = new ArrayList<>(newBooks.size());
        for (NewBook b : newBooks) {
            entities.add(Book.from(b));
        }
        // saveAll is transactional in Spring Data repositories
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
        return books.save(book);
    }

    @Override
    public void deleteBook(long id) {
        books.deleteById(id);
    }

    @Override
    public Long saveOrder(NewOrder newOrder) {
        PurchaseOrder order = new PurchaseOrder();
        order.setReference(newOrder.reference());
        order.setCustomer(newOrder.customer());
        order.setCreatedAt(newOrder.createdAt());
        for (NewOrderLine l : newOrder.lines()) {
            OrderLine line = new OrderLine();
            line.setLineNo(l.lineNo());
            line.setProduct(l.product());
            line.setQuantity(l.quantity());
            line.setUnitPrice(l.unitPrice());
            order.getLines().add(line);
        }
        // saving the aggregate root is transactional and inserts the lines
        return orders.save(order).getId();
    }

    @Override
    @Transactional
    public StockItemView decrementStock(long itemId) {
        StockItem item = stock.findById(itemId).orElseThrow();
        item.setQuantity(item.getQuantity() - 1);
        try {
            return stock.save(item);
        } catch (OptimisticLockingFailureException e) {
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
            stock.save(item);
        } catch (OptimisticLockingFailureException e) {
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
