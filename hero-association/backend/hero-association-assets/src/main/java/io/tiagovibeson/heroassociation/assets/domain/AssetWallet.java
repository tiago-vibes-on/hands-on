package io.tiagovibeson.heroassociation.assets.domain;

import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "asset_wallet")
@org.hibernate.annotations.Check(constraints = "gold >= 0")
public class AssetWallet {
    @Id @Column(name = "owner_id") public UUID ownerId;
    @Enumerated(EnumType.STRING) @Column(name = "owner_type", nullable = false, updatable = false, length = 10)
    public AssetOwnerType ownerType;
    @Column(nullable = false) public long gold;
    protected AssetWallet() { }
    public AssetWallet(AssetOwnerType type, UUID id) { ownerType = type; ownerId = id; }
}
