package io.tiagovibeson.heroassociation.expedition;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

/** Resolves player identity through Core before touching an owned Redis run. */
@ApplicationScoped
public class ExpeditionPlayerService {

    private final CoreAdmissionClient core;
    private final ExpeditionEntryService entries;
    private final ExpeditionService expeditions;

    public ExpeditionPlayerService(CoreAdmissionClient core, ExpeditionEntryService entries,
                                   ExpeditionService expeditions) {
        this.core = core;
        this.entries = entries;
        this.expeditions = expeditions;
    }

    public RunState start(UUID expeditionId, UUID agencyId, UUID partyId, UUID mapId,
                          UUID commandId, String playerAccessToken) {
        return entries.start(expeditionId, agencyId, partyId, mapId, commandId, playerAccessToken);
    }

    public RunState active(String playerAccessToken) {
        return expeditions.active(core.currentManagerId(playerAccessToken));
    }

    public RunState get(UUID expeditionId, String playerAccessToken) {
        return expeditions.get(core.currentManagerId(playerAccessToken), expeditionId);
    }

    public RunState continueRun(UUID expeditionId, UUID commandId, long expectedVersion,
                                String playerAccessToken) {
        return expeditions.continueRun(core.currentManagerId(playerAccessToken), expeditionId,
                commandId, expectedVersion);
    }

    public RunState returnRun(UUID expeditionId, UUID commandId, long expectedVersion,
                              String playerAccessToken) {
        return expeditions.returnRun(core.currentManagerId(playerAccessToken), expeditionId,
                commandId, expectedVersion);
    }
}
