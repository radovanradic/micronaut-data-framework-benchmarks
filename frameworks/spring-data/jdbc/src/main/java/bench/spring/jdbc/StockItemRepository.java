package bench.spring.jdbc;

import org.springframework.data.repository.ListCrudRepository;

public interface StockItemRepository extends ListCrudRepository<StockItem, Long> {
}
