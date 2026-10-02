package io.tiagovibeson.heroassociation.domain;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

/** Core orchestration and eligibility only; resource values and receipts belong to Assets. */
@Entity @Table(name = "asset_workflow")
public class AssetWorkflow {
    @Id public UUID id;
    @Version public long version;
    @Column(nullable = false, updatable = false, length = 30) public String kind;
    @Column(nullable = false, updatable = false) public UUID managerId;
    @Column(updatable = false) public UUID agencyId;
    @Column(updatable = false) public UUID heroId;
    @Column(updatable = false) public UUID questId;
    @Column(updatable = false) public UUID partyId;
    @Column(updatable = false) public UUID expeditionId;
    @Column(nullable = false, updatable = false, columnDefinition = "text") public String requestJson;
    @Column(nullable = false, updatable = false, columnDefinition = "text") public String commandJson;
    @Column(updatable = false, columnDefinition = "text") public String contextJson;
    @Column(nullable = false, length = 20) public String status = "PENDING";
    @Column(nullable = false, updatable = false) public Instant createdAt = Instant.now();
    @Column(nullable = false) public Instant nextAttemptAt = Instant.now();
    public UUID claimToken;
    public Instant claimUntil;
    @Column(nullable = false) public int attempts;
    public Integer rejectionStatus;
    @Column(length = 300) public String message;
    @Column(columnDefinition = "text") public String receiptJson;
    protected AssetWorkflow() { }
    public AssetWorkflow(UUID id, String kind, UUID manager, UUID agency, String request, String command) {
        this.id = id; this.kind = kind; managerId = manager; agencyId = agency; requestJson = request; commandJson = command;
    }
    public View view() { return new View(id, kind, status, rejectionStatus, message); }
    public record View(UUID operationKey, String kind, String status, Integer rejectionStatus, String message) { }
}
