package io.tiagovibeson.heroassociation.api.v1.agency;

import java.util.UUID;

import io.tiagovibeson.heroassociation.application.RuneLoadoutService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/agencies/{agencyId}/heroes/{heroId}/rune-slots")
@Produces(MediaType.APPLICATION_JSON)
public class RuneLoadoutController {

    @Inject
    RuneLoadoutService runeLoadoutService;

    @PUT
    @Path("/{slotIndex}")
    @Consumes(MediaType.APPLICATION_JSON)
    public AgencyStateResponse equip(
            @PathParam("agencyId") UUID agencyId,
            @PathParam("heroId") UUID heroId,
            @PathParam("slotIndex") int slotIndex,
            @Valid EquipRuneRequest request) {
        return runeLoadoutService.equip(agencyId, heroId, slotIndex, request.runeId());
    }

    @DELETE
    @Path("/{slotIndex}")
    public AgencyStateResponse unequip(
            @PathParam("agencyId") UUID agencyId,
            @PathParam("heroId") UUID heroId,
            @PathParam("slotIndex") int slotIndex) {
        return runeLoadoutService.unequip(agencyId, heroId, slotIndex);
    }
}
