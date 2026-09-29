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
@Table(name = "manager_rune", uniqueConstraints = @UniqueConstraint(columnNames = { "manager_id", "rune_id" }))
public class ManagerRune extends UuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manager_id", nullable = false)
    private Manager manager;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rune_id", nullable = false)
    private Rune rune;

    @Column(nullable = false)
    private int quantity;

    protected ManagerRune() {
    }

    public ManagerRune(Manager manager, Rune rune, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Rune inventory quantity must be positive.");
        }
        this.manager = Objects.requireNonNull(manager);
        this.rune = Objects.requireNonNull(rune);
        this.quantity = quantity;
    }

    public Rune getRune() {
        return rune;
    }

    public int getQuantity() {
        return quantity;
    }

    public void increaseQuantity(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Rune quantity increase must be positive.");
        }
        quantity = Math.addExact(quantity, amount);
    }

    public void decreaseQuantity(int amount) {
        if (amount <= 0 || amount > quantity) {
            throw new IllegalArgumentException("Rune inventory quantity cannot become negative.");
        }
        quantity -= amount;
    }
}
