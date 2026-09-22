package io.tiagovibeson.heroassociation.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;

@MappedSuperclass
public abstract class UuidEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @PrePersist
    protected void assignId() {
        if (id == null) {
            id = UuidV7.next();
        }
    }

    public UUID getId() {
        return id;
    }
}
