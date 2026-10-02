package io.tiagovibeson.heroassociation.assets.domain;

import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "hero_rune", uniqueConstraints = @UniqueConstraint(columnNames = {"hero_id", "slot_index"}))
@org.hibernate.annotations.Check(constraints = "slot_index >= 0 AND slot_index < 5")
public class HeroRune extends UuidEntity {
    @Column(name = "hero_id", nullable = false, updatable = false) public UUID heroId;
    @Column(name = "slot_index", nullable = false, updatable = false) public int slotIndex;
    @ManyToOne(optional = false) @JoinColumn(name = "rune_id", nullable = false) public Rune rune;
    protected HeroRune() { }
    public HeroRune(UUID hero, int slot, Rune rune) { heroId = hero; slotIndex = slot; this.rune = rune; }
}
