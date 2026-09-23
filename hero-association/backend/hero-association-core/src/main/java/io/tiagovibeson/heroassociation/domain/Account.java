package io.tiagovibeson.heroassociation.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "account")
public class Account extends UuidEntity {

    @Column(name = "keycloak_subject", nullable = false, unique = true, updatable = false, length = 255)
    private String keycloakSubject;

    @Column(length = 255)
    private String email;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_login_at", nullable = false)
    private Instant lastLoginAt;

    protected Account() {
    }

    public Account(String keycloakSubject, String email, boolean emailVerified) {
        this.keycloakSubject = keycloakSubject;
        this.email = email;
        this.emailVerified = emailVerified;
        this.status = AccountStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.lastLoginAt = createdAt;
    }

    public String getKeycloakSubject() {
        return keycloakSubject;
    }

    public String getEmail() {
        return email;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void registerLogin(String email, boolean emailVerified) {
        if (email != null) {
            this.email = email;
        }
        this.emailVerified = emailVerified;
        this.lastLoginAt = Instant.now();
    }
}
