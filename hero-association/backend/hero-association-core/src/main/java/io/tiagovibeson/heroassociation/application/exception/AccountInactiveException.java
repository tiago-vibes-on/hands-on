package io.tiagovibeson.heroassociation.application.exception;

public class AccountInactiveException extends RuntimeException {

    public AccountInactiveException() {
        super("This account is not active.");
    }
}
