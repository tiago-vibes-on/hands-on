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
@Table(name = "party", uniqueConstraints = @UniqueConstraint(columnNames = { "agency_id", "name" }))
public class Party extends UuidEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    @OneToOne(mappedBy = "party", fetch = FetchType.LAZY)
    private Quest quest;

    protected Party() {
    }

    public Party(Agency agency, String name) {
        this.agency = agency;
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
}
