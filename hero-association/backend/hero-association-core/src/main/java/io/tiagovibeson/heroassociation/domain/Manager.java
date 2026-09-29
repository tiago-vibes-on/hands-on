package io.tiagovibeson.heroassociation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "manager")
public class Manager extends UuidEntity {

    @Column(name = "display_name", nullable = false, unique = true, length = 100)
    private String displayName;

    @Column(name = "display_name_normalized", nullable = false, unique = true, length = 100)
    private String displayNameNormalized;

    @Column(nullable = false)
    @org.hibernate.annotations.ColumnDefault("0")
    private long gold;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private Account account;

    protected Manager() {
    }

    public Manager(Account account, String displayName, String displayNameNormalized) {
        this.account = account;
        this.displayName = displayName;
        this.displayNameNormalized = displayNameNormalized;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Account getAccount() {
        return account;
    }

    public long getGold() {
        return gold;
    }

    public void increaseGold(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Gold increase cannot be negative.");
        }
        gold = Math.addExact(gold, amount);
    }

    public void decreaseGold(long amount) {
        if (amount < 0 || amount > gold) {
            throw new IllegalArgumentException("Manager gold cannot become negative.");
        }
        gold -= amount;
    }
}
