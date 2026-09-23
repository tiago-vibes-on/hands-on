package io.tiagovibeson.heroassociation.api.v1.error;

import io.tiagovibeson.heroassociation.application.exception.AccountInactiveException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class AccountInactiveExceptionMapper implements ExceptionMapper<AccountInactiveException> {

    @Override
    public Response toResponse(AccountInactiveException exception) {
        return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(exception.getMessage()))
                .build();
    }
}
