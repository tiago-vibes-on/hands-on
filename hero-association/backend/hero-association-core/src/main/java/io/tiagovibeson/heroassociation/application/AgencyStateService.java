package io.tiagovibeson.heroassociation.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.tiagovibeson.heroassociation.api.v1.agency.AgencyStateResponse;
import io.tiagovibeson.heroassociation.application.exception.AgencyNotFoundException;
import io.tiagovibeson.heroassociation.domain.Agency;
import io.tiagovibeson.heroassociation.domain.AgencyItem;
import io.tiagovibeson.heroassociation.domain.AgencyRune;
import io.tiagovibeson.heroassociation.domain.FeedPost;
import io.tiagovibeson.heroassociation.domain.Hero;
import io.tiagovibeson.heroassociation.domain.Manager;
import io.tiagovibeson.heroassociation.domain.Party;
import io.tiagovibeson.heroassociation.domain.Quest;
import io.tiagovibeson.heroassociation.repository.AgencyRepository;
import io.tiagovibeson.heroassociation.repository.FeedPostRepository;
import io.tiagovibeson.heroassociation.repository.HeroRepository;
import io.tiagovibeson.heroassociation.repository.PartyRepository;
import io.tiagovibeson.heroassociation.repository.QuestRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AgencyStateService {

    @Inject io.tiagovibeson.heroassociation.application.assets.AssetsClient assets;

    @Inject
    AgencyRepository agencyRepository;

    @Inject
    HeroRepository heroRepository;

    @Inject
    PartyRepository partyRepository;

    @Inject
    QuestRepository questRepository;

    @Inject
    FeedPostRepository feedPostRepository;

    @Inject
    AgencyAccessService agencyAccessService;

    @Transactional
    public AgencyStateResponse findState(UUID agencyId) {
        Agency agency = agencyRepository.findByIdOptional(agencyId)
                .orElseThrow(() -> new AgencyNotFoundException(agencyId));
        agencyAccessService.requireMembership(agencyId);
        Manager manager = agencyAccessService.currentManager();
        List<Hero> heroes = heroRepository.list("agency.id = ?1 order by id", agencyId);
        Map<UUID, Hero> personalHeroesById = new LinkedHashMap<>();
        for (Hero hero : heroRepository.listByManagerId(manager.getId())) {
            personalHeroesById.put(hero.getId(), hero);
        }
        for (Hero hero : heroRepository.list(
                "ownerManager is not null and party.agency.id = ?1 order by id", agencyId)) {
            personalHeroesById.put(hero.getId(), hero);
        }
        List<Hero> personalHeroes = new ArrayList<>(personalHeroesById.values());
        personalHeroes.sort(Comparator.comparing(Hero::getId));
        List<Party> parties = partyRepository.list("agency.id = ?1 order by id", agencyId);
        List<Quest> quests = questRepository.list("agency.id = ?1 order by id", agencyId);
        var allHeroes = java.util.stream.Stream.concat(heroes.stream(), personalHeroes.stream()).toList();
        var snapshot = assets.snapshot(java.util.List.of(
                new io.tiagovibeson.heroassociation.application.assets.AssetsClient.OwnerRequest("AGENCY", agencyId),
                new io.tiagovibeson.heroassociation.application.assets.AssetsClient.OwnerRequest("MANAGER", manager.getId())),
                allHeroes.stream().map(Hero::getId).distinct().toList());
        assets.decorate(allHeroes, snapshot.heroes());
        var agencyAssets = snapshot.owner(agencyId);
        var managerAssets = snapshot.owner(manager.getId());
        agency.projectGold(agencyAssets.gold()); manager.projectGold(managerAssets.gold());
        List<AgencyRune> runeInventory = agencyAssets.runes().stream().map(entry -> new AgencyRune(agency, entry.rune().value(), entry.quantity())).toList();
        var personalRuneInventory = managerAssets.runes().stream().map(entry -> new io.tiagovibeson.heroassociation.domain.ManagerRune(manager, entry.rune().value(), entry.quantity())).toList();
        List<AgencyItem> itemInventory = agencyAssets.items().stream().map(entry -> new AgencyItem(agency, entry.item().value(), entry.quantity())).toList();
        List<FeedPost> feedPosts = feedPostRepository.list("agency.id = ?1 order by publishedAt desc, id desc", agencyId);
        return AgencyStateResponse.from(
                agency, heroes, personalHeroes, parties, quests, runeInventory, personalRuneInventory, itemInventory, feedPosts);
    }
}
