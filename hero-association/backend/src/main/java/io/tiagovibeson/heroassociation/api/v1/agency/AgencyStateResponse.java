package io.tiagovibeson.heroassociation.api.v1.agency;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.tiagovibeson.heroassociation.domain.Agency;
import io.tiagovibeson.heroassociation.domain.AgencyRune;
import io.tiagovibeson.heroassociation.domain.Hero;
import io.tiagovibeson.heroassociation.domain.HeroRune;
import io.tiagovibeson.heroassociation.domain.Party;
import io.tiagovibeson.heroassociation.domain.Quest;
import io.tiagovibeson.heroassociation.domain.QuestCombat;
import io.tiagovibeson.heroassociation.domain.QuestCombatant;
import io.tiagovibeson.heroassociation.domain.Rune;

public record AgencyStateResponse(
        AgencyResponse agency,
        List<PartyResponse> parties,
        List<QuestResponse> quests,
        List<HeroResponse> heroes,
        List<InventoryRuneResponse> runeInventory) {

    public static AgencyStateResponse from(
            Agency agency,
            List<Hero> heroes,
            List<Party> parties,
            List<Quest> quests,
            List<AgencyRune> runeInventory) {
        Map<UUID, List<UUID>> heroIdsByParty = heroes.stream()
                .filter(hero -> hero.getParty() != null)
                .collect(Collectors.groupingBy(
                        hero -> hero.getParty().getId(),
                        Collectors.mapping(Hero::getId, Collectors.toList())));

        return new AgencyStateResponse(
                AgencyResponse.from(agency),
                parties.stream().map(party -> PartyResponse.from(party, heroIdsByParty.getOrDefault(party.getId(), List.of()))).toList(),
                quests.stream().map(QuestResponse::from).toList(),
                heroes.stream().map(HeroResponse::from).toList(),
                runeInventory.stream().map(InventoryRuneResponse::from).toList());
    }

    public record AgencyResponse(
            UUID id,
            String name,
            String leaderName,
            long gold,
            int reputation,
            AgencyLevelsResponse levels) {

        private static AgencyResponse from(Agency agency) {
            return new AgencyResponse(
                    agency.getId(),
                    agency.getName(),
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

    public record PartyResponse(UUID id, String name, QuestResponse quest, List<UUID> heroIds) {

        private static PartyResponse from(Party party, List<UUID> heroIds) {
            return new PartyResponse(party.getId(), party.getName(), QuestResponse.from(party.getQuest()), heroIds);
        }
    }

    public record QuestResponse(
            UUID id,
            String title,
            String description,
            String status,
            String creatureName,
            int creaturesDefeated,
            int creaturesRequired,
            int minimumHeroes,
            int maximumHeroes,
            int durationMinutes,
            long goldReward,
            Instant startedAt,
            Instant expectedCompletionAt,
            UUID partyId,
            QuestCombatResponse combat) {

        private static QuestResponse from(Quest quest) {
            if (quest == null) {
                return null;
            }

            return new QuestResponse(
                    quest.getId(),
                    quest.getTitle(),
                    quest.getDescription(),
                    quest.getStatus().name(),
                    quest.getCreatureName(),
                    quest.getCreaturesDefeated(),
                    quest.getCreaturesRequired(),
                    quest.getMinimumHeroes(),
                    quest.getMaximumHeroes(),
                    quest.getDurationMinutes(),
                    quest.getGoldReward(),
                    quest.getStartedAt(),
                    quest.getExpectedCompletionAt(),
                    quest.getParty() == null ? null : quest.getParty().getId(),
                    QuestCombatResponse.from(quest.getCombat()));
        }
    }

    public record QuestCombatResponse(
            String status,
            long currentTimeMilliseconds,
            List<CombatantResponse> combatants) {

        private static QuestCombatResponse from(QuestCombat combat) {
            if (combat == null) {
                return null;
            }
            return new QuestCombatResponse(
                    combat.getStatus().name(),
                    combat.getCurrentTimeMilliseconds(),
                    combat.getCombatants().stream().map(CombatantResponse::from).toList());
        }
    }

    public record CombatantResponse(
            UUID id,
            UUID heroId,
            String team,
            int formationIndex,
            String name,
            String heroClass,
            int magicLevel,
            int maxHealth,
            int currentHealth,
            int maxMana,
            int currentMana,
            int attackDamage,
            long attackIntervalMilliseconds,
            int healthRecoveryPerSecond,
            int manaRecoveryPerSecond,
            double criticalChance,
            double criticalDamageMultiplier,
            long nextBasicAttackAt,
            Long fireBallNextCastAt,
            Long lightningRailNextCastAt) {

        private static CombatantResponse from(QuestCombatant combatant) {
            return new CombatantResponse(
                    combatant.getId(),
                    combatant.getHero() == null ? null : combatant.getHero().getId(),
                    combatant.getTeam().name(),
                    combatant.getFormationIndex(),
                    combatant.getName(),
                    combatant.getHeroClass() == null ? null : combatant.getHeroClass().name(),
                    combatant.getMagicLevel(),
                    combatant.getMaxHealth(),
                    combatant.getCurrentHealth(),
                    combatant.getMaxMana(),
                    combatant.getCurrentMana(),
                    combatant.getAttackDamage(),
                    combatant.getAttackIntervalMilliseconds(),
                    combatant.getHealthRecoveryPerSecond(),
                    combatant.getManaRecoveryPerSecond(),
                    combatant.getCriticalChance(),
                    combatant.getCriticalDamageMultiplier(),
                    combatant.getNextBasicAttackAt(),
                    combatant.getFireBallNextCastAt(),
                    combatant.getLightningRailNextCastAt());
        }
    }

    public record HeroResponse(
            UUID id,
            String name,
            String alias,
            String heroClass,
            int level,
            int magicLevel,
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

        private static HeroResponse from(Hero hero) {
            Map<Integer, Rune> runeBySlot = hero.getRuneSlots().stream()
                    .collect(Collectors.toMap(HeroRune::getSlotIndex, HeroRune::getRune));
            return new HeroResponse(
                    hero.getId(),
                    hero.getName(),
                    hero.getAlias(),
                    hero.getHeroClass().name(),
                    hero.getLevel(),
                    hero.getMagicLevel(),
                    hero.getCurrentHealth(),
                    hero.getHeroClass().getBaseHealth(),
                    hero.getCurrentMana(),
                    hero.getHeroClass().getBaseMana(),
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
}
