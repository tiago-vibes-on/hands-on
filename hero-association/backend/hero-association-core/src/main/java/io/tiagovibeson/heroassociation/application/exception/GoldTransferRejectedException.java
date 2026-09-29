package io.tiagovibeson.heroassociation.application.exception;

public class GoldTransferRejectedException extends RuntimeException {

    private final int status;

    public GoldTransferRejectedException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
