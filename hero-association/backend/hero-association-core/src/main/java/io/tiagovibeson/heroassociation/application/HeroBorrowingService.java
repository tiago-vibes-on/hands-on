package io.tiagovibeson.heroassociation.application;

import java.util.UUID;

import io.tiagovibeson.heroassociation.api.v1.agency.AgencyStateResponse;
import io.tiagovibeson.heroassociation.application.exception.AgencyNotFoundException;
import io.tiagovibeson.heroassociation.application.exception.HeroNotFoundException;
import io.tiagovibeson.heroassociation.domain.Hero;
import io.tiagovibeson.heroassociation.repository.AgencyRepository;
import io.tiagovibeson.heroassociation.repository.HeroRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class HeroBorrowingService {

    @Inject
    AgencyRepository agencyRepository;

    @Inject
    AgencyAccessService agencyAccessService;

    @Inject
    HeroRepository heroRepository;

    @Inject
    AgencyStateService agencyStateService;

    @Transactional
    public AgencyStateResponse setFee(UUID agencyId, UUID heroId, long feeGold) {
        agencyRepository.findByIdOptional(agencyId)
                .orElseThrow(() -> new AgencyNotFoundException(agencyId));
        agencyAccessService.requireLeadership(agencyId);
        Hero hero = heroRepository.findForUpdate(heroId)
                .filter(candidate -> candidate.getAgency() != null
                        && candidate.getAgency().getId().equals(agencyId))
                .orElseThrow(() -> new HeroNotFoundException(heroId));
        hero.setBorrowingFeeGold(feeGold);
        return agencyStateService.findState(agencyId);
    }
}
