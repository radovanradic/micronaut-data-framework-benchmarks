package bench.micronaut.jdbc;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface OrderRepository extends CrudRepository<PurchaseOrder, Long> {

    // LEFT_FETCH: in SQL, FETCH is an inner join, which would drop orders without lines
    @Join(value = "lines", type = Join.Type.LEFT_FETCH)
    List<PurchaseOrder> findByCustomerOrderById(String customer);
}
