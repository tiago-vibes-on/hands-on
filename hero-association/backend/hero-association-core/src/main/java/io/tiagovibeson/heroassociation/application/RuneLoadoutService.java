package io.tiagovibeson.heroassociation.application;

import java.util.UUID;

import io.tiagovibeson.heroassociation.api.v1.agency.AgencyStateResponse;
import io.tiagovibeson.heroassociation.application.exception.AgencyNotFoundException;
import io.tiagovibeson.heroassociation.application.exception.HeroNotFoundException;
import io.tiagovibeson.heroassociation.application.exception.InvalidRuneSlotException;
import io.tiagovibeson.heroassociation.application.exception.RuneNotAvailableException;
import io.tiagovibeson.heroassociation.domain.Agency;
import io.tiagovibeson.heroassociation.domain.AgencyRune;
import io.tiagovibeson.heroassociation.domain.Hero;
import io.tiagovibeson.heroassociation.domain.HeroRune;
import io.tiagovibeson.heroassociation.domain.Rune;
import io.tiagovibeson.heroassociation.repository.AgencyRepository;
import io.tiagovibeson.heroassociation.repository.AgencyRuneRepository;
import io.tiagovibeson.heroassociation.repository.HeroRepository;
import io.tiagovibeson.heroassociation.repository.HeroRuneRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class RuneLoadoutService {

    private static final int RUNE_SLOT_COUNT = 5;

    @Inject
    AgencyRepository agencyRepository;

    @Inject
    HeroRepository heroRepository;

    @Inject
    HeroRuneRepository heroRuneRepository;

    @Inject
    AgencyRuneRepository agencyRuneRepository;

    @Inject
    AgencyStateService agencyStateService;

    @Inject
    AgencyAccessService agencyAccessService;

    @Transactional
    public AgencyStateResponse equip(UUID agencyId, UUID heroId, int slotIndex, UUID runeId) {
        Agency agency = findAgency(agencyId);
        agencyAccessService.requireMembership(agencyId);
        Hero hero = findHero(agencyId, heroId);
        validateSlot(slotIndex);
        AgencyRune agencyRune = findAvailableRune(agencyId, runeId);
        HeroRune equippedRune = findEquippedRune(heroId, slotIndex);

        agencyRune.decreaseQuantity();
        if (equippedRune == null) {
            heroRuneRepository.persist(new HeroRune(hero, agencyRune.getRune(), slotIndex));
        } else {
            Rune previousRune = equippedRune.getRune();
            equippedRune.replaceRune(agencyRune.getRune());
            returnRuneToInventory(agency, previousRune);
        }

        return agencyStateService.findState(agencyId);
    }

    @Transactional
    public AgencyStateResponse unequip(UUID agencyId, UUID heroId, int slotIndex) {
        Agency agency = findAgency(agencyId);
        agencyAccessService.requireMembership(agencyId);
        findHero(agencyId, heroId);
        validateSlot(slotIndex);
        HeroRune equippedRune = findEquippedRune(heroId, slotIndex);

        if (equippedRune != null) {
            returnRuneToInventory(agency, equippedRune.getRune());
            heroRuneRepository.delete(equippedRune);
        }

        return agencyStateService.findState(agencyId);
    }

    private Agency findAgency(UUID agencyId) {
        return agencyRepository.findByIdOptional(agencyId)
                .orElseThrow(() -> new AgencyNotFoundException(agencyId));
    }

    private Hero findHero(UUID agencyId, UUID heroId) {
        return heroRepository.find("id = ?1 and agency.id = ?2", heroId, agencyId)
                .firstResultOptional()
                .orElseThrow(() -> new HeroNotFoundException(heroId));
    }

    private void validateSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= RUNE_SLOT_COUNT) {
            throw new InvalidRuneSlotException(slotIndex);
        }
    }

    private AgencyRune findAvailableRune(UUID agencyId, UUID runeId) {
        AgencyRune agencyRune = agencyRuneRepository.find("agency.id = ?1 and rune.id = ?2", agencyId, runeId)
                .firstResult();
        if (agencyRune == null || agencyRune.getQuantity() == 0) {
            throw new RuneNotAvailableException(runeId);
        }

        return agencyRune;
    }

    private HeroRune findEquippedRune(UUID heroId, int slotIndex) {
        return heroRuneRepository.find("hero.id = ?1 and slotIndex = ?2", heroId, slotIndex)
                .firstResult();
    }

    private void returnRuneToInventory(Agency agency, Rune rune) {
        AgencyRune agencyRune = agencyRuneRepository.find("agency.id = ?1 and rune.id = ?2", agency.getId(), rune.getId())
                .firstResult();
        if (agencyRune == null) {
            agencyRuneRepository.persist(new AgencyRune(agency, rune, 1));
        } else {
            agencyRune.increaseQuantity();
        }
    }
}
