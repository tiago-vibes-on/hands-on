package io.tiagovibeson.heroassociation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "manager")
public class Manager extends UuidEntity {

    @Column(name = "display_name", nullable = false, unique = true, length = 100)
    private String displayName;

    protected Manager() {
    }

    public String getDisplayName() {
        return displayName;
    }
}
