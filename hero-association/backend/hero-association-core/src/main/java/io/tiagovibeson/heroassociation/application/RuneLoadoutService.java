package io.tiagovibeson.heroassociation.application;

import java.util.UUID;

import io.tiagovibeson.heroassociation.api.v1.agency.AgencyStateResponse;
import io.tiagovibeson.heroassociation.application.exception.AgencyNotFoundException;
import io.tiagovibeson.heroassociation.application.exception.HeroNotFoundException;
import io.tiagovibeson.heroassociation.application.exception.HeroOnQuestException;
import io.tiagovibeson.heroassociation.application.exception.InvalidRuneSlotException;
import io.tiagovibeson.heroassociation.application.exception.RuneNotAvailableException;
import io.tiagovibeson.heroassociation.domain.Agency;
import io.tiagovibeson.heroassociation.domain.AgencyRune;
import io.tiagovibeson.heroassociation.domain.Hero;
import io.tiagovibeson.heroassociation.domain.HeroActivity;
import io.tiagovibeson.heroassociation.domain.HeroRune;
import io.tiagovibeson.heroassociation.domain.Manager;
import io.tiagovibeson.heroassociation.domain.ManagerRune;
import io.tiagovibeson.heroassociation.domain.Rune;
import io.tiagovibeson.heroassociation.domain.RuneInventoryOwnerType;
import io.tiagovibeson.heroassociation.repository.AgencyRepository;
import io.tiagovibeson.heroassociation.repository.AgencyRuneRepository;
import io.tiagovibeson.heroassociation.repository.HeroRepository;
import io.tiagovibeson.heroassociation.repository.HeroRuneRepository;
import io.tiagovibeson.heroassociation.repository.ManagerRepository;
import io.tiagovibeson.heroassociation.repository.ManagerRuneRepository;
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
    ManagerRepository managerRepository;

    @Inject
    ManagerRuneRepository managerRuneRepository;

    @Inject
    AgencyStateService agencyStateService;

    @Inject
    AgencyAccessService agencyAccessService;

    @Transactional
    public AgencyStateResponse equip(UUID agencyId, UUID heroId, int slotIndex, UUID runeId,
            RuneInventoryOwnerType sourceOwnerType) {
        agencyAccessService.requireMembership(agencyId);
        Manager manager = lockCurrentManager();
        lockAgency(agencyId);
        Hero hero = findHero(agencyId, heroId, manager.getId());
        validateHeroAvailableForLoadout(hero);
        validateSlot(slotIndex);
        HeroRune equippedRune = findEquippedRune(heroId, slotIndex);
        if (equippedRune != null && equippedRune.getRune().getId().equals(runeId)) {
            return agencyStateService.findState(agencyId);
        }
        Rune rune = takeRune(agencyId, manager.getId(), runeId, sourceOwnerType);

        if (equippedRune == null) {
            heroRuneRepository.persist(new HeroRune(hero, rune, slotIndex));
        } else {
            Rune previousRune = equippedRune.getRune();
            equippedRune.replaceRune(rune);
            returnRuneToManager(manager, previousRune);
        }

        return agencyStateService.findState(agencyId);
    }

    @Transactional
    public AgencyStateResponse unequip(UUID agencyId, UUID heroId, int slotIndex) {
        agencyAccessService.requireMembership(agencyId);
        Manager manager = lockCurrentManager();
        lockAgency(agencyId);
        Hero hero = findHero(agencyId, heroId, manager.getId());
        validateHeroAvailableForLoadout(hero);
        validateSlot(slotIndex);
        HeroRune equippedRune = findEquippedRune(heroId, slotIndex);

        if (equippedRune != null) {
            returnRuneToManager(manager, equippedRune.getRune());
            heroRuneRepository.delete(equippedRune);
        }

        return agencyStateService.findState(agencyId);
    }

    private Agency lockAgency(UUID agencyId) {
        return agencyRepository.findForUpdate(agencyId)
                .orElseThrow(() -> new AgencyNotFoundException(agencyId));
    }

    private Manager lockCurrentManager() {
        Manager current = agencyAccessService.currentManager();
        return managerRepository.findForUpdate(current.getId()).orElseThrow();
    }

    private Hero findHero(UUID agencyId, UUID heroId, UUID managerId) {
        Hero hero = heroRepository.findForUpdate(heroId)
                .orElseThrow(() -> new HeroNotFoundException(heroId));
        boolean agencyHero = hero.getAgency() != null && hero.getAgency().getId().equals(agencyId);
        boolean personalHero = hero.getOwnerManager() != null && hero.getOwnerManager().getId().equals(managerId);
        if (!agencyHero && !personalHero) {
            throw new HeroNotFoundException(heroId);
        }
        return hero;
    }

    private void validateHeroAvailableForLoadout(Hero hero) {
        if (hero.getActivity() == HeroActivity.ON_QUEST || hero.getActivity() == HeroActivity.ON_EXPEDITION) {
            throw new HeroOnQuestException(hero.getId());
        }
    }

    private void validateSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= RUNE_SLOT_COUNT) {
            throw new InvalidRuneSlotException(slotIndex);
        }
    }

    private Rune takeRune(UUID agencyId, UUID managerId, UUID runeId, RuneInventoryOwnerType sourceOwnerType) {
        if (sourceOwnerType == RuneInventoryOwnerType.AGENCY) {
            AgencyRune agencyRune = agencyRuneRepository.find("agency.id = ?1 and rune.id = ?2", agencyId, runeId)
                    .firstResult();
            if (agencyRune == null || agencyRune.getQuantity() == 0) {
                throw new RuneNotAvailableException(runeId, "agency");
            }
            agencyRune.decreaseQuantity();
            return agencyRune.getRune();
        }
        ManagerRune managerRune = managerRuneRepository.find("manager.id = ?1 and rune.id = ?2", managerId, runeId)
                .firstResult();
        if (managerRune == null || managerRune.getQuantity() == 0) {
            throw new RuneNotAvailableException(runeId, "manager");
        }
        managerRune.decreaseQuantity(1);
        return managerRune.getRune();
    }

    private HeroRune findEquippedRune(UUID heroId, int slotIndex) {
        return heroRuneRepository.find("hero.id = ?1 and slotIndex = ?2", heroId, slotIndex)
                .firstResult();
    }

    private void returnRuneToManager(Manager manager, Rune rune) {
        ManagerRune managerRune = managerRuneRepository.find("manager.id = ?1 and rune.id = ?2", manager.getId(), rune.getId())
                .firstResult();
        if (managerRune == null) {
            managerRuneRepository.persist(new ManagerRune(manager, rune, 1));
        } else {
            managerRune.increaseQuantity(1);
        }
    }
}
