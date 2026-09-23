package io.tiagovibeson.heroassociation.application.exception;

public class MarketOrderRejectedException extends RuntimeException {

    public MarketOrderRejectedException(String message) {
        super(message);
    }
}
