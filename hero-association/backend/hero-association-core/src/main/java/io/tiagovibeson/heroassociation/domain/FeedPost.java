package io.tiagovibeson.heroassociation.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "feed_post")
public class FeedPost extends UuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_type", nullable = false, length = 20)
    private FeedPostAuthorType authorType;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "author_name", nullable = false, length = 100)
    private String authorName;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(name = "item_id") private UUID itemId;
    @Column(name = "item_code", length = 50) private String itemCode;
    @Column(name = "item_name", length = 100) private String itemName;
    @Column(name = "item_symbol", length = 10) private String itemSymbol;
    @Column(name = "item_description", length = 255) private String itemDescription;

    @Column(name = "item_quantity")
    private Integer itemQuantity;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    protected FeedPost() {
    }

    public FeedPost(
            Agency agency,
            FeedPostAuthorType authorType,
            UUID authorId,
            String authorName,
            String content,
            Item item,
            Integer itemQuantity) {
        this.agency = agency;
        this.authorType = authorType;
        this.authorId = authorId;
        this.authorName = authorName;
        this.content = content;
        if (item != null) {
            itemId = item.getId(); itemCode = item.getCode(); itemName = item.getName();
            itemSymbol = item.getSymbol(); itemDescription = item.getDescription();
        }
        this.itemQuantity = itemQuantity;
        publishedAt = Instant.now();
    }

    public FeedPostAuthorType getAuthorType() {
        return authorType;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public String getContent() {
        return content;
    }

    public Item getItem() {
        return itemId == null ? null : new Item(itemId, itemCode, itemName, itemSymbol, itemDescription);
    }

    public Integer getItemQuantity() {
        return itemQuantity;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
