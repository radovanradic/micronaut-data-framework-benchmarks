package bench.spring.jdbc;

import org.springframework.data.repository.ListCrudRepository;

import java.util.List;

public interface OrderRepository extends ListCrudRepository<PurchaseOrder, Long> {

    // the aggregate is loaded with its lines; Spring Data JDBC issues one query for the orders and
    // one per order for its lines (single-query loading does not apply to derived queries)
    List<PurchaseOrder> findByCustomerOrderById(String customer);
}
