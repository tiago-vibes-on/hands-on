package io.tiagovibeson.heroassociation.api.v1.account;

import io.tiagovibeson.heroassociation.application.AccountService;
import io.tiagovibeson.heroassociation.application.AuthenticatedIdentityService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/account")
@Produces(MediaType.APPLICATION_JSON)
public class AccountController {

    @Inject
    AccountService accountService;

    @Inject
    AuthenticatedIdentityService authenticatedIdentityService;

    @GET
    public AccountResponse currentAccount() {
        return accountService.currentAccount(authenticatedIdentityService.currentIdentity());
    }

    @POST
    @Path("/manager")
    @Consumes(MediaType.APPLICATION_JSON)
    public AccountResponse createManager(CreateManagerRequest request) {
        return accountService.createManager(
                authenticatedIdentityService.currentIdentity(),
                request == null ? null : request.displayName());
    }
}
