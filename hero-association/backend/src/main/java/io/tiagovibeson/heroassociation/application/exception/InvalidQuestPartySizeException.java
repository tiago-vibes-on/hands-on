package io.tiagovibeson.heroassociation.application.exception;

public class InvalidQuestPartySizeException extends RuntimeException {

    public InvalidQuestPartySizeException(int minimumHeroes, int maximumHeroes) {
        super("Party must contain between %d and %d heroes to start this quest.".formatted(minimumHeroes, maximumHeroes));
    }
}
