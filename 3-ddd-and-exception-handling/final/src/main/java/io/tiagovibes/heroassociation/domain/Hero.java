package io.tiagovibes.heroassociation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "heroes")
public class Hero {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String alias;

    @Column(nullable = false, length = 255)
    private String power;

    protected Hero() {
    }

    private Hero(String name, String alias, String power) {
        replace(name, alias, power);
    }

    public static Hero register(String name, String alias, String power) {
        return new Hero(name, alias, power);
    }

    public void replace(String name, String alias, String power) {
        this.name = name;
        this.alias = alias;
        this.power = power;
    }

    public void update(String name, String alias, String power) {
        if (name != null) {
            this.name = name;
        }
        if (alias != null) {
            this.alias = alias;
        }
        if (power != null) {
            this.power = power;
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAlias() {
        return alias;
    }

    public String getPower() {
        return power;
    }
}
