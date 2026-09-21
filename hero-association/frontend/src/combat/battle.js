const heroes = [
  {
    id: 'brom',
    name: 'Ironwall',
    role: 'Warrior',
    level: 1,
    maxHealth: 300,
    maxMana: 50,
    damage: 22,
    attackInterval: 1300,
    color: 0xa67434,
  },
  {
    id: 'elara',
    name: 'Moonweaver',
    role: 'Mage',
    level: 1,
    maxHealth: 100,
    maxMana: 500,
    damage: 32,
    attackInterval: 1700,
    color: 0x835d9a,
  },
  {
    id: 'kael',
    name: 'Swiftarrow',
    role: 'Archer',
    level: 1,
    maxHealth: 200,
    maxMana: 200,
    damage: 26,
    attackInterval: 1100,
    color: 0x357c79,
  },
]

const creatures = [
  {
    id: 'young-troll-1',
    name: 'Troll',
    maxHealth: 2000,
    maxMana: 100,
    damage: 1,
    attackInterval: 1850,
    color: 0x7c6047,
  },
  {
    id: 'young-troll-2',
    name: 'Troll',
    maxHealth: 2000,
    maxMana: 100,
    damage: 1,
    attackInterval: 1950,
    color: 0x8d6c4d,
  },
  {
    id: 'young-troll-3',
    name: 'Troll',
    maxHealth: 2000,
    maxMana: 100,
    damage: 1,
    attackInterval: 2050,
    color: 0x74583e,
  },
]

function prepareCombatant(combatant) {
  return {
    ...combatant,
    currentHealth: combatant.maxHealth,
    currentMana: combatant.maxMana,
    alive: true,
  }
}

export function createBattle(encounterId = 1) {
  return {
    encounterId,
    status: 'in-progress',
    heroes: heroes.map(prepareCombatant),
    creatures: creatures.map(prepareCombatant),
  }
}
