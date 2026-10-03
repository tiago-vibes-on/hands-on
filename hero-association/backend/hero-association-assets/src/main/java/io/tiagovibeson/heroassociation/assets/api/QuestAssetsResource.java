package io.tiagovibeson.heroassociation.assets.api;

import io.tiagovibeson.heroassociation.assets.application.CoreAssetCommands;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/internal/v1/assets/quest") @Consumes(MediaType.APPLICATION_JSON) @Produces(MediaType.APPLICATION_JSON)
public class QuestAssetsResource {
    @Inject ServiceAuthentication authentication;
    @Inject CoreAssetCommands commands;
    @POST @Path("/rewards")
    public CoreAssetCommands.CommandReceipt reward(@HeaderParam("X-Hero-Association-Assets-Quest-Service-Key") String key, CoreAssetCommands.Command command) {
        authentication.quest(key);
        if (command == null || !"QUEST_REWARD".equals(command.kind()) || command.agencyId() != null || command.heroId() != null
                || command.slotIndex() != null || command.runeId() != null || command.sourceOwnerType() != null || command.feeGold() != null || command.heroIds() != null)
            throw new BadRequestException("Quest may only credit its pinned Manager reward.");
        return commands.execute(command);
    }
}
