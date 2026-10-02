package io.tiagovibeson.heroassociation.domain;

import java.util.UUID;

/** Immutable projection supplied by Assets; not a Core database entity. */
public final class Rune {
    private final UUID id;
    private final String code;
    private final String name;
    private final String symbol;
    private final String stats;
    private final String description;
    private final RuneEffect effect;
    private final double effectValue;
    public Rune(UUID id, String code, String name, String symbol, String stats, String description, RuneEffect effect, double effectValue) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.symbol = symbol;
        this.stats = stats;
        this.description = description;
        this.effect = effect;
        this.effectValue = effectValue;
    }
    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getSymbol() { return symbol; }
    public String getStats() { return stats; }
    public String getDescription() { return description; }
    public RuneEffect getEffect() { return effect; }
    public double getEffectValue() { return effectValue; }
}
