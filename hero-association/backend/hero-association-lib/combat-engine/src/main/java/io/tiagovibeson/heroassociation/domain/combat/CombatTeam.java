package io.tiagovibeson.heroassociation.domain.combat;

public enum CombatTeam {
    HEROES,
    CREATURES;

    public CombatTeam opponent() {
        return this == HEROES ? CREATURES : HEROES;
    }
}
