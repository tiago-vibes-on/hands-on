package io.tiagovibeson.heroassociation.domain;

import java.util.UUID;

/** Immutable projection supplied by Assets; not a Core database entity. */
public final class AgencyItem {
    private final Agency agency;
    private final Item item;
    private final int quantity;
    public AgencyItem(Agency agency, Item item, int quantity) {
        this.agency = agency;
        this.item = item;
        this.quantity = quantity;
    }
    public Agency getAgency() { return agency; }
    public Item getItem() { return item; }
    public int getQuantity() { return quantity; }
}
