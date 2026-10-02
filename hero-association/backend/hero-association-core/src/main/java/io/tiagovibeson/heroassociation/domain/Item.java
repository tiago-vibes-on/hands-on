package io.tiagovibeson.heroassociation.domain;

import java.util.UUID;

/** Immutable projection supplied by Assets; not a Core database entity. */
public final class Item {
    private final UUID id;
    private final String code;
    private final String name;
    private final String symbol;
    private final String description;
    public Item(UUID id, String code, String name, String symbol, String description) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.symbol = symbol;
        this.description = description;
    }
    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getSymbol() { return symbol; }
    public String getDescription() { return description; }
}
