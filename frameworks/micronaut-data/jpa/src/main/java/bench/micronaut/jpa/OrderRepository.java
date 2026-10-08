package bench.micronaut.jpa;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@Repository
public interface OrderRepository extends CrudRepository<PurchaseOrder, Long> {

    // LEFT JOIN FETCH, like the JDBC variant, so orders without lines are kept
    @Join(value = "lines", type = Join.Type.LEFT_FETCH)
    List<PurchaseOrder> findByCustomerOrderById(String customer);
}
