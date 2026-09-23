package io.tiagovibeson.heroassociation.application;

import java.util.UUID;

import io.tiagovibeson.heroassociation.application.exception.AgencyLeaderRequiredException;
import io.tiagovibeson.heroassociation.application.exception.AgencyMembershipRequiredException;
import io.tiagovibeson.heroassociation.application.exception.ManagerOnboardingRequiredException;
import io.tiagovibeson.heroassociation.domain.Account;
import io.tiagovibeson.heroassociation.domain.AgencyMember;
import io.tiagovibeson.heroassociation.domain.AgencyMemberRole;
import io.tiagovibeson.heroassociation.domain.Manager;
import io.tiagovibeson.heroassociation.repository.AccountRepository;
import io.tiagovibeson.heroassociation.repository.AgencyMemberRepository;
import io.tiagovibeson.heroassociation.repository.ManagerRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AgencyAccessService {

    @Inject
    AuthenticatedIdentityService authenticatedIdentityService;

    @Inject
    AccountRepository accountRepository;

    @Inject
    ManagerRepository managerRepository;

    @Inject
    AgencyMemberRepository agencyMemberRepository;

    public Manager currentManager() {
        AuthenticatedIdentity identity = authenticatedIdentityService.currentIdentity();
        Account account = accountRepository.findByKeycloakSubject(identity.keycloakSubject())
                .orElseThrow(ManagerOnboardingRequiredException::new);
        return managerRepository.findByAccountId(account.getId())
                .orElseThrow(ManagerOnboardingRequiredException::new);
    }

    public void requireMembership(UUID agencyId) {
        membership(agencyId);
    }

    public void requireLeadership(UUID agencyId) {
        if (membership(agencyId).getRole() != AgencyMemberRole.LEADER) {
            throw new AgencyLeaderRequiredException();
        }
    }

    private AgencyMember membership(UUID agencyId) {
        Manager manager = currentManager();
        return agencyMemberRepository.findByAgencyAndManagerId(agencyId, manager.getId())
                .orElseThrow(AgencyMembershipRequiredException::new);
    }
}
