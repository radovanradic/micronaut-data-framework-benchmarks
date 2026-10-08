package bench.micronaut.jpa;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;

@Repository
public interface StockItemRepository extends CrudRepository<StockItem, Long> {
}
