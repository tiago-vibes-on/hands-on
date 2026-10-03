package io.tiagovibeson.heroassociation.api.v1.agency;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.tiagovibeson.heroassociation.domain.Agency;
import io.tiagovibeson.heroassociation.domain.AgencyItem;
import io.tiagovibeson.heroassociation.domain.AgencyRune;
import io.tiagovibeson.heroassociation.domain.FeedPost;
import io.tiagovibeson.heroassociation.domain.Hero;
import io.tiagovibeson.heroassociation.domain.HeroRune;
import io.tiagovibeson.heroassociation.domain.HeroSkill;
import io.tiagovibeson.heroassociation.domain.Item;
import io.tiagovibeson.heroassociation.domain.ManagerRune;
import io.tiagovibeson.heroassociation.domain.Party;
import io.tiagovibeson.heroassociation.domain.Rune;

public record AgencyStateResponse(
        AgencyResponse agency,
        List<PartyResponse> parties,
        List<HeroResponse> heroes,
        List<HeroResponse> personalHeroes,
        List<InventoryRuneResponse> runeInventory,
        List<InventoryRuneResponse> personalRuneInventory,
        List<InventoryItemResponse> itemInventory,
        List<FeedPostResponse> feedPosts) {

    public static AgencyStateResponse from(
            Agency agency,
            List<Hero> heroes,
            List<Hero> personalHeroes,
            List<Party> parties,
            List<AgencyRune> runeInventory,
            List<ManagerRune> personalRuneInventory,
            List<AgencyItem> itemInventory,
            List<FeedPost> feedPosts) {
        Map<UUID, List<UUID>> heroIdsByParty = Stream.concat(heroes.stream(), personalHeroes.stream())
                .filter(hero -> hero.getParty() != null)
                .collect(Collectors.groupingBy(
                        hero -> hero.getParty().getId(),
                        Collectors.mapping(Hero::getId, Collectors.toList())));

        return new AgencyStateResponse(
                AgencyResponse.from(agency),
                parties.stream().map(party -> PartyResponse.from(party, heroIdsByParty.getOrDefault(party.getId(), List.of()))).toList(),
                heroes.stream().map(HeroResponse::from).toList(),
                personalHeroes.stream().map(HeroResponse::from).toList(),
                runeInventory.stream().map(InventoryRuneResponse::from).toList(),
                personalRuneInventory.stream().map(InventoryRuneResponse::from).toList(),
                itemInventory.stream().map(InventoryItemResponse::from).toList(),
                feedPosts.stream().map(FeedPostResponse::from).toList());
    }

    public record AgencyResponse(
            UUID id,
            String name,
            UUID leaderId,
            String leaderName,
            long gold,
            int reputation,
            AgencyLevelsResponse levels) {

        private static AgencyResponse from(Agency agency) {
            return new AgencyResponse(
                    agency.getId(),
                    agency.getName(),
                    agency.getLeader().getId(),
                    agency.getLeader().getDisplayName(),
                    agency.getGold(),
                    agency.getReputation(),
                    new AgencyLevelsResponse(
                            agency.getAgencyLevel(),
                            agency.getTrainingLevel(),
                            agency.getRestLevel(),
                            agency.getSizeLevel(),
                            agency.getReputationLevel(),
                            agency.getIntelligenceLevel()));
        }
    }

    public record AgencyLevelsResponse(
            int agency,
            int training,
            int rest,
            int size,
            int reputation,
            int intelligence) {
    }

    public record PartyResponse(UUID id, String name, UUID ownerManagerId, List<UUID> heroIds) {

        private static PartyResponse from(Party party, List<UUID> heroIds) {
            return new PartyResponse(
                    party.getId(), party.getName(), party.getOwnerManager().getId(),
                    heroIds);
        }
    }

    public record HeroResponse(
            UUID id,
            UUID ownerManagerId,
            long borrowingFeeGold,
            String name,
            String alias,
            String heroClass,
            int level,
            long experience,
            int meleeLevel,
            int distanceLevel,
            int magicLevel,
            int shieldLevel,
            int currentHealth,
            int maxHealth,
            int currentMana,
            int maxMana,
            int healthRecoveryPerSecond,
            int manaRecoveryPerSecond,
            int stamina,
            String activity,
            UUID partyId,
            List<RuneSlotResponse> runeSlots) {

        public static HeroResponse from(Hero hero) {
            Map<Integer, Rune> runeBySlot = hero.getRuneSlots().stream()
                    .collect(Collectors.toMap(HeroRune::getSlotIndex, HeroRune::getRune));
            return new HeroResponse(
                    hero.getId(),
                    hero.getOwnerManager() == null ? null : hero.getOwnerManager().getId(),
                    hero.getBorrowingFeeGold(),
                    hero.getName(),
                    hero.getAlias(),
                    hero.getHeroClass().name(),
                    hero.getLevel(),
                    hero.getExperience(),
                    hero.getSkillLevel(HeroSkill.MELEE),
                    hero.getSkillLevel(HeroSkill.DISTANCE),
                    hero.getMagicLevel(),
                    hero.getSkillLevel(HeroSkill.SHIELD),
                    hero.getCurrentHealth(),
                    hero.getMaxHealth(),
                    hero.getCurrentMana(),
                    hero.getMaxMana(),
                    hero.getHeroClass().getHealthRecoveryPerSecond(),
                    hero.getHeroClass().getManaRecoveryPerSecond(),
                    hero.getStamina(),
                    hero.getActivity().name(),
                    hero.getParty() == null ? null : hero.getParty().getId(),
                    IntStream.range(0, 5)
                            .mapToObj(slot -> RuneSlotResponse.from(slot, runeBySlot.get(slot)))
                            .toList());
        }
    }

    public record RuneSlotResponse(int slot, RuneResponse rune) {

        private static RuneSlotResponse from(int slot, Rune rune) {
            return new RuneSlotResponse(slot, rune == null ? null : RuneResponse.from(rune));
        }
    }

    public record InventoryRuneResponse(RuneResponse rune, int quantity) {

        private static InventoryRuneResponse from(AgencyRune agencyRune) {
            return new InventoryRuneResponse(RuneResponse.from(agencyRune.getRune()), agencyRune.getQuantity());
        }

        private static InventoryRuneResponse from(ManagerRune managerRune) {
            return new InventoryRuneResponse(RuneResponse.from(managerRune.getRune()), managerRune.getQuantity());
        }
    }

    public record InventoryItemResponse(ItemResponse item, int quantity) {

        private static InventoryItemResponse from(AgencyItem agencyItem) {
            return new InventoryItemResponse(ItemResponse.from(agencyItem.getItem()), agencyItem.getQuantity());
        }
    }

    public record FeedPostResponse(
            UUID id,
            String authorType,
            UUID authorId,
            String authorName,
            String content,
            ItemResponse item,
            Integer itemQuantity,
            Instant publishedAt) {

        private static FeedPostResponse from(FeedPost feedPost) {
            return new FeedPostResponse(
                    feedPost.getId(),
                    feedPost.getAuthorType().name(),
                    feedPost.getAuthorId(),
                    feedPost.getAuthorName(),
                    feedPost.getContent(),
                    feedPost.getItem() == null ? null : ItemResponse.from(feedPost.getItem()),
                    feedPost.getItemQuantity(),
                    feedPost.getPublishedAt());
        }
    }

    public record RuneResponse(
            UUID id,
            String code,
            String name,
            String symbol,
            String stats,
            String description,
            String effect,
            double effectValue) {

        private static RuneResponse from(Rune rune) {
            return new RuneResponse(
                    rune.getId(),
                    rune.getCode(),
                    rune.getName(),
                    rune.getSymbol(),
                    rune.getStats(),
                    rune.getDescription(),
                    rune.getEffect().name(),
                    rune.getEffectValue());
        }
    }

    public record ItemResponse(
            UUID id,
            String code,
            String name,
            String symbol,
            String description) {

        private static ItemResponse from(Item item) {
            return new ItemResponse(
                    item.getId(),
                    item.getCode(),
                    item.getName(),
                    item.getSymbol(),
                    item.getDescription());
        }
    }
}
