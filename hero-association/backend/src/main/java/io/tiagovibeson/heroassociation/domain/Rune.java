package io.tiagovibeson.heroassociation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "rune")
public class Rune extends UuidEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 10)
    private String symbol;

    @Column(nullable = false, length = 100)
    private String stats;

    @Column(nullable = false, length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RuneEffect effect;

    @Column(name = "effect_value", nullable = false)
    private double effectValue;

    protected Rune() {
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getStats() {
        return stats;
    }

    public String getDescription() {
        return description;
    }

    public RuneEffect getEffect() {
        return effect;
    }

    public double getEffectValue() {
        return effectValue;
    }
}
