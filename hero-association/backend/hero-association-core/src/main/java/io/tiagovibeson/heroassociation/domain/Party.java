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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manager_id", nullable = false)
    private Manager ownerManager;

    @OneToOne(mappedBy = "party", fetch = FetchType.LAZY)
    private Quest quest;

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

    public Manager getOwnerManager() {
        return ownerManager;
    }

    public void assignQuest(Quest newQuest) {
        quest = newQuest;
    }
}
