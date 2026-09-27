package io.tiagovibeson.heroassociation.application;

import java.util.List;
import java.util.UUID;

import io.tiagovibeson.heroassociation.api.v1.agency.AgencyStateResponse;
import io.tiagovibeson.heroassociation.application.exception.AgencyNotFoundException;
import io.tiagovibeson.heroassociation.application.exception.HeroNotFoundException;
import io.tiagovibeson.heroassociation.application.exception.RecruitNotFoundException;
import io.tiagovibeson.heroassociation.application.exception.RecruitUnavailableException;
import io.tiagovibeson.heroassociation.domain.Agency;
import io.tiagovibeson.heroassociation.domain.Hero;
import io.tiagovibeson.heroassociation.repository.AgencyRepository;
import io.tiagovibeson.heroassociation.repository.HeroRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class HeroRecruitmentService {

    @Inject
    AgencyRepository agencyRepository;

    @Inject
    HeroRepository heroRepository;

    @Inject
    AgencyStateService agencyStateService;

    @Inject
    AgencyAccessService agencyAccessService;

    @Transactional
    public List<Hero> listRecruitable() {
        agencyAccessService.currentManager();
        return heroRepository.listRecruitable();
    }

    @Transactional
    public AgencyStateResponse recruit(UUID agencyId, UUID recruitId) {
        Agency agency = findAgency(agencyId);
        agencyAccessService.requireMembership(agencyId);

        Hero recruit = heroRepository.findForUpdate(recruitId)
                .orElseThrow(() -> new RecruitNotFoundException(recruitId));
        if (!recruit.isRecruitable()) {
            throw new RecruitUnavailableException(recruitId);
        }

        recruit.recruitTo(agency);
        return agencyStateService.findState(agencyId);
    }

    @Transactional
    public AgencyStateResponse.HeroResponse findDetail(UUID agencyId, UUID heroId) {
        findAgency(agencyId);
        agencyAccessService.requireMembership(agencyId);
        Hero hero = heroRepository.find("id = ?1 and agency.id = ?2", heroId, agencyId)
                .firstResultOptional()
                .orElseThrow(() -> new HeroNotFoundException(heroId));
        return AgencyStateResponse.HeroResponse.from(hero);
    }

    private Agency findAgency(UUID agencyId) {
        return agencyRepository.findByIdOptional(agencyId)
                .orElseThrow(() -> new AgencyNotFoundException(agencyId));
    }
}
