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

    @OneToOne(mappedBy = "party", fetch = FetchType.LAZY)
    private Quest quest;

    @jakarta.persistence.Column(name = "pending_asset_operation")
    private java.util.UUID pendingAssetOperation;
    public java.util.UUID getPendingAssetOperation() { return pendingAssetOperation; }
    public void requireNoPendingAssets() {
        if (pendingAssetOperation != null) throw new jakarta.ws.rs.WebApplicationException(jakarta.ws.rs.core.Response.status(409)
                .entity(java.util.Map.of("message", "Party has a pending asset operation.", "operationKey", pendingAssetOperation)).build());
    }
    public void fenceAssets(java.util.UUID id) { requireNoPendingAssets(); pendingAssetOperation = id; }
    public void finishAssets(java.util.UUID id) {
        if (!id.equals(pendingAssetOperation)) throw new IllegalStateException("Party asset fence differs from workflow.");
        pendingAssetOperation = null;
    }

    protected Party() {
    }

    public void clearQuest() {
        quest = null;
    }

    public Party(Agency agency, Manager ownerManager, String name) {
        this.agency = agency;
        this.ownerManager = ownerManager;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Quest getQuest() {
        return quest;
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

    public void assignQuest(Quest newQuest) {
        quest = newQuest;
    }
}
