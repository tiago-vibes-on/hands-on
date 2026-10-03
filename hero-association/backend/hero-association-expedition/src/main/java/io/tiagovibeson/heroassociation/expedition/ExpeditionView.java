package io.tiagovibeson.heroassociation.expedition;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Public command snapshot; excludes the hidden RNG seed and opening engine state. */
public record ExpeditionView(
        UUID expeditionId,
        UUID ownerManagerId,
        UUID agencyId,
        UUID partyId,
        UUID mapId,
        int mapVersion,
        int encounterIndex,
        long stateVersion,
        RunState.Phase phase,
        boolean returnRequested,
        List<RunState.HeroState> heroes,
        RunState.CreatureProfile creature,
        RunState.CarriedAssets carried,
        FightView fight,
        RunState.Outcome lastOutcome,
        io.tiagovibeson.heroassociation.contract.WorldContract.MapDefinition map,
        int floor,
        boolean dungeonCompleted,
        boolean canContinue,
        io.tiagovibeson.heroassociation.contract.QuestContract.Progress quest) {

    public static ExpeditionView from(RunState run) {
        return from(run, Instant.now(), null);
    }

    public static ExpeditionView from(RunState run, Instant now) {
        return from(run, now, null);
    }

    public static ExpeditionView from(RunState run, Instant now, FightTimeline timeline) {
        FightView fight = run.fight() == null ? null
                : new FightView(run.fight().fightId(), run.fight().startedAt(),
                        run.fight().rulesetVersion(), FightTimelineProjector.at(run, timeline, now));
        return new ExpeditionView(run.expeditionId(), run.ownerManagerId(), run.agencyId(), run.partyId(),
                run.mapId(), run.mapVersion(), run.encounterIndex(), run.stateVersion(),
                run.phase(), run.returnRequested(), run.heroes(), run.creature(),
                run.carried(), fight, run.lastOutcome(), run.world().map(), run.world().map().floorNumber(run.encounterIndex()),
                run.world().map().completedAfter(run.encounterIndex()) && run.lastOutcome() != null
                        && run.lastOutcome().status() == io.tiagovibeson.heroassociation.domain.combat.CombatStatus.HERO_VICTORY,
                run.canContinue(), run.quest());
    }

    public record FightView(UUID fightId, Instant startedAt, String rulesetVersion,
                            FightTimelineProjector.VisualFrame visual) { }
}
