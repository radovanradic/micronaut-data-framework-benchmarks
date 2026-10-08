package bench.spring.jdbc;

import bench.common.model.OrderView;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Aggregate root; the lines belong to the aggregate (no repository of their own).
 */
@Table("purchase_order")
public class PurchaseOrder implements OrderView {

    @Id
    private Long id;
    private UUID reference;
    private String customer;
    private Instant createdAt;
    @MappedCollection(idColumn = "order_id")
    private Set<OrderLine> lines = new HashSet<>();

    @Override
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Override
    public UUID getReference() {
        return reference;
    }

    public void setReference(UUID reference) {
        this.reference = reference;
    }

    @Override
    public String getCustomer() {
        return customer;
    }

    public void setCustomer(String customer) {
        this.customer = customer;
    }

    @Override
    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public Set<OrderLine> getLines() {
        return lines;
    }

    public void setLines(Set<OrderLine> lines) {
        this.lines = lines;
    }
}
