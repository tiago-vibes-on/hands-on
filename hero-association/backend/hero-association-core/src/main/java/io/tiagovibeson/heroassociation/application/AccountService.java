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
import io.tiagovibeson.heroassociation.domain.Hero;
import io.tiagovibeson.heroassociation.domain.HeroClass;
import io.tiagovibeson.heroassociation.domain.Manager;
import io.tiagovibeson.heroassociation.domain.Party;
import io.tiagovibeson.heroassociation.repository.AccountRepository;
import io.tiagovibeson.heroassociation.repository.AgencyMemberRepository;
import io.tiagovibeson.heroassociation.repository.HeroRepository;
import io.tiagovibeson.heroassociation.repository.ManagerRepository;
import io.tiagovibeson.heroassociation.repository.PartyRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AccountService {

    @Inject io.tiagovibeson.heroassociation.application.assets.AssetsClient assets;

    @Inject
    AccountRepository accountRepository;

    @Inject
    ManagerRepository managerRepository;

    @Inject
    PartyRepository partyRepository;

    @Inject
    AgencyMemberRepository agencyMemberRepository;

    @Inject
    HeroRepository heroRepository;

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
        Party defaultParty = new Party(null, manager, "Main Party");
        partyRepository.persist(defaultParty);
        for (HeroClass heroClass : HeroClass.values()) {
            String className = heroClass.name().toLowerCase(Locale.ROOT);
            String alias = "starter-" + manager.getId().toString().substring(24) + "-" + className;
            Hero hero = Hero.createPersonal(
                    "Starter " + className.substring(0, 1).toUpperCase(Locale.ROOT) + className.substring(1),
                    alias,
                    heroClass,
                    manager);
            hero.assignToParty(defaultParty);
            heroRepository.persist(hero);
        }
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
        if (manager == null) return AccountResponse.from(account, null, java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of());
        var heroes = heroRepository.listByManagerId(manager.getId());
        var snapshot = assets.snapshot(java.util.List.of(new io.tiagovibeson.heroassociation.application.assets.AssetsClient.OwnerRequest("MANAGER", manager.getId())),
                heroes.stream().map(Hero::getId).toList());
        var owner = snapshot.owner(manager.getId());
        manager.projectGold(owner.gold()); assets.decorate(heroes, snapshot.heroes());
        return AccountResponse.from(account, manager, agencyMemberRepository.listByManagerId(manager.getId()), heroes,
                owner.items().stream().map(entry -> new io.tiagovibeson.heroassociation.domain.ManagerItem(manager, entry.item().value(), entry.quantity())).toList(),
                owner.runes().stream().map(entry -> new io.tiagovibeson.heroassociation.domain.ManagerRune(manager, entry.rune().value(), entry.quantity())).toList());
    }
}
