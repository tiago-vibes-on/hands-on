package io.tiagovibeson.heroassociation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "party", uniqueConstraints = @UniqueConstraint(columnNames = { "agency_id", "manager_id", "name" }))
public class Party extends UuidEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agency_id")
    private Agency agency;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manager_id", nullable = false)
    private Manager ownerManager;

    protected Party() {
    }

    public Party(Agency agency, Manager ownerManager, String name) {
        this.agency = agency;
        this.ownerManager = ownerManager;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Agency getAgency() {
        return agency;
    }

    public void attachToAgency(Agency agency) {
        if (this.agency != null) {
            throw new IllegalStateException("Party already belongs to an agency.");
        }
        this.agency = agency;
    }

    public Manager getOwnerManager() {
        return ownerManager;
    }

}
