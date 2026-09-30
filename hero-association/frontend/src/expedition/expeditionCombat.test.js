import assert from 'node:assert/strict'
import test from 'node:test'
import { toPhaserBattle } from './expeditionCombat.js'

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
      creatures: [{ id: 'troll', health: 1_980, mana: 100, maxHealth: 2_000, maxMana: 100 }],
      recentEvents: [{ sequenceNumber: 2, actorId: 'troll', action: 'BASIC_ATTACK', hits: [] },
        { sequenceNumber: 1, actorId: 'mage', action: 'FIRE_BALL', hits: [] }],
    } },
  }

  const battle = toPhaserBattle(run, [{ id: 'mage', level: 1, runeSlots: [rune] }])
  assert.equal(battle.heroes[0].currentHealth, 80)
  assert.equal(battle.heroes[0].nextSpellCastAt['fire-ball'], 3_500)
  assert.equal(battle.heroes[0].runes[0], rune)
  assert.equal(battle.heroes[0].spells.length, 2)
  assert.equal(battle.creatures[0].currentHealth, 1_980)
  assert.deepEqual(battle.events.map((event) => event.sequenceNumber), [1, 2])
})

test('Map has no animated fight after its active fight is cleared', () => {
  assert.equal(toPhaserBattle({ fight: null }, []), null)
})
