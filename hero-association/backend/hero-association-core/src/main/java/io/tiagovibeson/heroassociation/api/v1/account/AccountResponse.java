package io.tiagovibeson.heroassociation.api.v1.account;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.tiagovibeson.heroassociation.domain.Account;
import io.tiagovibeson.heroassociation.domain.AgencyMember;
import io.tiagovibeson.heroassociation.domain.Manager;

public record AccountResponse(
        UUID id,
        String keycloakSubject,
        String email,
        boolean emailVerified,
        String status,
        Instant createdAt,
        Instant lastLoginAt,
        ManagerResponse manager,
        List<AgencyMembershipResponse> agencyMemberships) {

    public static AccountResponse from(Account account, Manager manager, List<AgencyMember> agencyMemberships) {
        return new AccountResponse(
                account.getId(),
                account.getKeycloakSubject(),
                account.getEmail(),
                account.isEmailVerified(),
                account.getStatus().name(),
                account.getCreatedAt(),
                account.getLastLoginAt(),
                manager == null ? null : new ManagerResponse(manager.getId(), manager.getDisplayName()),
                agencyMemberships.stream()
                        .map(AgencyMembershipResponse::from)
                        .toList());
    }

    public record ManagerResponse(UUID id, String displayName) {
    }

    public record AgencyMembershipResponse(UUID agencyId, String agencyName, String role) {

        private static AgencyMembershipResponse from(AgencyMember membership) {
            return new AgencyMembershipResponse(
                    membership.getAgency().getId(),
                    membership.getAgency().getName(),
                    membership.getRole().name());
        }
    }
}
