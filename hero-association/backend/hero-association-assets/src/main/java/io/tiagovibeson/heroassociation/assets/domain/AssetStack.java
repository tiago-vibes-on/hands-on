package io.tiagovibeson.heroassociation.assets.domain;

import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "asset_stack", uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "resource_type", "resource_id"}))
@org.hibernate.annotations.Check(constraints = "quantity >= 0")
public class AssetStack extends UuidEntity {
    @Column(name = "owner_id", nullable = false, updatable = false) public UUID ownerId;
    @Column(name = "resource_type", nullable = false, updatable = false, length = 10) public String resourceType;
    @Column(name = "resource_id", nullable = false, updatable = false) public UUID resourceId;
    @Column(nullable = false) public int quantity;
    protected AssetStack() { }
    public AssetStack(UUID owner, String type, UUID resource, int quantity) {
        ownerId = owner; resourceType = type; resourceId = resource; this.quantity = quantity;
    }
}
