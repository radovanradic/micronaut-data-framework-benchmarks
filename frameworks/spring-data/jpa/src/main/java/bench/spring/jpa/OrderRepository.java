package bench.spring.jpa;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<PurchaseOrder, Long> {

    // fetch graph: the lines are loaded with a left join in the same query
    @EntityGraph(attributePaths = "lines")
    List<PurchaseOrder> findByCustomerOrderById(String customer);
}
