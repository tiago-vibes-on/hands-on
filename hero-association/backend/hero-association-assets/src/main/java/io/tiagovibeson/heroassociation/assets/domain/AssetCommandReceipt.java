package io.tiagovibeson.heroassociation.assets.domain;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity @Table(name = "asset_command_receipt")
public class AssetCommandReceipt {
    @Id public UUID operationKey;
    @Column(nullable = false, updatable = false, length = 30) public String kind;
    @Column(nullable = false, updatable = false, columnDefinition = "text") public String requestJson;
    @Column(nullable = false, updatable = false, columnDefinition = "text") public String responseJson;
    @Column(nullable = false, updatable = false) public Instant createdAt;
    protected AssetCommandReceipt() { }
    public AssetCommandReceipt(UUID key, String kind, String request, String response) {
        operationKey = key; this.kind = kind; requestJson = request; responseJson = response; createdAt = Instant.now();
    }
}
