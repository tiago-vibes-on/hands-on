package io.tiagovibeson.heroassociation.domain;

import java.util.UUID;

/** Immutable projection supplied by Assets; not a Core database entity. */
public final class AgencyRune {
    private final Agency agency;
    private final Rune rune;
    private final int quantity;
    public AgencyRune(Agency agency, Rune rune, int quantity) {
        this.agency = agency;
        this.rune = rune;
        this.quantity = quantity;
    }
    public Agency getAgency() { return agency; }
    public Rune getRune() { return rune; }
    public int getQuantity() { return quantity; }
}
