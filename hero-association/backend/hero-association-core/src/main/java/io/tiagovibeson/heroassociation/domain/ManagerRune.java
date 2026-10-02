package io.tiagovibeson.heroassociation.domain;

import java.util.UUID;

/** Immutable projection supplied by Assets; not a Core database entity. */
public final class ManagerRune {
    private final Manager manager;
    private final Rune rune;
    private final int quantity;
    public ManagerRune(Manager manager, Rune rune, int quantity) {
        this.manager = manager;
        this.rune = rune;
        this.quantity = quantity;
    }
    public Manager getManager() { return manager; }
    public Rune getRune() { return rune; }
    public int getQuantity() { return quantity; }
}
