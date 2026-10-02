package io.tiagovibeson.heroassociation.market;

import java.util.UUID;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

@Entity @Table(name = "market_book")
public class MarketBook {
    @Id public UUID id;
    @Column(name = "next_sequence", nullable = false) public long nextSequence;
    protected MarketBook() { }
}
