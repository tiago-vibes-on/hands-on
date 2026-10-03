package io.tiagovibeson.heroassociation.quest;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity @Table(name = "quest_admission")
public class QuestAdmission {
    @Id public UUID id;
    @Column(nullable = false, updatable = false) public UUID managerId;
    @Column(nullable = false, updatable = false, columnDefinition = "text") public String pinJson;
    @Column(columnDefinition = "text") public String requestJson;
    @Column(nullable = false, length = 30) public String status;
    public UUID claimToken;
    public Instant claimUntil;
    public Instant nextAttemptAt;
    public int attempts;
    public String message;
    @Column(nullable = false, updatable = false) public Instant createdAt;
}
