package io.tiagovibeson.heroassociation.quest;

import java.util.UUID;
import jakarta.persistence.*;

@Entity @Table(name = "quest_command")
public class QuestCommand {
    @Id public UUID id;
    @Column(nullable = false, updatable = false) public UUID managerId;
    @Column(nullable = false, updatable = false, columnDefinition = "text") public String requestJson;
    @Column(nullable = false, updatable = false, columnDefinition = "text") public String responseJson;
}
