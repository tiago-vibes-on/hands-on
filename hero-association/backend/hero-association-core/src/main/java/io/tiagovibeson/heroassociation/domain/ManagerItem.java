package io.tiagovibeson.heroassociation.domain;

import java.util.UUID;

/** Immutable projection supplied by Assets; not a Core database entity. */
public final class ManagerItem {
    private final Manager manager;
    private final Item item;
    private final int quantity;
    public ManagerItem(Manager manager, Item item, int quantity) {
        this.manager = manager;
        this.item = item;
        this.quantity = quantity;
    }
    public Manager getManager() { return manager; }
    public Item getItem() { return item; }
    public int getQuantity() { return quantity; }
}
