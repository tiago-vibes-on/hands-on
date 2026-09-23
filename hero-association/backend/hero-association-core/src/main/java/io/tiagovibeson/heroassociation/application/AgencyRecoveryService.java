package io.tiagovibeson.heroassociation.application;

import java.time.Instant;

import io.tiagovibeson.heroassociation.repository.HeroRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AgencyRecoveryService {

    @Inject
    HeroRepository heroRepository;

    @Transactional
    public void recoverAt(Instant synchronizedAt) {
        heroRepository.listRecoveringForUpdate()
                .forEach(hero -> hero.recoverAgencyResourcesAt(synchronizedAt));
    }
}
