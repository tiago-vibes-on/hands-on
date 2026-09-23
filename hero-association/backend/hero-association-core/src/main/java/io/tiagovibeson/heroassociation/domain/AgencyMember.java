package io.tiagovibeson.heroassociation.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "agency_member",
        uniqueConstraints = @UniqueConstraint(name = "uk_agency_member_agency_manager", columnNames = { "agency_id", "manager_id" }))
public class AgencyMember extends UuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manager_id", nullable = false)
    private Manager manager;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AgencyMemberRole role;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    protected AgencyMember() {
    }

    public AgencyMember(Agency agency, Manager manager, AgencyMemberRole role) {
        this.agency = agency;
        this.manager = manager;
        this.role = role;
        this.joinedAt = Instant.now();
    }

    public Agency getAgency() {
        return agency;
    }

    public Manager getManager() {
        return manager;
    }

    public AgencyMemberRole getRole() {
        return role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
