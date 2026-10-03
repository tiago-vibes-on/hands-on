package io.tiagovibeson.heroassociation.quest;

import java.util.UUID;
import jakarta.persistence.*;

@Entity @Table(name = "quest_manager")
public class QuestManager {
    @Id public UUID id;
    public UUID activeAssignment;
    public UUID activeExpedition;
}
