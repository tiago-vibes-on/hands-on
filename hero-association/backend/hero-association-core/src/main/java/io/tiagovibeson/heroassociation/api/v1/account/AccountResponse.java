package io.tiagovibeson.heroassociation.api.v1.account;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.tiagovibeson.heroassociation.domain.Account;
import io.tiagovibeson.heroassociation.domain.AgencyMember;
import io.tiagovibeson.heroassociation.domain.Hero;
import io.tiagovibeson.heroassociation.domain.HeroSkill;
import io.tiagovibeson.heroassociation.domain.Manager;
import io.tiagovibeson.heroassociation.domain.ManagerItem;
import io.tiagovibeson.heroassociation.domain.ManagerRune;

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

    public static AccountResponse from(
            Account account,
            Manager manager,
            List<AgencyMember> agencyMemberships,
            List<Hero> personalHeroes,
            List<ManagerItem> personalItems,
            List<ManagerRune> personalRunes) {
        return new AccountResponse(
                account.getId(),
                account.getKeycloakSubject(),
                account.getEmail(),
                account.isEmailVerified(),
                account.getStatus().name(),
                account.getCreatedAt(),
                account.getLastLoginAt(),
                manager == null ? null : new ManagerResponse(
                        manager.getId(),
                        manager.getDisplayName(),
                        manager.getGold(),
                        personalHeroes.stream().map(PersonalHeroResponse::from).toList(),
                        personalItems.stream()
                                .map(entry -> new PersonalInventoryResponse(
                                        entry.getItem().getId(), entry.getItem().getCode(), entry.getQuantity()))
                                .toList(),
                        personalRunes.stream()
                                .map(entry -> new PersonalInventoryResponse(
                                        entry.getRune().getId(), entry.getRune().getCode(), entry.getQuantity()))
                                .toList()),
                agencyMemberships.stream()
                        .map(AgencyMembershipResponse::from)
                        .toList());
    }

    public record ManagerResponse(
            UUID id,
            String displayName,
            long gold,
            List<PersonalHeroResponse> heroes,
            List<PersonalInventoryResponse> items,
            List<PersonalInventoryResponse> runes) {
    }

    public record PersonalHeroResponse(
            UUID id, String name, String alias, String heroClass, int level,
            int meleeLevel, int distanceLevel, int magicLevel, int shieldLevel,
            int health, int mana, int stamina) {

        private static PersonalHeroResponse from(Hero hero) {
            return new PersonalHeroResponse(
                    hero.getId(), hero.getName(), hero.getAlias(), hero.getHeroClass().name(),
                    hero.getLevel(), hero.getSkillLevel(HeroSkill.MELEE),
                    hero.getSkillLevel(HeroSkill.DISTANCE), hero.getSkillLevel(HeroSkill.MAGIC),
                    hero.getSkillLevel(HeroSkill.SHIELD),
                    hero.getCurrentHealth(), hero.getCurrentMana(), hero.getStamina());
        }
    }

    public record PersonalInventoryResponse(UUID id, String code, int quantity) {
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
