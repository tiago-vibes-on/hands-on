package io.tiagovibeson.heroassociation.application.exception;

import java.util.UUID;

public class MarketOrderNotFoundException extends RuntimeException {

    public MarketOrderNotFoundException(UUID orderId) {
        super("Market order with id %s was not found for this agency.".formatted(orderId));
    }
}
