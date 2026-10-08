package bench.micronaut.jdbc;

import bench.common.model.OrderView;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@MappedEntity("purchase_order")
public class PurchaseOrder implements OrderView {

    @Id
    @GeneratedValue
    private Long id;
    private UUID reference;
    private String customer;
    private Instant createdAt;
    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "order", cascade = Relation.Cascade.PERSIST)
    private List<OrderLine> lines = new ArrayList<>();

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
    public List<OrderLine> getLines() {
        return lines;
    }

    public void setLines(List<OrderLine> lines) {
        this.lines = lines;
    }
}
