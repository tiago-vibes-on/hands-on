package io.tiagovibeson.heroassociation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.tiagovibeson.heroassociation.api.v1.agency.AgencyStateResponse;
import io.tiagovibeson.heroassociation.repository.CreatureDefinitionRepository;
import io.tiagovibeson.heroassociation.repository.HeroRepository;
import io.tiagovibeson.heroassociation.repository.QuestRepository;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

@QuarkusTest
class HeroProgressionPersistenceTest {

    private static final UUID TROLL_QUEST_ID = UUID.fromString("019c4c00-0003-7000-8000-000000000001");

    @Inject
    HeroRepository heroRepository;

    @Inject
    QuestRepository questRepository;

    @Inject
    CreatureDefinitionRepository creatureDefinitionRepository;

    @Inject
    EntityManager entityManager;

    @Test
    @TestTransaction
    void shouldPersistExperienceSkillsAndPreciseStamina() {
        Hero hero = Hero.createRecruitable("Progression Mage", "progression-mage-" + UuidV7.next(),
                HeroClass.MAGE);
        hero.addSkillPoints(HeroSkill.MAGIC, new BigDecimal("4058"));
        hero.consumeStaminaMilliseconds(HeroProgression.MAX_STAMINA_MILLISECONDS - 41_472_000);
        heroRepository.persist(hero);
        entityManager.flush();
        UUID heroId = hero.getId();
        entityManager.clear();

        Hero loaded = heroRepository.findById(heroId);
        assertEquals(15, loaded.getMagicLevel());
        assertEquals(24, loaded.getStamina());
        assertEquals(41_472_000, loaded.getStaminaMilliseconds());

        loaded.addExperience(100);
        loaded.addSkillPoints(HeroSkill.MELEE, new BigDecimal("0.050000"));
        loaded.addSkillPoints(HeroSkill.DISTANCE, new BigDecimal("0.250000"));
        loaded.addSkillPoints(HeroSkill.SHIELD, new BigDecimal("0.100000"));
        loaded.consumeStaminaMilliseconds(1);
        entityManager.flush();
        entityManager.clear();

        Hero reloaded = heroRepository.findById(heroId);
        assertEquals(100, reloaded.getExperience());
        assertEquals(2, reloaded.getLevel());
        assertEquals(115, reloaded.getMaxHealth());
        assertEquals(560, reloaded.getMaxMana());
        assertEquals(0, reloaded.getSkillPoints(HeroSkill.MELEE).compareTo(new BigDecimal("0.05")));
        assertEquals(0, reloaded.getSkillPoints(HeroSkill.DISTANCE).compareTo(new BigDecimal("0.25")));
        assertEquals(0, reloaded.getSkillPoints(HeroSkill.SHIELD).compareTo(new BigDecimal("0.10")));
        assertEquals(15, reloaded.getMagicLevel());
        assertEquals(41_471_999, reloaded.getStaminaMilliseconds());
        assertEquals(115, AgencyStateResponse.HeroResponse.from(reloaded).maxHealth());
        assertEquals(560, AgencyStateResponse.HeroResponse.from(reloaded).maxMana());

        QuestCombat combat = QuestCombat.start(questRepository.findById(TROLL_QUEST_ID), List.of(reloaded),
                CreatureCombatProfile.from(creatureDefinitionRepository.findLatestByName("Troll").orElseThrow()));
        QuestCombatant snapshot = combat.getCombatants().stream()
                .filter(combatant -> combatant.getHero() != null)
                .findFirst()
                .orElseThrow();
        assertEquals(2, snapshot.getHeroLevel());
        assertEquals(1, snapshot.getMeleeLevel());
        assertEquals(1, snapshot.getDistanceLevel());
        assertEquals(1, snapshot.getShieldLevel());
        assertEquals(reloaded.getStaminaMilliseconds(), snapshot.getStartingStaminaMilliseconds());
        assertEquals(20, snapshot.getBasicAttackManaCost());
        assertEquals(115, snapshot.getMaxHealth());
        assertEquals(560, snapshot.getMaxMana());
    }
}
