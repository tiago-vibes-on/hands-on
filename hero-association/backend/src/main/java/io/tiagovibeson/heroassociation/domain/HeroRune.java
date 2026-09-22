package io.tiagovibeson.heroassociation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "hero_rune", uniqueConstraints = @UniqueConstraint(columnNames = { "hero_id", "slot_index" }))
public class HeroRune extends UuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hero_id", nullable = false)
    private Hero hero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rune_id", nullable = false)
    private Rune rune;

    @Column(name = "slot_index", nullable = false)
    private int slotIndex;

    protected HeroRune() {
    }

    public HeroRune(Hero hero, Rune rune, int slotIndex) {
        this.hero = hero;
        this.rune = rune;
        this.slotIndex = slotIndex;
    }

    public Rune getRune() {
        return rune;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public void replaceRune(Rune replacement) {
        rune = replacement;
    }
}
