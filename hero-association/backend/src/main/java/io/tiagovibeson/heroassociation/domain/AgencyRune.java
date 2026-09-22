package io.tiagovibeson.heroassociation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "agency_rune", uniqueConstraints = @UniqueConstraint(columnNames = { "agency_id", "rune_id" }))
public class AgencyRune extends UuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rune_id", nullable = false)
    private Rune rune;

    @Column(nullable = false)
    private int quantity;

    protected AgencyRune() {
    }

    public AgencyRune(Agency agency, Rune rune, int quantity) {
        this.agency = agency;
        this.rune = rune;
        this.quantity = quantity;
    }

    public Rune getRune() {
        return rune;
    }

    public int getQuantity() {
        return quantity;
    }

    public void decreaseQuantity() {
        if (quantity == 0) {
            throw new IllegalStateException("Rune inventory quantity cannot become negative.");
        }

        quantity--;
    }

    public void increaseQuantity() {
        quantity++;
    }
}
