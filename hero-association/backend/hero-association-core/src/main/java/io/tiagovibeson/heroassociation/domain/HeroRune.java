package io.tiagovibeson.heroassociation.domain;

import java.util.UUID;

/** Immutable projection supplied by Assets; not a Core database entity. */
public final class HeroRune {
    private final Hero hero;
    private final Rune rune;
    private final int slotIndex;
    public HeroRune(Hero hero, Rune rune, int slotIndex) {
        this.hero = hero;
        this.rune = rune;
        this.slotIndex = slotIndex;
    }
    public Hero getHero() { return hero; }
    public Rune getRune() { return rune; }
    public int getSlotIndex() { return slotIndex; }
}
