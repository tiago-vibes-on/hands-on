package io.tiagovibeson.heroassociation.domain;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "manager_item", uniqueConstraints = @UniqueConstraint(columnNames = { "manager_id", "item_id" }))
public class ManagerItem extends UuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manager_id", nullable = false)
    private Manager manager;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(nullable = false)
    private int quantity;

    protected ManagerItem() {
    }

    public ManagerItem(Manager manager, Item item, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Item inventory quantity must be positive.");
        }
        this.manager = Objects.requireNonNull(manager);
        this.item = Objects.requireNonNull(item);
        this.quantity = quantity;
    }

    public Item getItem() {
        return item;
    }

    public int getQuantity() {
        return quantity;
    }

    public void increaseQuantity(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Item quantity increase must be positive.");
        }
        quantity = Math.addExact(quantity, amount);
    }

    public void decreaseQuantity(int amount) {
        if (amount <= 0 || amount > quantity) {
            throw new IllegalArgumentException("Item inventory quantity cannot become negative.");
        }
        quantity -= amount;
    }
}
