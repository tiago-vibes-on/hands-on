package io.tiagovibeson.heroassociation.application.exception;

public class MarketOrderOwnerRequiredException extends RuntimeException {

    public MarketOrderOwnerRequiredException() {
        super("Only the personal order owner may cancel this market order.");
    }
}
