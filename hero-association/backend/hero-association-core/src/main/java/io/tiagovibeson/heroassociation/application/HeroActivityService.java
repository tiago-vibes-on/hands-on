package io.tiagovibeson.heroassociation.application;

import java.util.UUID;

import io.tiagovibeson.heroassociation.api.v1.agency.AgencyStateResponse;
import io.tiagovibeson.heroassociation.application.exception.AgencyNotFoundException;
import io.tiagovibeson.heroassociation.application.exception.HeroNotFoundException;
import io.tiagovibeson.heroassociation.application.exception.HeroOnQuestException;
import io.tiagovibeson.heroassociation.application.exception.InvalidHeroActivityException;
import io.tiagovibeson.heroassociation.domain.Agency;
import io.tiagovibeson.heroassociation.domain.Hero;
import io.tiagovibeson.heroassociation.domain.HeroActivity;
import io.tiagovibeson.heroassociation.repository.AgencyRepository;
import io.tiagovibeson.heroassociation.repository.HeroRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class HeroActivityService {

    @Inject
    AgencyRepository agencyRepository;

    @Inject
    HeroRepository heroRepository;

    @Inject
    AgencyStateService agencyStateService;

    @Inject
    AgencyAccessService agencyAccessService;

    @Transactional
    public AgencyStateResponse changeActivity(UUID agencyId, UUID heroId, HeroActivity activity) {
        Agency agency = agencyRepository.findByIdOptional(agencyId)
                .orElseThrow(() -> new AgencyNotFoundException(agencyId));
        agencyAccessService.requireMembership(agencyId);

        Hero hero = heroRepository.find("id = ?1 and agency.id = ?2", heroId, agencyId)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResultOptional()
                .orElseThrow(() -> new HeroNotFoundException(heroId));
        if (hero.getParty() != null || hero.getActivity() == HeroActivity.ON_QUEST || hero.getActivity() == HeroActivity.ON_EXPEDITION) {
            throw new HeroOnQuestException(heroId);
        }
        if (activity == null || activity == HeroActivity.ON_QUEST || activity == HeroActivity.ON_EXPEDITION) {
            throw new InvalidHeroActivityException();
        }

        hero.changeActivity(activity, agency.getRestLevel());
        return agencyStateService.findState(agencyId);
    }
}
