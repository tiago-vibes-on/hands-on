package io.tiagovibeson.heroassociation.quest;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity @Table(name = "quest_assignment")
public class QuestAssignment {
    @Id public UUID id;
    @Column(nullable = false, updatable = false) public UUID managerId;
    @Column(nullable = false, updatable = false, columnDefinition = "text") public String definitionJson;
    @Column(nullable = false) public int progress;
    @Column(nullable = false, length = 30) public String status;
    @Column(nullable = false, updatable = false) public Instant acceptedAt;
    public Instant finishedAt;
}
