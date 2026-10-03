package io.tiagovibeson.heroassociation.application.expedition;

public class ReservationClosedException extends IllegalStateException {
    public ReservationClosedException() { super("This Expedition entry is closed. Start a new entry."); }
}
