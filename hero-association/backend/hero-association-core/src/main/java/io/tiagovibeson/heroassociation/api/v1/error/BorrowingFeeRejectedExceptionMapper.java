package io.tiagovibeson.heroassociation.api.v1.error;

import io.tiagovibeson.heroassociation.application.exception.BorrowingFeeRejectedException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class BorrowingFeeRejectedExceptionMapper implements ExceptionMapper<BorrowingFeeRejectedException> {

    @Override
    public Response toResponse(BorrowingFeeRejectedException exception) {
        return Response.status(Response.Status.CONFLICT)
                .entity(new ApiError(exception.getMessage()))
                .build();
    }
}
