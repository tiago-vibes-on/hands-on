package io.tiagovibeson.heroassociation.assets.application.exception;

public class AssetOperationRejectedException extends RuntimeException {
    private final int status;

    public AssetOperationRejectedException(String message) {
        this(409, message);
    }

    public AssetOperationRejectedException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() { return status; }
}
