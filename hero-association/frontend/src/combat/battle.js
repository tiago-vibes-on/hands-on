import { mageSpells } from '../data/spells'

export const combatRules = {
  baseCriticalChance: 0,
  baseCriticalDamageMultiplier: 2,
}

const heroes = [
  {
    id: 'brom',
    name: 'Ironwall',
    role: 'Warrior',
    level: 1,
    healthRecovery: 10,
    manaRecovery: 2,
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
    magicLevel: 15,
    healthRecovery: 2,
    manaRecovery: 10,
    maxHealth: 100,
    maxMana: 500,
    spells: mageSpells,
    damage: 32,
    attackInterval: 1700,
    color: 0x835d9a,
  },
  {
    id: 'kael',
    name: 'Swiftarrow',
    role: 'Archer',
    level: 1,
    healthRecovery: 6,
    manaRecovery: 6,
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
    criticalChance: 0.1,
    damage: 1,
    attackInterval: 1850,
    color: 0x7c6047,
  },
  {
    id: 'young-troll-2',
    name: 'Troll',
    maxHealth: 2000,
    maxMana: 100,
    criticalChance: 0.1,
    damage: 1,
    attackInterval: 1950,
    color: 0x8d6c4d,
  },
  {
    id: 'young-troll-3',
    name: 'Troll',
    maxHealth: 2000,
    maxMana: 100,
    criticalChance: 0.1,
    damage: 1,
    attackInterval: 2050,
    color: 0x74583e,
  },
]

function runeEffectTotal(runes, effect) {
  return runes.reduce((total, rune) => total + (rune?.effects?.[effect] ?? 0), 0)
}

function prepareCombatant(combatant, runes = []) {
  return {
    ...combatant,
    runes: Array.from({ length: 5 }, (_, index) => runes[index] ?? null),
    criticalChance: (combatant.criticalChance ?? combatRules.baseCriticalChance) + runeEffectTotal(runes, 'criticalChance'),
    criticalDamageMultiplier: (combatant.criticalDamageMultiplier ?? combatRules.baseCriticalDamageMultiplier) + runeEffectTotal(runes, 'criticalDamage'),
    currentHealth: combatant.maxHealth,
    currentMana: combatant.maxMana,
    alive: true,
  }
}

export function createBattle(encounterId = 1, equippedRunes = {}) {
  return {
    encounterId,
    status: 'in-progress',
    heroes: heroes.map((hero) => prepareCombatant(hero, equippedRunes[hero.name] ?? [])),
    creatures: creatures.map((creature) => prepareCombatant(creature)),
  }
}
