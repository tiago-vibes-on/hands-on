import { mageSpells } from '../data/spells.js'

const heroColors = { WARRIOR: 0xa67434, MAGE: 0x835d9a, ARCHER: 0x357c79 }
const creatureColors = [0x7c6047, 0x8d6c4d, 0x74583e, 0x876747]

export function shouldAutoContinue(run, enabled, pendingAction) {
  return Boolean(enabled && !pendingAction && run?.phase === 'AWAITING_CONTINUE'
    && !run.returnRequested && run.heroes?.some((hero) => hero.health > 0))
}

export function toLoadoutHeroes(run, knownHeroes, visual = run.fight?.visual) {
  const knownById = new Map(knownHeroes.map((hero) => [hero.id, hero]))
  const visualById = new Map((visual?.heroes ?? []).map((hero) => [hero.id, hero]))
  return run.heroes.map((hero) => {
    const current = visualById.get(hero.heroId)
    const known = knownById.get(hero.heroId)
    const magicLevel = current?.magicLevel ?? known?.magicLevel ?? 1
    return {
      id: hero.heroId,
      name: hero.name,
      role: hero.heroClass.toLowerCase().replace(/^./, (letter) => letter.toUpperCase()),
      level: hero.level ?? known?.level ?? 1,
      magicLevel,
      maxHealth: current?.maxHealth ?? known?.maxHealth ?? hero.health,
      currentHealth: current?.health ?? hero.health,
      maxMana: current?.maxMana ?? known?.maxMana ?? hero.mana,
      currentMana: current?.mana ?? hero.mana,
      color: heroColors[hero.heroClass] ?? heroColors.WARRIOR,
      alive: (current?.health ?? hero.health) > 0,
      runes: known?.runeSlots ?? [null, null, null, null, null],
      spells: hero.heroClass === 'MAGE'
        ? mageSpells.map((spell) => ({
          ...spell,
          locked: magicLevel < spell.requiredMagicLevel,
        })) : [],
      nextSpellCastAt: current?.nextSpellCastAt ?? {},
    }
  })
}

export function spellAvailability(spell, hero, currentTimeMilliseconds) {
  if (spell.locked) return `Requires Magic Level ${spell.requiredMagicLevel}`
  const remaining = (hero.nextSpellCastAt?.[spell.id] ?? 0) - currentTimeMilliseconds
  if (remaining > 0) return `${Math.ceil(remaining / 1_000)}s cooldown`
  if (hero.currentMana < spell.manaCost) return `Needs ${spell.manaCost} mana`
  return 'Ready'
}

/** Only adapts authoritative visual data; it never runs combat mechanics. */
export function toPhaserBattle(run, knownHeroes) {
  const visual = run.fight?.visual
  if (!visual) return null

  const heroes = toLoadoutHeroes(run, knownHeroes, visual)
  const creatures = visual.creatures.map((creature, index) => ({
    id: creature.id,
    name: run.creature.name,
    maxHealth: creature.maxHealth,
    currentHealth: creature.health,
    maxMana: creature.maxMana,
    currentMana: creature.mana,
    color: creatureColors[index % creatureColors.length],
    alive: creature.health > 0,
    nextSpellCastAt: {},
  }))
  return {
    currentTimeMilliseconds: visual.elapsedMilliseconds,
    status: visual.status,
    heroes,
    creatures,
    events: [...(visual.recentEvents ?? [])].sort((a, b) => a.sequenceNumber - b.sequenceNumber),
  }
}
