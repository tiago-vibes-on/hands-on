import assert from 'node:assert/strict'
import test from 'node:test'
import { shouldAutoContinue, spellAvailability, toLoadoutHeroes, toPhaserBattle } from './expeditionCombat.js'

test('Map adapts authoritative events and resources for the old animated scene', () => {
  const rune = { symbol: '✦' }
  const run = {
    creature: { name: 'Troll' },
    heroes: [{ heroId: 'mage', name: 'Mage', heroClass: 'MAGE', health: 100, mana: 500 }],
    fight: { visual: {
      elapsedMilliseconds: 1_500,
      status: 'IN_PROGRESS',
      heroes: [{ id: 'mage', health: 80, mana: 480, maxHealth: 100, maxMana: 500,
        magicLevel: 15, nextSpellCastAt: { 'fire-ball': 3_500 } }],
      creatures: [
        { id: 'troll-1', health: 1_980, mana: 100, maxHealth: 2_000, maxMana: 100 },
        { id: 'troll-2', health: 2_000, mana: 100, maxHealth: 2_000, maxMana: 100 },
        { id: 'troll-3', health: 2_000, mana: 100, maxHealth: 2_000, maxMana: 100 },
      ],
      recentEvents: [{ sequenceNumber: 2, actorId: 'troll-1', action: 'BASIC_ATTACK', hits: [] },
        { sequenceNumber: 1, actorId: 'mage', action: 'FIRE_BALL', hits: [] }],
    } },
  }

  const battle = toPhaserBattle(run, [{ id: 'mage', level: 1, runeSlots: [rune] }])
  assert.equal(battle.heroes[0].currentHealth, 80)
  assert.equal(battle.heroes[0].nextSpellCastAt['fire-ball'], 3_500)
  assert.equal(battle.heroes[0].runes[0], rune)
  assert.equal(battle.heroes[0].spells.length, 2)
  assert.equal(battle.heroes[0].spells.every((spell) => !spell.locked), true)
  assert.equal(battle.creatures.length, 3)
  assert.equal(battle.creatures[0].currentHealth, 1_980)
  assert.deepEqual(battle.events.map((event) => event.sequenceNumber), [1, 2])
})

test('Map has no animated fight after its active fight is cleared', () => {
  assert.equal(toPhaserBattle({ fight: null }, []), null)
})

test('Map shows locked Mage spell slots before their required Magic Levels', () => {
  const run = {
    creature: { name: 'Troll' },
    heroes: [{ heroId: 'mage', name: 'Mage', heroClass: 'MAGE', health: 100, mana: 500 }],
    fight: { visual: {
      elapsedMilliseconds: 0, status: 'IN_PROGRESS',
      heroes: [{ id: 'mage', health: 100, mana: 500, maxHealth: 100, maxMana: 500,
        magicLevel: 1, nextSpellCastAt: {} }],
      creatures: [], recentEvents: [],
    } },
  }
  const battle = toPhaserBattle(run, [])
  assert.deepEqual(battle.heroes[0].spells.map((spell) => spell.locked), [true, true])
  assert.equal(battle.heroes[0].runes.length, 5)
})

test('Map keeps Mage spells and rune details visible between encounters', () => {
  const rune = { id: 'rune', name: 'Mana Rune', symbol: '♦', stats: '+30 mana' }
  const run = { heroes: [{ heroId: 'mage', name: 'Mage', heroClass: 'MAGE', health: 90, mana: 450 }] }
  const [mage] = toLoadoutHeroes(run, [{ id: 'mage', magicLevel: 15, runeSlots: [rune] }])
  assert.deepEqual(mage.runes, [rune])
  assert.deepEqual(mage.spells.map((spell) => spell.name), ['Fire Ball', 'Lightning Rail'])
  assert.equal(mage.spells.every((spell) => !spell.locked), true)
})

test('Spell details distinguish locked, cooling, low-mana, and ready states', () => {
  const spell = { id: 'fire-ball', requiredMagicLevel: 10, manaCost: 20, locked: false }
  assert.equal(spellAvailability({ ...spell, locked: true }, { currentMana: 100 }, 0),
    'Requires Magic Level 10')
  assert.equal(spellAvailability(spell, { currentMana: 100, nextSpellCastAt: { 'fire-ball': 2_100 } }, 1_000),
    '2s cooldown')
  assert.equal(spellAvailability(spell, { currentMana: 10 }, 0), 'Needs 20 mana')
  assert.equal(spellAvailability(spell, { currentMana: 20 }, 0), 'Ready')
})

test('Auto-continue only commands a living victory while enabled and idle', () => {
  const ready = { phase: 'AWAITING_CONTINUE', returnRequested: false, heroes: [{ health: 1 }] }
  assert.equal(shouldAutoContinue(ready, true, null), true)
  assert.equal(shouldAutoContinue(ready, false, null), false)
  assert.equal(shouldAutoContinue(ready, true, 'return'), false)
  assert.equal(shouldAutoContinue({ ...ready, returnRequested: true }, true, null), false)
  assert.equal(shouldAutoContinue({ ...ready, phase: 'WIPED' }, true, null), false)
  assert.equal(shouldAutoContinue({ ...ready, phase: 'FIGHTING' }, true, null), false)
  assert.equal(shouldAutoContinue({ ...ready, canContinue: false }, true, null), false)
  assert.equal(shouldAutoContinue({ ...ready, heroes: [{ health: 0 }] }, true, null), false)
})

test('Auto-continue repeats a completed dungeon only when the server permits Continue', () => {
  const completed = { phase: 'DUNGEON_COMPLETED', canContinue: true, returnRequested: false, heroes: [{ health: 1 }] }
  assert.equal(shouldAutoContinue(completed, true, null), true)
  assert.equal(shouldAutoContinue(completed, false, null), false)
  assert.equal(shouldAutoContinue({ ...completed, canContinue: undefined }, true, null), false)
  assert.equal(shouldAutoContinue(completed, true, 'continue'), false)
  assert.equal(shouldAutoContinue({ ...completed, returnRequested: true }, true, null), false)
  assert.equal(shouldAutoContinue({ ...completed, heroes: [{ health: 0 }] }, true, null), false)
  assert.equal(shouldAutoContinue({ ...completed, phase: 'SETTLEMENT_PENDING' }, true, null), false)
})
