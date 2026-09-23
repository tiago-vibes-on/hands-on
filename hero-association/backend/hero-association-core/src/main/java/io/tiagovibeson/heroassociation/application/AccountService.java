package io.tiagovibeson.heroassociation.application;

import java.util.Locale;

import io.tiagovibeson.heroassociation.api.v1.account.AccountResponse;
import io.tiagovibeson.heroassociation.application.exception.AccountInactiveException;
import io.tiagovibeson.heroassociation.application.exception.InvalidManagerNameException;
import io.tiagovibeson.heroassociation.application.exception.ManagerAlreadyExistsException;
import io.tiagovibeson.heroassociation.application.exception.ManagerNameAlreadyUsedException;
import io.tiagovibeson.heroassociation.domain.Account;
import io.tiagovibeson.heroassociation.domain.AccountStatus;
import io.tiagovibeson.heroassociation.domain.AgencyMember;
import io.tiagovibeson.heroassociation.domain.Manager;
import io.tiagovibeson.heroassociation.repository.AccountRepository;
import io.tiagovibeson.heroassociation.repository.AgencyMemberRepository;
import io.tiagovibeson.heroassociation.repository.ManagerRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AccountService {

    @Inject
    AccountRepository accountRepository;

    @Inject
    ManagerRepository managerRepository;

    @Inject
    AgencyMemberRepository agencyMemberRepository;

    @Transactional
    public AccountResponse currentAccount(AuthenticatedIdentity identity) {
        Account account = provisionAccount(identity);
        return responseFor(account, managerRepository.findByAccountId(account.getId()).orElse(null));
    }

    @Transactional
    public AccountResponse createManager(AuthenticatedIdentity identity, String requestedDisplayName) {
        Account account = provisionAccount(identity);
        if (managerRepository.findByAccountId(account.getId()).isPresent()) {
            throw new ManagerAlreadyExistsException();
        }

        String displayName = normalizedDisplayName(requestedDisplayName);
        String displayNameNormalized = displayName.toLowerCase(Locale.ROOT);
        if (managerRepository.existsWithNormalizedDisplayName(displayNameNormalized)) {
            throw new ManagerNameAlreadyUsedException(displayName);
        }

        Manager manager = new Manager(account, displayName, displayNameNormalized);
        managerRepository.persist(manager);
        return responseFor(account, manager);
    }

    private Account provisionAccount(AuthenticatedIdentity identity) {
        Account account = accountRepository.findByKeycloakSubject(identity.keycloakSubject())
                .orElseGet(() -> {
                    Account createdAccount = new Account(
                            identity.keycloakSubject(),
                            identity.email(),
                            identity.emailVerified());
                    accountRepository.persist(createdAccount);
                    return createdAccount;
                });
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountInactiveException();
        }
        account.registerLogin(identity.email(), identity.emailVerified());
        return account;
    }

    private String normalizedDisplayName(String requestedDisplayName) {
        if (requestedDisplayName == null) {
            throw new InvalidManagerNameException();
        }
        String displayName = requestedDisplayName.trim();
        if (displayName.length() < 3 || displayName.length() > 100) {
            throw new InvalidManagerNameException();
        }
        return displayName;
    }

    private AccountResponse responseFor(Account account, Manager manager) {
        return AccountResponse.from(
                account,
                manager,
                manager == null ? java.util.List.of() : agencyMemberRepository.listByManagerId(manager.getId()));
    }
}
