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
}
