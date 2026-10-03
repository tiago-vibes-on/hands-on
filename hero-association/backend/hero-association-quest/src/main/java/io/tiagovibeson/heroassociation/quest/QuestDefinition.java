package io.tiagovibeson.heroassociation.quest;

import java.util.UUID;
import jakarta.persistence.*;

@Entity @Table(name = "quest_definition", uniqueConstraints = @UniqueConstraint(columnNames = {"definition_id", "version"}))
public class QuestDefinition {
    @Id public UUID id;
    @Column(name = "definition_id", nullable = false, updatable = false) public UUID definitionId;
    @Column(nullable = false, updatable = false) public int version;
    @Column(nullable = false, updatable = false, columnDefinition = "text") public String payload;
}
