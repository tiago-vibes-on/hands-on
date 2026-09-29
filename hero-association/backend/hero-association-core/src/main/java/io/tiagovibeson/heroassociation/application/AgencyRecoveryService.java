package io.tiagovibeson.heroassociation.application;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import io.tiagovibeson.heroassociation.domain.Hero;
import io.tiagovibeson.heroassociation.repository.AgencyMemberRepository;
import io.tiagovibeson.heroassociation.repository.HeroRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AgencyRecoveryService {

    @Inject
    HeroRepository heroRepository;

    @Inject
    AgencyMemberRepository agencyMemberRepository;

    @Transactional
    public void recoverAt(Instant synchronizedAt) {
        var heroes = heroRepository.listRecoveringForUpdate();
        Set<UUID> managerIds = heroes.stream()
                .map(Hero::getOwnerManager)
                .filter(manager -> manager != null)
                .map(manager -> manager.getId())
                .collect(Collectors.toSet());
        Map<UUID, Integer> personalRestLevels = managerIds.isEmpty()
                ? Map.of()
                : agencyMemberRepository.listByManagerIds(managerIds).stream()
                        .collect(Collectors.toMap(member -> member.getManager().getId(),
                                member -> member.getAgency().getRestLevel()));
        heroes.forEach(hero -> {
            int restLevel = hero.getAgency() != null
                    ? hero.getAgency().getRestLevel()
                    : hero.getOwnerManager() == null ? 1
                            : personalRestLevels.getOrDefault(hero.getOwnerManager().getId(), 1);
            hero.recoverAgencyResourcesAt(synchronizedAt, restLevel);
        });
    }
}
