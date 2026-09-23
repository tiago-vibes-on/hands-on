package io.tiagovibeson.heroassociation.application.exception;

public class InvalidRuneSlotException extends RuntimeException {

    public InvalidRuneSlotException(int slotIndex) {
        super("Rune slot %d is invalid. A hero has slots 0 through 4.".formatted(slotIndex));
    }
}
