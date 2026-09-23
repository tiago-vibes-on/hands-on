import { lazy, Suspense, useEffect, useRef, useState } from 'react'
import { addHeroToParty, ApiRequestError, beginLogin, cancelMarketOrder, changeHeroActivity, createFeedPost, createManager, createMarketOrder, createParty, equipHeroRune, fetchAccount, fetchAgencyState, fetchMarketOrders, fetchSession, logout, removeHeroFromParty, startQuest, synchronizeQuestCombat, unequipHeroRune } from './api/agency'
import { initialEquippedRunes, initialRunes } from './data/inventory'
import { mageSpells } from './data/spells'
import './App.css'

const CombatScene = lazy(() => import('./combat/CombatScene'))

const navigation = [
  { id: 'overview', label: 'Overview', icon: '◇' },
  { id: 'heroes', label: 'Heroes', icon: '♙' },
  { id: 'quests', label: 'Quests', icon: '⚔' },
  { id: 'agency', label: 'Agency', icon: '⌂' },
  { id: 'market', label: 'Market', icon: '⇄' },
  { id: 'feed', label: 'Feed', icon: '◌' },
]

const fallbackHeroes = [
  { name: 'Brom Ironwall', alias: 'Ironwall', role: 'Warrior', level: 1, currentHealth: 300, maxHealth: 300, currentMana: 50, maxMana: 50, healthRecovery: 10, manaRecovery: 2, stamina: 58, color: 'gold', partyId: 'broken-pass-party', status: 'quest' },
  { name: 'Elara Moonweaver', alias: 'Moonweaver', role: 'Mage', level: 1, magicLevel: 15, currentHealth: 100, maxHealth: 100, currentMana: 500, maxMana: 500, healthRecovery: 2, manaRecovery: 10, spells: mageSpells, stamina: 24, color: 'violet', partyId: 'broken-pass-party', status: 'quest' },
  { name: 'Kael Swiftarrow', alias: 'Swiftarrow', role: 'Archer', level: 1, currentHealth: 200, maxHealth: 200, currentMana: 200, maxMana: 200, healthRecovery: 6, manaRecovery: 6, stamina: 91, color: 'teal', partyId: 'broken-pass-party', status: 'quest' },
  { name: 'Dorian Oakshield', alias: 'Oakshield', role: 'Warrior', level: 1, currentHealth: 300, maxHealth: 300, currentMana: 50, maxMana: 50, healthRecovery: 10, manaRecovery: 2, color: 'gold', activity: 'Training', status: 'agency' },
  { name: 'Runa Emberveil', alias: 'Emberveil', role: 'Mage', level: 1, currentHealth: 100, maxHealth: 100, currentMana: 500, maxMana: 500, healthRecovery: 2, manaRecovery: 10, color: 'violet', activity: 'Resting', status: 'agency' },
  { name: 'Lyra Hawkeye', alias: 'Hawkeye', role: 'Archer', level: 1, currentHealth: 200, maxHealth: 200, currentMana: 200, maxMana: 200, healthRecovery: 6, manaRecovery: 6, color: 'teal', activity: 'Training', status: 'agency' },
]

const fallbackCombat = {
  version: 'local-fixture',
  status: 'in-progress',
  currentTimeMilliseconds: 0,
  events: [],
  heroes: [
    { id: 'brom', name: 'Ironwall', role: 'Warrior', level: 1, maxHealth: 300, currentHealth: 300, maxMana: 50, currentMana: 50, color: 0xa67434, alive: true, runes: initialEquippedRunes.Ironwall, nextSpellCastAt: {} },
    { id: 'elara', name: 'Moonweaver', role: 'Mage', level: 1, magicLevel: 15, maxHealth: 100, currentHealth: 100, maxMana: 500, currentMana: 500, color: 0x835d9a, alive: true, runes: initialEquippedRunes.Moonweaver, spells: mageSpells, nextSpellCastAt: {} },
    { id: 'kael', name: 'Swiftarrow', role: 'Archer', level: 1, maxHealth: 200, currentHealth: 200, maxMana: 200, currentMana: 200, color: 0x357c79, alive: true, runes: initialEquippedRunes.Swiftarrow, nextSpellCastAt: {} },
  ],
  creatures: [
    { id: 'young-troll-1', name: 'Troll', maxHealth: 2000, currentHealth: 2000, maxMana: 100, currentMana: 100, color: 0x7c6047, alive: true },
    { id: 'young-troll-2', name: 'Troll', maxHealth: 2000, currentHealth: 2000, maxMana: 100, currentMana: 100, color: 0x8d6c4d, alive: true },
    { id: 'young-troll-3', name: 'Troll', maxHealth: 2000, currentHealth: 2000, maxMana: 100, currentMana: 100, color: 0x74583e, alive: true },
  ],
}

const fallbackParty = {
  id: 'broken-pass-party',
  name: 'Broken Pass Party',
  quest: 'Trolls at Broken Pass',
  questState: {
    title: 'Trolls at Broken Pass',
    status: 'IN_PROGRESS',
    creatureName: 'Troll',
    creaturesDefeated: 0,
    creaturesRequired: 3,
    combat: fallbackCombat,
  },
  heroIds: ['ironwall', 'moonweaver', 'swiftarrow'],
}

const fallbackQuestHeroes = fallbackHeroes.filter((hero) => hero.partyId === fallbackParty.id)
const fallbackAgencyHeroes = fallbackHeroes.filter((hero) => hero.status === 'agency')
const fallbackAvailableQuests = [
  {
    id: 'lost-courier',
    title: 'Lost Courier',
    description: 'Find the missing courier in the old forest.',
    status: 'AVAILABLE',
    creatureName: 'Forest Wolf',
    creaturesDefeated: 0,
    creaturesRequired: 4,
    minimumHeroes: 1,
    maximumHeroes: 2,
    durationMinutes: 30,
    goldReward: 85,
  },
]

const fallbackMetrics = [
  { label: 'Gold', value: '2,480', detail: '+240 this week', icon: 'G' },
  { label: 'Reputation', value: '340', detail: 'Rank: Trusted', icon: 'R' },
  { label: 'Party capacity', value: '71 / 120', detail: '49 remaining', icon: 'C' },
]

const fallbackUpgrades = [
  { name: 'Training', level: 4, detail: 'Improves available hero training.' },
  { name: 'Rest', level: 3, detail: 'Supports hero recovery facilities.' },
  { name: 'Size', level: 4, detail: 'Room for heroes and facilities.' },
  { name: 'Reputation', level: 3, detail: 'Unlocks better opportunities.' },
  { name: 'Intelligence', level: 3, detail: 'Reveals quest risks and rewards.' },
]

const fallbackItemInventory = [
  { id: 'magic-crystal', code: 'magic-crystal', name: 'Magic Crystal', symbol: '◇', description: 'A concentrated shard of arcane energy used in trade and crafting.', quantity: 3 },
  { id: 'iron-ingot', code: 'iron-ingot', name: 'Iron Ingot', symbol: '▰', description: 'Refined iron ready for weapons, armor, or trade.', quantity: 24 },
]

const fallbackMarketOrders = [
  { id: 'ironridge-buy-magic', agencyId: 'ironridge-exchange', agencyName: 'Ironridge Exchange', itemId: 'magic-crystal', itemCode: 'magic-crystal', itemName: 'Magic Crystal', itemSymbol: '◇', side: 'BUY', quantityRemaining: 2, priceGoldPerItem: 100, createdAt: '2026-01-01T11:50:00Z' },
  { id: 'ironridge-sell-iron', agencyId: 'ironridge-exchange', agencyName: 'Ironridge Exchange', itemId: 'iron-ingot', itemCode: 'iron-ingot', itemName: 'Iron Ingot', itemSymbol: '▰', side: 'SELL', quantityRemaining: 12, priceGoldPerItem: 16, createdAt: '2026-01-01T11:55:00Z' },
]

const heroColors = {
  WARRIOR: 'gold',
  MAGE: 'violet',
  ARCHER: 'teal',
}

const combatHeroColors = {
  WARRIOR: 0xa67434,
  MAGE: 0x835d9a,
  ARCHER: 0x357c79,
}

const creatureColors = [0x7c6047, 0x8d6c4d, 0x74583e, 0x876747]

const runeEffects = {
  CRITICAL_CHANCE: 'criticalChance',
  CRITICAL_DAMAGE: 'criticalDamage',
}

function titleCase(value) {
  return value.toLowerCase().split('_').map((word) => `${word[0].toUpperCase()}${word.slice(1)}`).join(' ')
}

function mapRune(rune) {
  const effect = runeEffects[rune.effect]
  return {
    ...rune,
    effects: effect ? { [effect]: rune.effectValue } : undefined,
  }
}

function mapCombatSnapshot(combat, heroesById) {
  if (!combat) {
    return null
  }

  const combatants = combat.combatants.map((combatant) => {
    const hero = combatant.heroId ? heroesById.get(combatant.heroId) : null
    return {
      id: combatant.id,
      name: combatant.name,
      team: combatant.team,
      role: hero?.role,
      level: hero?.level,
      magicLevel: combatant.magicLevel,
      maxHealth: combatant.maxHealth,
      currentHealth: combatant.currentHealth,
      maxMana: combatant.maxMana,
      currentMana: combatant.currentMana,
      alive: combatant.currentHealth > 0,
      color: hero ? combatHeroColors[combatant.heroClass] : creatureColors[combatant.formationIndex % creatureColors.length],
      runes: hero?.runeSlots ?? [],
      spells: hero?.spells,
      nextSpellCastAt: {
        'fire-ball': combatant.fireBallNextCastAt,
        'lightning-rail': combatant.lightningRailNextCastAt,
      },
      formationIndex: combatant.formationIndex,
    }
  })
  const status = {
    IN_PROGRESS: 'in-progress',
    HERO_VICTORY: 'victory',
    CREATURE_VICTORY: 'defeat',
  }[combat.status] ?? 'in-progress'

  return {
    version: combat.lastSynchronizedAt ?? `${combat.currentTimeMilliseconds}`,
    status,
    currentTimeMilliseconds: combat.currentTimeMilliseconds,
    lastSynchronizedAt: combat.lastSynchronizedAt,
    events: [...(combat.events ?? [])].sort((left, right) => left.sequenceNumber - right.sequenceNumber),
    heroes: combatants.filter((combatant) => combatant.team === 'HEROES').sort((left, right) => left.formationIndex - right.formationIndex),
    creatures: combatants.filter((combatant) => combatant.team === 'CREATURES').sort((left, right) => left.formationIndex - right.formationIndex),
  }
}

function mapAgencyState(state) {
  const heroes = state.heroes.map((hero) => ({
    id: hero.id,
    name: hero.name,
    alias: hero.alias,
    role: titleCase(hero.heroClass),
    level: hero.level,
    magicLevel: hero.magicLevel,
    currentHealth: hero.currentHealth,
    maxHealth: hero.maxHealth,
    currentMana: hero.currentMana,
    maxMana: hero.maxMana,
    healthRecovery: hero.healthRecoveryPerSecond,
    manaRecovery: hero.manaRecoveryPerSecond,
    stamina: hero.stamina,
    color: heroColors[hero.heroClass],
    partyId: hero.partyId,
    status: hero.activity === 'ON_QUEST' ? 'quest' : 'agency',
    activity: titleCase(hero.activity),
    spells: hero.heroClass === 'MAGE' && hero.magicLevel >= 10 ? mageSpells : undefined,
    runeSlots: hero.runeSlots.map((slot) => slot.rune ? mapRune(slot.rune) : null),
  }))
  const heroesById = new Map(heroes.map((hero) => [hero.id, hero]))
  const quests = state.quests.map((quest) => ({ ...quest, combat: mapCombatSnapshot(quest.combat, heroesById) }))
  const questsById = new Map(quests.map((quest) => [quest.id, quest]))
  const parties = state.parties.map((party) => {
    const quest = party.quest ? questsById.get(party.quest.id) : null
    return { ...party, quest: quest?.title, questState: quest }
  })
  const activeParties = parties.filter((party) => party.questState?.status === 'IN_PROGRESS')
  const activeParty = activeParties[0] ?? null
  const questHeroes = heroes.filter((hero) => hero.partyId === activeParty?.id)
  const preparedParties = parties.filter((party) => !party.questState || party.questState.status !== 'IN_PROGRESS')
  const availableQuests = state.quests.filter((quest) => quest.status === 'AVAILABLE')
  const agencyHeroes = heroes.filter((hero) => hero.status === 'agency' && !hero.partyId)
  const runeInventory = state.runeInventory.map(({ rune, quantity }) => ({ ...mapRune(rune), quantity }))
  const itemInventory = (state.itemInventory ?? []).map(({ item, quantity }) => ({ ...item, quantity }))
  const equippedRunes = Object.fromEntries(heroes.map((hero) => [hero.alias, hero.runeSlots]))
  const upgrades = [
    { name: 'Training', level: state.agency.levels.training, detail: 'Improves available hero training.' },
    { name: 'Rest', level: state.agency.levels.rest, detail: 'Supports hero recovery facilities.' },
    { name: 'Size', level: state.agency.levels.size, detail: 'Room for heroes and facilities.' },
    { name: 'Reputation', level: state.agency.levels.reputation, detail: 'Unlocks better opportunities.' },
    { name: 'Intelligence', level: state.agency.levels.intelligence, detail: 'Reveals quest risks and rewards.' },
  ]

  return {
    agency: state.agency,
    heroes,
    parties,
    quests,
    activeParty,
    activeParties,
    questHeroes,
    preparedParties,
    availableQuests,
    agencyHeroes,
    runeInventory,
    itemInventory,
    equippedRunes,
    feedPosts: state.feedPosts.map((post) => ({ ...post, item: post.item ?? null, itemQuantity: post.itemQuantity ?? null })),
    upgrades,
    metrics: [
      { label: 'Gold', value: state.agency.gold.toLocaleString(), detail: 'Agency funds', icon: 'G' },
      { label: 'Reputation', value: state.agency.reputation.toLocaleString(), detail: 'Agency standing', icon: 'R' },
      { label: 'Active parties', value: `${activeParties.length}`, detail: activeParties.length === 1 ? '1 party on a quest' : `${activeParties.length} parties on quests`, icon: 'P' },
    ],
  }
}

const fallbackGameState = {
  agency: { id: 'dawnwatch-agency', name: 'Dawnwatch Agency', leaderId: 'tiago', leaderName: 'Tiago', levels: { agency: 4 } },
  heroes: fallbackHeroes,
  activeParty: fallbackParty,
  activeParties: [fallbackParty],
  questHeroes: fallbackQuestHeroes,
  preparedParties: [],
  availableQuests: fallbackAvailableQuests,
  agencyHeroes: fallbackAgencyHeroes,
  runeInventory: initialRunes.map((rune) => ({ ...rune })),
  itemInventory: fallbackItemInventory,
  equippedRunes: initialEquippedRunes,
  feedPosts: [
    { id: 'dawnwatch-update', authorType: 'AGENCY', authorId: 'dawnwatch-agency', authorName: 'Dawnwatch Agency', content: 'The party has reached Broken Pass. The road will be open again soon.', publishedAt: '2026-01-01T12:00:00Z' },
    { id: 'moonweaver-update', authorType: 'HERO', authorId: 'moonweaver', authorName: 'Elara Moonweaver', content: 'Rested, prepared, and ready for whatever waits beyond the pass.', item: fallbackItemInventory[0], itemQuantity: 1, publishedAt: '2026-01-01T11:00:00Z' },
  ],
  upgrades: fallbackUpgrades,
  metrics: fallbackMetrics,
}

function HeroAvatar({ hero, size = 'normal' }) {
  return <div className={`hero-avatar hero-avatar--${hero.color} hero-avatar--${size}`} aria-hidden="true">{hero.alias.slice(0, 1)}</div>
}

function experienceGain(stamina) {
  if (stamina >= 80) {
    return 150
  }
  if (stamina >= 30) {
    return 100
  }
  return 50
}

function StaminaBar({ value }) {
  const status = value < 30 ? 'critical' : value < 80 ? 'warning' : 'healthy'

  return (
    <div className={`stamina stamina--${status}`} aria-label={`${value}% stamina`}>
      <span className="stamina__label">Stamina</span>
      <div className="stamina__track"><span className="stamina__value" style={{ width: `${value}%` }} /></div>
      <span>{value}%</span>
    </div>
  )
}

function HeroLoadoutSlots({ hero, runes, onSelectRuneSlot }) {
  const runeSlots = runes[hero.alias] ?? Array(5).fill(null)

  return (
    <div className="hero-loadout" role="group" aria-label={`${hero.alias} rune slots`}>
      <div className="hero-loadout__group">
        <span>Runes</span>
        <div className="hero-loadout__slots" role="group" aria-label="Five rune slots">
          {runeSlots.map((rune, index) => (
            <button className={`hero-loadout__slot hero-loadout__slot--rune ${rune ? 'hero-loadout__slot--equipped' : ''}`} type="button" key={index} aria-label={`${hero.alias} rune slot ${index + 1}${rune ? `: ${rune.name}` : ', empty'}`} onClick={() => onSelectRuneSlot(hero, index)}>
              {rune?.symbol}
            </button>
          ))}
        </div>
      </div>
      {hero.spells?.length > 0 && <div className="hero-loadout__group">
        <span>Spells</span>
        <div className="hero-loadout__slots" role="group" aria-label={`${hero.alias} equipped spells`}>
          {hero.spells.map((spell) => <span className="hero-loadout__slot hero-loadout__slot--spell" key={spell.id} title={`${spell.name}: ${spell.target}, base ${spell.baseDamage} + ${spell.magicLevelScaling * 100}% Magic Level, needs Magic Level ${spell.requiredMagicLevel}, ${spell.manaCost} mana, ${spell.cooldown / 1000}s cooldown`} aria-label={spell.name}>{spell.symbol}</span>)}
        </div>
      </div>}
    </div>
  )
}

function RuneDrawer({ selectedSlot, runes, runeInventory, isUpdating, error, onClose, onEquipRune, onUnequipRune }) {
  if (!selectedSlot) {
    return null
  }

  const { hero, slotIndex } = selectedSlot
  const equippedRune = runes[hero.alias]?.[slotIndex]

  return (
    <div className="equipment-drawer__backdrop" onClick={onClose}>
      <aside className="equipment-drawer" role="dialog" aria-modal="true" aria-labelledby="equipment-drawer-title" onClick={(event) => event.stopPropagation()}>
        <div className="equipment-drawer__header">
          <div><p className="eyebrow">Agency rune inventory</p><h2 id="equipment-drawer-title">Equip rune</h2><p>{hero.alias} · Rune slot {slotIndex + 1}</p></div>
          <button className="equipment-drawer__close" type="button" aria-label="Close rune drawer" onClick={onClose}>×</button>
        </div>
        {error && <p className="equipment-drawer__error" role="alert">{error}</p>}
        {equippedRune && <div className="equipment-drawer__equipped"><span>Currently equipped</span><strong>{equippedRune.symbol} {equippedRune.name}</strong><button className="text-button" type="button" disabled={isUpdating} onClick={onUnequipRune}>Unequip</button></div>}
        <div className="equipment-drawer__items">
          {runeInventory.map((rune) => (
            <button className="inventory-item" type="button" key={rune.id} disabled={isUpdating || rune.quantity === 0} onClick={() => onEquipRune(rune)}>
              <span className="inventory-item__symbol" aria-hidden="true">{rune.symbol}</span>
              <span className="inventory-item__content"><strong>{rune.name}</strong><small>{rune.stats}</small><span>{rune.description}</span></span>
              <span className="inventory-item__quantity">×{rune.quantity}</span>
            </button>
          ))}
        </div>
      </aside>
    </div>
  )
}

function PageHeading({ eyebrow, title, description, action }) {
  return (
    <header className="page-heading">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h1>{title}</h1>
        <p className="page-heading__description">{description}</p>
      </div>
      {action}
    </header>
  )
}

function Overview({ agency, metrics, activeParty, questHeroes, onNavigate }) {
  const quest = activeParty.questState
  const progress = quest ? (quest.creaturesDefeated / quest.creaturesRequired) * 100 : 0

  return (
    <>
      <PageHeading
        eyebrow={agency.name}
        title="Your agency is ready"
        description="Prepare your heroes, send a party on a quest, and grow your name."
        action={<button className="button button--primary" type="button" onClick={() => onNavigate('quests')}>View quests</button>}
      />
      <section className="metrics" aria-label="Agency summary">
        {metrics.map((metric) => (
          <article className="metric-card" key={metric.label}>
            <span className="metric-card__icon" aria-hidden="true">{metric.icon}</span>
            <div><p>{metric.label}</p><strong>{metric.value}</strong><small>{metric.detail}</small></div>
          </article>
        ))}
      </section>
      <section className="dashboard-grid">
        <article className="panel active-quest">
          <div className="panel__header">
            <div><p className="eyebrow">Active quest</p><h2>{activeParty.quest}</h2></div>
            <span className="status status--progress">In progress</span>
          </div>
          <p className="active-quest__description">Defeat {quest?.creaturesRequired} {quest?.creatureName?.toLowerCase()} to complete this quest.</p>
          <div className="quest-progress">
            <div className="quest-progress__labels"><span>Creatures defeated</span><strong>{quest?.creaturesDefeated} / {quest?.creaturesRequired}</strong></div>
            <div className="quest-progress__track"><span style={{ width: `${progress}%` }} /></div>
          </div>
          <div className="active-quest__footer">
            <div className="party-avatars" aria-label="Quest party">
              {questHeroes.map((hero) => <HeroAvatar hero={hero} size="small" key={hero.alias} />)}
              <span>{questHeroes.length} heroes</span>
            </div>
            <div className="quest-time"><span>Quest status</span><strong>In progress</strong></div>
          </div>
        </article>
        <article className="panel agency-level">
          <div className="panel__header">
            <div><p className="eyebrow">Agency progress</p><h2>Agency level {agency.levels.agency}</h2></div>
            <button className="text-button" type="button" onClick={() => onNavigate('agency')}>Manage</button>
          </div>
          <div className="level-orb" aria-label={`Agency level ${agency.levels.agency}`}>{agency.levels.agency}</div>
          <p>Next level unlocks more training and rest upgrades.</p>
          <div className="level-progress"><span style={{ width: '68%' }} /></div>
          <small>1,360 / 2,000 agency experience</small>
        </article>
      </section>
      <section className="panel roster-panel">
        <div className="panel__header">
          <div><p className="eyebrow">Active quest</p><h2>Party</h2></div>
          <button className="text-button" type="button" onClick={() => onNavigate('heroes')}>View all</button>
        </div>
        <div className="hero-list">
          {questHeroes.map((hero) => (
            <article className="hero-row" key={hero.alias}>
              <HeroAvatar hero={hero} />
              <div className="hero-row__identity"><strong>{hero.alias}</strong><span>{hero.role} · Level {hero.level}</span></div>
              <div className="hero-row__training">Earning experience from creatures</div>
              <StaminaBar value={hero.stamina} />
            </article>
          ))}
        </div>
      </section>
    </>
  )
}

function HeroCards({ roster, runes, onSelectRuneSlot, onChangeActivity, isUpdatingActivity, preparedParties, onAddToParty, onRemoveFromParty, partyId, isUpdatingParty }) {
  return (
    <div className="hero-cards">
      {roster.map((hero) => (
        <article className="panel hero-card" key={hero.alias}>
          <div className="hero-card__topline"><HeroAvatar hero={hero} size="large" /><span className={`status ${hero.status === 'quest' ? 'status--progress' : ''}`}>{hero.status === 'quest' ? 'On quest' : hero.activity}</span></div>
          <div><p className="eyebrow">{hero.role}</p><h2>{hero.alias}</h2><p className="hero-card__name">{hero.name} · Level {hero.level}</p>{hero.magicLevel && <p className="hero-card__magic-level">Magic Level {hero.magicLevel}</p>}<p className="hero-card__resources">Health {hero.currentHealth} / {hero.maxHealth} · Mana {hero.currentMana} / {hero.maxMana}</p><p className="hero-card__recovery">Recovery: +{hero.healthRecovery} health/s · +{hero.manaRecovery} mana/s</p></div>
          {hero.status === 'quest' ? (
            <>
              <div className="hero-card__details"><span>Experience</span><strong>{experienceGain(hero.stamina)}% XP gain from creatures</strong></div>
              <StaminaBar value={hero.stamina} />
            </>
          ) : <div className="hero-card__agency-activity"><span>At the agency</span><strong>{hero.activity}</strong><p>{hero.activity === 'Training' ? 'Recovering health and mana at the base class rate.' : 'Recovering health and mana at 2× the base class rate. Stamina recovery is not implemented yet.'}</p><div className="hero-card__activity-actions" role="group" aria-label={`${hero.alias} agency activity`}><button className={`activity-button ${hero.activity === 'Training' ? 'activity-button--active' : ''}`} type="button" disabled={isUpdatingActivity || hero.activity === 'Training'} onClick={() => onChangeActivity(hero, 'TRAINING')}>Training</button><button className={`activity-button ${hero.activity === 'Resting' ? 'activity-button--active' : ''}`} type="button" disabled={isUpdatingActivity || hero.activity === 'Resting'} onClick={() => onChangeActivity(hero, 'RESTING')}>Resting</button></div>{partyId ? <button className="text-button hero-card__party-action" type="button" disabled={isUpdatingParty} onClick={() => onRemoveFromParty(hero, partyId)}>Remove from party</button> : preparedParties?.length > 0 && <label className="hero-card__party-select"><span>Assign to party</span><select defaultValue="" disabled={isUpdatingParty} onChange={(event) => { const selectedPartyId = event.target.value; event.target.value = ''; if (selectedPartyId) { onAddToParty(hero, selectedPartyId) } }}><option value="" disabled>Select a party</option>{preparedParties.map((party) => <option value={party.id} key={party.id}>{party.name}</option>)}</select></label>}</div>}
          <HeroLoadoutSlots hero={hero} runes={runes} onSelectRuneSlot={onSelectRuneSlot} />
        </article>
      ))}
    </div>
  )
}

function Heroes({ agency, heroes, activeParties, agencyHeroes, preparedParties, runes, isUpdatingActivity, activityError, isUpdatingParty, partyError, isCreatingParty, partyName, onPartyNameChange, onCreateParty, onCancelCreateParty, onStartCreateParty, onSelectRuneSlot, onChangeActivity, onAddToParty, onRemoveFromParty }) {
  return (
    <>
      <PageHeading eyebrow={agency.name} title="Heroes" description="Prepare parties at the agency, then send one on a quest." action={<button className="button button--primary" type="button" onClick={onStartCreateParty}>Create party</button>} />
      {activeParties.map((party) => {
        const partyHeroes = heroes.filter((hero) => hero.partyId === party.id)
        return <section className="hero-group" aria-labelledby={`party-${party.id}`} key={party.id}><div className="hero-group__header party-card"><div><p className="eyebrow">Active party</p><h2 id={`party-${party.id}`}>{party.name}</h2><p>On quest: {party.quest} · {partyHeroes.length} heroes.</p></div><span className="status status--progress">{partyHeroes.length} in party</span></div><HeroCards roster={partyHeroes} runes={runes} onSelectRuneSlot={onSelectRuneSlot} /></section>
      })}
      {isCreatingParty && <form className="party-form" onSubmit={onCreateParty}><label><span>Party name</span><input value={partyName} maxLength="100" autoFocus disabled={isUpdatingParty} onChange={(event) => onPartyNameChange(event.target.value)} placeholder="Forest Scouts" /></label><div className="party-form__actions"><button className="button button--primary" type="submit" disabled={isUpdatingParty}>{isUpdatingParty ? 'Creating…' : 'Create party'}</button><button className="text-button" type="button" disabled={isUpdatingParty} onClick={onCancelCreateParty}>Cancel</button></div></form>}
      {partyError && <p className="inline-error" role="alert">{partyError}</p>}
      {preparedParties.map((party) => {
        const partyHeroes = heroes.filter((hero) => hero.partyId === party.id)
        const heroCountLabel = partyHeroes.length === 1 ? 'hero is' : 'heroes are'
        return <section className="hero-group" aria-labelledby={`party-${party.id}`} key={party.id}><div className="hero-group__header party-card"><div><p className="eyebrow">Prepared party</p><h2 id={`party-${party.id}`}>{party.name}</h2><p>{partyHeroes.length === 0 ? 'No heroes assigned yet.' : `${partyHeroes.length} ${heroCountLabel} ready at the agency.`}</p></div><span className="status">{partyHeroes.length} in party</span></div><HeroCards roster={partyHeroes} runes={runes} onSelectRuneSlot={onSelectRuneSlot} onChangeActivity={onChangeActivity} isUpdatingActivity={isUpdatingActivity} partyId={party.id} onRemoveFromParty={onRemoveFromParty} isUpdatingParty={isUpdatingParty} /></section>
      })}
      <section className="hero-group" aria-labelledby="agency-heroes-heading">
        <div className="hero-group__header"><div><p className="eyebrow">Agency roster</p><h2 id="agency-heroes-heading">Unassigned heroes</h2><p>Heroes can train or rest while they wait for a prepared party.</p></div><span className="status">{agencyHeroes.length} unassigned</span></div>
        {activityError && <p className="inline-error" role="alert">{activityError}</p>}
        <HeroCards roster={agencyHeroes} runes={runes} onSelectRuneSlot={onSelectRuneSlot} onChangeActivity={onChangeActivity} isUpdatingActivity={isUpdatingActivity} preparedParties={preparedParties} onAddToParty={onAddToParty} isUpdatingParty={isUpdatingParty} />
      </section>
    </>
  )
}

function AvailableQuestCard({ quest, preparedParties, isStartingQuest, onStartQuest }) {
  const [partyId, setPartyId] = useState('')
  const hasPreparedParty = preparedParties.length > 0

  return (
    <article className="panel quest-card">
      <div className="quest-card__content">
        <div><span className="status">Available</span><h2>{quest.title}</h2><p>{quest.description}</p></div>
        <div className="quest-card__facts"><span className="quest-card__fact"><b>{quest.minimumHeroes}–{quest.maximumHeroes}</b><small>heroes</small></span><span className="quest-card__fact"><b>{quest.durationMinutes}m</b><small>estimated</small></span><span className="quest-card__fact"><b>{quest.goldReward}</b><small>gold reward</small></span></div>
      </div>
      <div className="quest-card__start">
        {hasPreparedParty ? <><label><span>Prepared party</span><select value={partyId} disabled={isStartingQuest} onChange={(event) => setPartyId(event.target.value)}><option value="" disabled>Select a party</option>{preparedParties.map((party) => <option value={party.id} key={party.id}>{party.name} · {party.heroIds.length} heroes</option>)}</select></label><button className="button button--primary" type="button" disabled={isStartingQuest || !partyId} onClick={() => onStartQuest(quest.id, partyId)}>{isStartingQuest ? 'Starting…' : 'Start quest'}</button></> : <p>Create a prepared party before starting this quest.</p>}
      </div>
    </article>
  )
}

function Quests({ activeParty, activeParties, availableQuests, preparedParties, battle, isCombatExpanded, isSynchronizingCombat, isStartingQuest, questError, onStartQuest, onToggleCombat }) {
  const quest = activeParty.questState
  const defeatedCreatures = battle
    ? battle.creatures.filter((creature) => !creature.alive).length
    : quest.creaturesDefeated
  const combatStatus = battle?.status ?? 'in-progress'

  function handleQuestCardKeyDown(event) {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      onToggleCombat()
    }
  }

  return (
    <>
      <PageHeading eyebrow="Quest board" title="Quests" description="Choose a party that can finish the job and return safely." action={<button className="button button--primary" type="button">Find quests</button>} />
      {questError && <p className="inline-error" role="alert">{questError}</p>}
      <section className="quest-list">
        <article className={`panel quest-card quest-card--active ${isCombatExpanded ? 'quest-card--expanded' : ''}`}>
          <div className="quest-card__trigger" role="button" tabIndex="0" aria-expanded={isCombatExpanded} onClick={onToggleCombat} onKeyDown={handleQuestCardKeyDown}>
            <div><span className="status status--progress">{combatStatus === 'in-progress' ? 'In progress' : combatStatus}</span><h2>{quest.title}</h2><p>Defeat {quest.creaturesRequired} {quest.creatureName.toLowerCase()} to complete this quest.</p></div>
            <div className="quest-card__facts"><span className="quest-card__fact"><b>{defeatedCreatures} / {quest.creaturesRequired}</b><small>defeated</small></span><span className="quest-card__fact"><b>{activeParty.heroIds?.length ?? 0}</b><small>heroes</small></span><span className="quest-card__fact"><b>Active</b><small>quest</small></span><span className="quest-card__expand-icon" aria-hidden="true">{isCombatExpanded ? '−' : '+'}</span></div>
          </div>
          {isCombatExpanded && (
            <div className="combat-panel">
              <div className="combat-panel__header">
                <div><p className="eyebrow">Current encounter</p></div>
                {battle ? <span className="combat-panel__hint">{isSynchronizingCombat ? 'Synchronizing…' : 'Server-synchronized every 2 seconds.'}</span> : null}
              </div>
              {battle ? <Suspense fallback={<div className="combat-scene combat-scene--loading">Preparing the battlefield…</div>}><CombatScene battle={battle} /></Suspense> : <p className="combat-panel__hint">Combat data is not available for this quest yet.</p>}
            </div>
          )}
        </article>
        {activeParties.slice(1).map((party) => <article className="panel quest-card" key={party.id}><div className="quest-card__content"><div><span className="status status--progress">In progress</span><h2>{party.quest}</h2><p>{party.questState.description}</p></div><div className="quest-card__facts"><span className="quest-card__fact"><b>{party.questState.creaturesDefeated} / {party.questState.creaturesRequired}</b><small>defeated</small></span><span className="quest-card__fact"><b>{party.heroIds.length}</b><small>heroes</small></span></div></div></article>)}
        {availableQuests.map((availableQuest) => <AvailableQuestCard quest={availableQuest} preparedParties={preparedParties} isStartingQuest={isStartingQuest} onStartQuest={onStartQuest} key={availableQuest.id} />)}
      </section>
    </>
  )
}

function Agency({ agency, upgrades, runeInventory, itemInventory }) {
  const itemQuantity = itemInventory.reduce((total, item) => total + item.quantity, 0)
  const runeQuantity = runeInventory.reduce((total, rune) => total + rune.quantity, 0)

  return (
    <>
      <PageHeading eyebrow={agency.name} title="Agency upgrades" description="Agency level caps each specialized upgrade. You choose which areas to prioritize." action={<button className="button button--primary" type="button">Upgrade agency</button>} />
      <section className="upgrade-grid" aria-label="Agency upgrade levels">
        {upgrades.map((upgrade) => (
          <article className="panel upgrade-card" key={upgrade.name}>
            <div className="upgrade-card__level">{upgrade.level}</div>
            <div><h2>{upgrade.name} level</h2><p>{upgrade.detail}</p></div>
            <button className="text-button" type="button">Details</button>
          </article>
        ))}
      </section>
      <section className="panel agency-inventory">
        <div className="panel__header"><div><p className="eyebrow">Agency storage</p><h2>Agency inventory</h2></div><span className="status">{itemQuantity} items · {runeQuantity} runes</span></div>
        <h3 className="agency-inventory__heading">Items</h3>
        <div className="agency-inventory__items">
          {itemInventory.map((item) => (
            <article className="agency-inventory__item" key={item.id}>
              <span className="agency-inventory__symbol" aria-hidden="true">{item.symbol}</span>
              <div><strong>{item.name}</strong><span>Material</span><small>{item.description}</small></div>
              <b>×{item.quantity}</b>
            </article>
          ))}
        </div>
        <h3 className="agency-inventory__heading">Runes</h3>
        <div className="agency-inventory__items">
          {runeInventory.map((rune) => (
            <article className="agency-inventory__item" key={rune.id}>
              <span className="agency-inventory__symbol" aria-hidden="true">{rune.symbol}</span>
              <div><strong>{rune.name}</strong><span>{rune.stats}</span><small>{rune.description}</small></div>
              <b>×{rune.quantity}</b>
            </article>
          ))}
        </div>
      </section>
    </>
  )
}

function Market({ agency, itemInventory, marketOrders, isSubmittingOrder, marketError, onCreateOrder, onCancelOrder }) {
  const [side, setSide] = useState('BUY')
  const [itemId, setItemId] = useState(itemInventory[0]?.id ?? '')
  const [quantity, setQuantity] = useState(1)
  const [priceGoldPerItem, setPriceGoldPerItem] = useState(100)
  const buyOrders = marketOrders.filter((order) => order.side === 'BUY')
  const sellOrders = marketOrders.filter((order) => order.side === 'SELL')

  async function submitOrder(event) {
    event.preventDefault()
    const wasCreated = await onCreateOrder({ side, itemId, quantity, priceGoldPerItem })
    if (wasCreated) {
      setQuantity(1)
      setPriceGoldPerItem(100)
    }
  }

  function orderList(orders, emptyMessage) {
    if (orders.length === 0) {
      return <p className="offer-panel__empty">{emptyMessage}</p>
    }

    return orders.map((order) => <div className="offer-row" key={order.id}><span><b>{order.itemSymbol}</b>{order.itemName}<small>{order.agencyName}</small></span><strong>{order.priceGoldPerItem} gold</strong><small>{order.quantityRemaining} available</small>{order.agencyId === agency.id && <button className="text-button" type="button" disabled={isSubmittingOrder} onClick={() => onCancelOrder(order.id)}>Cancel</button>}</div>)
  }

  return (
    <>
      <PageHeading eyebrow="Community exchange" title="Market" description="Place buy and sell offers. When an offer matches, the seller pays a 10% fee." />
      <form className="panel market-form" onSubmit={submitOrder}>
        <label><span>Order type</span><select value={side} disabled={isSubmittingOrder} onChange={(event) => setSide(event.target.value)}><option value="BUY">Buy</option><option value="SELL">Sell</option></select></label>
        <label><span>Item</span><select value={itemId} disabled={isSubmittingOrder} onChange={(event) => setItemId(event.target.value)}>{itemInventory.map((item) => <option key={item.id} value={item.id}>{item.name} · {item.quantity} available</option>)}</select></label>
        <label><span>Quantity</span><input type="number" min="1" value={quantity} disabled={isSubmittingOrder} onChange={(event) => setQuantity(Math.max(1, Number(event.target.value) || 1))} /></label>
        <label><span>Gold per item</span><input type="number" min="1" value={priceGoldPerItem} disabled={isSubmittingOrder} onChange={(event) => setPriceGoldPerItem(Math.max(1, Number(event.target.value) || 1))} /></label>
        <button className="button button--primary" type="submit" disabled={isSubmittingOrder || !itemId}>{isSubmittingOrder ? 'Placing…' : 'Place order'}</button>
      </form>
      {marketError && <p className="inline-error" role="alert">{marketError}</p>}
      <section className="market-grid">
        <article className="panel offer-panel">
          <div className="panel__header"><h2>Buy offers</h2><span className="status">{buyOrders.length} open</span></div>
          {orderList(buyOrders, 'No buy offers are open.')}
        </article>
        <article className="panel offer-panel">
          <div className="panel__header"><h2>Sell offers</h2><span className="status">{sellOrders.length} open</span></div>
          {orderList(sellOrders, 'No sell offers are open.')}
        </article>
      </section>
    </>
  )
}

function Feed({ agency, heroes, feedPosts, itemInventory, isPostingFeed, feedError, onCreatePost }) {
  const [isComposerOpen, setIsComposerOpen] = useState(false)
  const [content, setContent] = useState('')
  const [authorKey, setAuthorKey] = useState(`AGENCY:${agency.id}`)
  const [itemId, setItemId] = useState('')
  const [itemQuantity, setItemQuantity] = useState(1)
  const authors = [
    { type: 'AGENCY', id: agency.id, name: agency.name, label: `${agency.name} · Agency` },
    { type: 'MANAGER', id: agency.leaderId, name: agency.leaderName, label: `${agency.leaderName} · Manager` },
    ...heroes.filter((hero) => hero.id).map((hero) => ({ type: 'HERO', id: hero.id, name: hero.name, label: `${hero.name} · Hero` })),
  ]
  const selectedAuthor = authors.find((author) => `${author.type}:${author.id}` === authorKey) ?? authors[0]
  const selectedItem = itemInventory.find((item) => item.id === itemId)

  async function submitPost(event) {
    event.preventDefault()
    if (!content.trim()) {
      return
    }

    const wasCreated = await onCreatePost({
      authorType: selectedAuthor.type,
      authorId: selectedAuthor.id,
      content,
      itemId: selectedItem?.id,
      itemQuantity: selectedItem ? itemQuantity : undefined,
    })
    if (wasCreated) {
      setContent('')
      setItemId('')
      setItemQuantity(1)
      setIsComposerOpen(false)
    }
  }

  return (
    <>
      <PageHeading eyebrow="Community" title="Feed" description="Updates from agencies, managers, and the heroes who make their names known." action={<button className="button button--primary" type="button" onClick={() => setIsComposerOpen(true)}>Write post</button>} />
      {isComposerOpen && <form className="panel feed-composer" onSubmit={submitPost}>
        <label><span>Posting as</span><select value={authorKey} disabled={isPostingFeed} onChange={(event) => setAuthorKey(event.target.value)}>{authors.map((author) => <option key={`${author.type}:${author.id}`} value={`${author.type}:${author.id}`}>{author.label}</option>)}</select></label>
        <label><span>Message</span><textarea value={content} maxLength="500" autoFocus disabled={isPostingFeed} onChange={(event) => setContent(event.target.value)} placeholder="Share an update with the agency." /></label>
        <div className="feed-composer__attachment"><label><span>Attach item (optional)</span><select value={itemId} disabled={isPostingFeed} onChange={(event) => { setItemId(event.target.value); setItemQuantity(1) }}><option value="">No item</option>{itemInventory.filter((item) => item.quantity > 0).map((item) => <option key={item.id} value={item.id}>{item.name} · {item.quantity} available</option>)}</select></label>{selectedItem && <label><span>Quantity</span><input type="number" min="1" max={selectedItem.quantity} value={itemQuantity} disabled={isPostingFeed} onChange={(event) => setItemQuantity(Math.min(selectedItem.quantity, Math.max(1, Number(event.target.value) || 1)))} /></label>}</div>
        <div className="feed-composer__actions"><small>{content.length} / 500</small><button className="button button--primary" type="submit" disabled={isPostingFeed || !content.trim()}>{isPostingFeed ? 'Posting…' : 'Publish'}</button><button className="text-button" type="button" disabled={isPostingFeed} onClick={() => setIsComposerOpen(false)}>Cancel</button></div>
      </form>}
      {feedError && <p className="inline-error" role="alert">{feedError}</p>}
      <section className="feed-list">
        {feedPosts.map((post) => {
          const hero = post.authorType === 'HERO' ? heroes.find((candidate) => candidate.id === post.authorId) : null
          return <article className="panel feed-post" key={post.id}>
            <div className="feed-post__author">{hero ? <HeroAvatar hero={hero} size="small" /> : <span className="feed-post__mark">{post.authorName.slice(0, 1)}</span>}<div><strong>{post.authorName}</strong><small>{relativePostTime(post.publishedAt)}</small></div></div>
            <p>{post.content}</p>
            {post.item && <div className="feed-post__item"><span aria-hidden="true">{post.item.symbol}</span>{post.item.name} ×{post.itemQuantity}</div>}
          </article>
        })}
      </section>
    </>
  )
}

function relativePostTime(publishedAt) {
  const minutes = Math.max(0, Math.floor((Date.now() - Date.parse(publishedAt)) / 60_000))
  if (minutes < 1) {
    return 'Just now'
  }
  if (minutes < 60) {
    return `${minutes} minute${minutes === 1 ? '' : 's'} ago`
  }
  const hours = Math.floor(minutes / 60)
  return `${hours} hour${hours === 1 ? '' : 's'} ago`
}

function App() {
  const [activePage, setActivePage] = useState('overview')
  const [gameState, setGameState] = useState(fallbackGameState)
  const [apiStatus, setApiStatus] = useState('loading')
  const [session, setSession] = useState(null)
  const [sessionStatus, setSessionStatus] = useState('loading')
  const [account, setAccount] = useState(null)
  const [managerName, setManagerName] = useState('')
  const [managerError, setManagerError] = useState(null)
  const [isCreatingManager, setIsCreatingManager] = useState(false)
  const [accountRefresh, setAccountRefresh] = useState(0)
  const [runeInventory, setRuneInventory] = useState(() => initialRunes.map((rune) => ({ ...rune })))
  const [equippedRunes, setEquippedRunes] = useState(() => Object.fromEntries(Object.entries(initialEquippedRunes).map(([hero, runes]) => [hero, [...runes]])))
  const [isCombatExpanded, setIsCombatExpanded] = useState(false)
  const [isSynchronizingCombat, setIsSynchronizingCombat] = useState(false)
  const [selectedSlot, setSelectedSlot] = useState(null)
  const [isUpdatingLoadout, setIsUpdatingLoadout] = useState(false)
  const [loadoutError, setLoadoutError] = useState(null)
  const [isUpdatingActivity, setIsUpdatingActivity] = useState(false)
  const [activityError, setActivityError] = useState(null)
  const [isUpdatingParty, setIsUpdatingParty] = useState(false)
  const [partyError, setPartyError] = useState(null)
  const [isCreatingParty, setIsCreatingParty] = useState(false)
  const [partyName, setPartyName] = useState('')
  const [isStartingQuest, setIsStartingQuest] = useState(false)
  const [questError, setQuestError] = useState(null)
  const [isPostingFeed, setIsPostingFeed] = useState(false)
  const [feedError, setFeedError] = useState(null)
  const [marketOrders, setMarketOrders] = useState(fallbackMarketOrders)
  const [isSubmittingMarketOrder, setIsSubmittingMarketOrder] = useState(false)
  const [marketError, setMarketError] = useState(null)
  const combatSyncInFlight = useRef(false)
  const stateRefreshInFlight = useRef(false)
  const activeQuestId = gameState.activeParty?.questState?.id
  const activeCombatStatus = gameState.activeParty?.questState?.combat?.status

  useEffect(() => {
    let cancelled = false
    let intervalId
    let activeAgencyId

    async function refreshAgencyState() {
      if (document.visibilityState !== 'visible' || stateRefreshInFlight.current) {
        return
      }

      stateRefreshInFlight.current = true
      try {
        if (!activeAgencyId) {
          return
        }

        const [rawState, orders] = await Promise.all([fetchAgencyState(activeAgencyId), fetchMarketOrders()])
        const state = mapAgencyState(rawState)
        if (cancelled) {
          return
        }

        setGameState(state)
        setRuneInventory(state.runeInventory)
        setEquippedRunes(state.equippedRunes)
        setMarketOrders(orders)
        setApiStatus('ready')
      } catch (error) {
        if (!cancelled) {
          if (error instanceof ApiRequestError && error.status === 499) {
            setSession(null)
            setSessionStatus('anonymous')
            setApiStatus('unauthenticated')
          } else if (error instanceof ApiRequestError && error.status === 403) {
            setSessionStatus('no-agency')
          } else {
            setApiStatus('unavailable')
          }
        }
      } finally {
        stateRefreshInFlight.current = false
      }
    }

    function refreshWhenVisible() {
      if (document.visibilityState === 'visible') {
        refreshAgencyState()
      }
    }

    async function initializeSession() {
      try {
        const currentSession = await fetchSession()
        if (cancelled) {
          return
        }

        setSession(currentSession)
        if (!currentSession.authenticated) {
          setSessionStatus('anonymous')
          setApiStatus('unauthenticated')
          return
        }

        const currentAccount = await fetchAccount()
        if (cancelled) {
          return
        }

        setAccount(currentAccount)
        if (!currentAccount.manager) {
          setSessionStatus('onboarding')
          return
        }

        activeAgencyId = currentAccount.agencyMemberships[0]?.agencyId
        if (!activeAgencyId) {
          setSessionStatus('no-agency')
          return
        }

        setSessionStatus('authenticated')
        await refreshAgencyState()
        if (cancelled) {
          return
        }

        intervalId = window.setInterval(refreshAgencyState, 5_000)
        document.addEventListener('visibilitychange', refreshWhenVisible)
      } catch {
        if (!cancelled) {
          setSessionStatus('unavailable')
          setApiStatus('unavailable')
        }
      }
    }

    initializeSession()

    return () => {
      cancelled = true
      if (intervalId) {
        window.clearInterval(intervalId)
      }
      document.removeEventListener('visibilitychange', refreshWhenVisible)
    }
  }, [accountRefresh])

  function applyRemoteAgencyState(rawState) {
    const state = mapAgencyState(rawState)
    setGameState(state)
    setRuneInventory(state.runeInventory)
    setEquippedRunes(state.equippedRunes)
  }

  useEffect(() => {
    if (activePage !== 'quests' || !isCombatExpanded || apiStatus !== 'ready' || !activeQuestId || activeCombatStatus !== 'in-progress') {
      return undefined
    }

    let cancelled = false
    async function synchronizeCombat() {
      if (combatSyncInFlight.current) {
        return
      }
      combatSyncInFlight.current = true
      setIsSynchronizingCombat(true)
      try {
        const state = await synchronizeQuestCombat({ agencyId: gameState.agency.id, questId: activeQuestId })
        if (!cancelled) {
          applyRemoteAgencyState(state)
        }
      } catch (error) {
        if (!cancelled) {
          setQuestError(error.message)
        }
      } finally {
        combatSyncInFlight.current = false
        if (!cancelled) {
          setIsSynchronizingCombat(false)
        }
      }
    }

    synchronizeCombat()
    const intervalId = window.setInterval(synchronizeCombat, 2_000)
    return () => {
      cancelled = true
      window.clearInterval(intervalId)
    }
  }, [activeCombatStatus, activePage, activeQuestId, apiStatus, gameState.agency.id, isCombatExpanded])

  function equipRuneLocally(rune) {
    if (!selectedSlot || rune.quantity === 0) {
      return
    }

    const { hero, slotIndex } = selectedSlot
    const heroRunes = equippedRunes[hero.alias] ?? Array(5).fill(null)
    const previousRune = heroRunes[slotIndex]
    if (previousRune?.id === rune.id) {
      setSelectedSlot(null)
      return
    }

    setEquippedRunes((allRunes) => ({
      ...allRunes,
      [hero.alias]: heroRunes.map((equippedRune, index) => index === slotIndex ? rune : equippedRune),
    }))
    setRuneInventory((runes) => runes.map((inventoryRune) => {
      const leavingInventory = inventoryRune.id === rune.id ? 1 : 0
      const returningToInventory = inventoryRune.id === previousRune?.id ? 1 : 0
      const quantityChange = returningToInventory - leavingInventory
      return quantityChange === 0 ? inventoryRune : { ...inventoryRune, quantity: inventoryRune.quantity + quantityChange }
    }))
    setSelectedSlot(null)
  }

  async function equipRune(rune) {
    if (!selectedSlot || rune.quantity === 0) {
      return
    }

    if (apiStatus !== 'ready') {
      equipRuneLocally(rune)
      return
    }

    setIsUpdatingLoadout(true)
    setLoadoutError(null)
    try {
      const { hero, slotIndex } = selectedSlot
      const state = await equipHeroRune({
        agencyId: gameState.agency.id,
        heroId: hero.id,
        slotIndex,
        runeId: rune.id,
      })
      applyRemoteAgencyState(state)
      setSelectedSlot(null)
    } catch (error) {
      setLoadoutError(error.message)
    } finally {
      setIsUpdatingLoadout(false)
    }
  }

  function unequipRuneLocally() {
    if (!selectedSlot) {
      return
    }

    const { hero, slotIndex } = selectedSlot
    const heroRunes = equippedRunes[hero.alias] ?? Array(5).fill(null)
    const previousRune = heroRunes[slotIndex]
    if (!previousRune) {
      return
    }

    setEquippedRunes((allRunes) => ({
      ...allRunes,
      [hero.alias]: heroRunes.map((rune, index) => index === slotIndex ? null : rune),
    }))
    setRuneInventory((runes) => runes.map((rune) => rune.id === previousRune.id ? { ...rune, quantity: rune.quantity + 1 } : rune))
    setSelectedSlot(null)
  }

  async function unequipRune() {
    if (!selectedSlot) {
      return
    }

    if (apiStatus !== 'ready') {
      unequipRuneLocally()
      return
    }

    setIsUpdatingLoadout(true)
    setLoadoutError(null)
    try {
      const { hero, slotIndex } = selectedSlot
      const state = await unequipHeroRune({
        agencyId: gameState.agency.id,
        heroId: hero.id,
        slotIndex,
      })
      applyRemoteAgencyState(state)
      setSelectedSlot(null)
    } catch (error) {
      setLoadoutError(error.message)
    } finally {
      setIsUpdatingLoadout(false)
    }
  }

  function changeActivityLocally(hero, activity) {
    setGameState((currentState) => {
      const heroes = currentState.heroes.map((currentHero) => currentHero.alias === hero.alias ? { ...currentHero, activity: titleCase(activity) } : currentHero)
      return {
        ...currentState,
        heroes,
        agencyHeroes: heroes.filter((currentHero) => currentHero.status === 'agency'),
      }
    })
  }

  async function updateHeroActivity(hero, activity) {
    if (apiStatus !== 'ready') {
      changeActivityLocally(hero, activity)
      return
    }

    setIsUpdatingActivity(true)
    setActivityError(null)
    try {
      const state = await changeHeroActivity({
        agencyId: gameState.agency.id,
        heroId: hero.id,
        activity,
      })
      applyRemoteAgencyState(state)
    } catch (error) {
      setActivityError(error.message)
    } finally {
      setIsUpdatingActivity(false)
    }
  }

  function startCreatingParty() {
    setPartyError(null)
    setIsCreatingParty(true)
  }

  function cancelCreatingParty() {
    setPartyName('')
    setPartyError(null)
    setIsCreatingParty(false)
  }

  async function handleCreateParty(event) {
    event.preventDefault()
    const name = partyName.trim()

    if (!name) {
      setPartyError('Enter a name for the party.')
      return
    }
    if (apiStatus !== 'ready') {
      setPartyError('Party management requires the backend connection.')
      return
    }

    setIsUpdatingParty(true)
    setPartyError(null)
    try {
      const state = await createParty({ agencyId: gameState.agency.id, name })
      applyRemoteAgencyState(state)
      setPartyName('')
      setIsCreatingParty(false)
    } catch (error) {
      setPartyError(error.message)
    } finally {
      setIsUpdatingParty(false)
    }
  }

  async function assignHeroToParty(hero, partyId) {
    if (apiStatus !== 'ready') {
      setPartyError('Party management requires the backend connection.')
      return
    }

    setIsUpdatingParty(true)
    setPartyError(null)
    try {
      const state = await addHeroToParty({
        agencyId: gameState.agency.id,
        partyId,
        heroId: hero.id,
      })
      applyRemoteAgencyState(state)
    } catch (error) {
      setPartyError(error.message)
    } finally {
      setIsUpdatingParty(false)
    }
  }

  async function removeHeroFromPreparedParty(hero, partyId) {
    if (apiStatus !== 'ready') {
      setPartyError('Party management requires the backend connection.')
      return
    }

    setIsUpdatingParty(true)
    setPartyError(null)
    try {
      const state = await removeHeroFromParty({
        agencyId: gameState.agency.id,
        partyId,
        heroId: hero.id,
      })
      applyRemoteAgencyState(state)
    } catch (error) {
      setPartyError(error.message)
    } finally {
      setIsUpdatingParty(false)
    }
  }

  async function handleStartQuest(questId, partyId) {
    if (apiStatus !== 'ready') {
      setQuestError('Starting a quest requires the backend connection.')
      return
    }

    setIsStartingQuest(true)
    setQuestError(null)
    try {
      const state = await startQuest({
        agencyId: gameState.agency.id,
        questId,
        partyId,
      })
      applyRemoteAgencyState(state)
    } catch (error) {
      setQuestError(error.message)
    } finally {
      setIsStartingQuest(false)
    }
  }

  async function handleCreateFeedPost(post) {
    setFeedError(null)
    if (apiStatus !== 'ready') {
      const authorName = post.authorType === 'AGENCY'
        ? gameState.agency.name
        : post.authorType === 'MANAGER'
          ? gameState.agency.leaderName
          : gameState.heroes.find((hero) => hero.id === post.authorId)?.name
      const item = post.itemId ? gameState.itemInventory.find((candidate) => candidate.id === post.itemId) : null
      setGameState((state) => ({
        ...state,
        feedPosts: [{ ...post, item, id: `local-post-${Date.now()}`, authorName, publishedAt: new Date().toISOString() }, ...state.feedPosts],
      }))
      return true
    }

    setIsPostingFeed(true)
    try {
      const state = await createFeedPost({ agencyId: gameState.agency.id, ...post })
      applyRemoteAgencyState(state)
      return true
    } catch (error) {
      setFeedError(error.message)
      return false
    } finally {
      setIsPostingFeed(false)
    }
  }

  async function refreshMarketOrders() {
    setMarketOrders(await fetchMarketOrders())
  }

  async function handleCreateMarketOrder(order) {
    if (apiStatus !== 'ready') {
      setMarketError('Market orders require the backend connection.')
      return false
    }

    setIsSubmittingMarketOrder(true)
    setMarketError(null)
    try {
      const state = await createMarketOrder({ agencyId: gameState.agency.id, ...order })
      applyRemoteAgencyState(state)
      await refreshMarketOrders()
      return true
    } catch (error) {
      setMarketError(error.message)
      return false
    } finally {
      setIsSubmittingMarketOrder(false)
    }
  }

  async function handleCancelMarketOrder(orderId) {
    if (apiStatus !== 'ready') {
      return
    }

    setIsSubmittingMarketOrder(true)
    setMarketError(null)
    try {
      const state = await cancelMarketOrder({ agencyId: gameState.agency.id, orderId })
      applyRemoteAgencyState(state)
      await refreshMarketOrders()
    } catch (error) {
      setMarketError(error.message)
    } finally {
      setIsSubmittingMarketOrder(false)
    }
  }

  async function handleLogout() {
    try {
      await logout()
      setSession(null)
      setAccount(null)
      setManagerName('')
      setManagerError(null)
      setSessionStatus('anonymous')
      setApiStatus('unauthenticated')
    } catch (error) {
      setMarketError(error.message)
    }
  }

  async function handleCreateManager(event) {
    event.preventDefault()
    const displayName = managerName.trim()
    if (!displayName) {
      setManagerError('Enter a manager name.')
      return
    }

    setIsCreatingManager(true)
    setManagerError(null)
    try {
      const updatedAccount = await createManager(displayName)
      setAccount(updatedAccount)
      setManagerName('')
      setSessionStatus('loading')
      setAccountRefresh((current) => current + 1)
    } catch (error) {
      setManagerError(error.message)
    } finally {
      setIsCreatingManager(false)
    }
  }

  if (sessionStatus === 'loading') {
    return <AuthenticationGate title="Checking your session" description="Connecting to Hero Association…" />
  }

  if (sessionStatus === 'anonymous') {
    return <AuthenticationGate title="Welcome to Hero Association" description="Sign in to manage your agency." onLogin={beginLogin} />
  }

  if (sessionStatus === 'onboarding') {
    return <ManagerOnboarding
      identity={session?.identity}
      managerName={managerName}
      error={managerError}
      isSubmitting={isCreatingManager}
      onManagerNameChange={setManagerName}
      onSubmit={handleCreateManager}
      onSignOut={handleLogout}
    />
  }

  if (sessionStatus === 'no-agency') {
    return <AgencyAccessGate managerName={account?.manager?.displayName} onSignOut={handleLogout} />
  }

  const battle = gameState.activeParty?.questState?.combat
  const pages = {
    overview: <Overview agency={gameState.agency} metrics={gameState.metrics} activeParty={gameState.activeParty} questHeroes={gameState.questHeroes} onNavigate={setActivePage} />,
    heroes: <Heroes agency={gameState.agency} heroes={gameState.heroes} activeParties={gameState.activeParties} agencyHeroes={gameState.agencyHeroes} preparedParties={gameState.preparedParties} runes={equippedRunes} isUpdatingActivity={isUpdatingActivity} activityError={activityError} isUpdatingParty={isUpdatingParty} partyError={partyError} isCreatingParty={isCreatingParty} partyName={partyName} onPartyNameChange={setPartyName} onCreateParty={handleCreateParty} onCancelCreateParty={cancelCreatingParty} onStartCreateParty={startCreatingParty} onSelectRuneSlot={(hero, slotIndex) => { setLoadoutError(null); setSelectedSlot({ hero, slotIndex }) }} onChangeActivity={updateHeroActivity} onAddToParty={assignHeroToParty} onRemoveFromParty={removeHeroFromPreparedParty} />,
    quests: <Quests activeParty={gameState.activeParty} activeParties={gameState.activeParties} availableQuests={gameState.availableQuests} preparedParties={gameState.preparedParties} battle={battle} isCombatExpanded={isCombatExpanded} isSynchronizingCombat={isSynchronizingCombat} isStartingQuest={isStartingQuest} questError={questError} onStartQuest={handleStartQuest} onToggleCombat={() => setIsCombatExpanded((expanded) => !expanded)} />,
    agency: <Agency agency={gameState.agency} upgrades={gameState.upgrades} runeInventory={runeInventory} itemInventory={gameState.itemInventory} />,
    market: <Market agency={gameState.agency} itemInventory={gameState.itemInventory} marketOrders={marketOrders} isSubmittingOrder={isSubmittingMarketOrder} marketError={marketError} onCreateOrder={handleCreateMarketOrder} onCancelOrder={handleCancelMarketOrder} />,
    feed: <Feed agency={gameState.agency} heroes={gameState.heroes} feedPosts={gameState.feedPosts} itemInventory={gameState.itemInventory} isPostingFeed={isPostingFeed} feedError={feedError} onCreatePost={handleCreateFeedPost} />,
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="#overview" onClick={() => setActivePage('overview')}><span className="brand__mark" aria-hidden="true">H</span><span>Hero Association</span></a>
        <nav className="navigation" aria-label="Main navigation">
          {navigation.map((item) => (
            <button className={`navigation__item ${activePage === item.id ? 'navigation__item--active' : ''}`} key={item.id} type="button" aria-current={activePage === item.id ? 'page' : undefined} onClick={() => setActivePage(item.id)}>
              <span aria-hidden="true">{item.icon}</span>{item.label}
            </button>
          ))}
        </nav>
        <div className="sidebar__bottom"><div className="player-card"><span className="player-card__avatar">{account?.manager?.displayName?.slice(0, 1).toUpperCase() ?? session?.identity?.username?.slice(0, 1).toUpperCase() ?? 'T'}</span><span><strong>{account?.manager?.displayName ?? session?.identity?.username ?? gameState.agency.leaderName}</strong><small>{account?.manager ? 'Manager' : 'Signed in'}</small></span><button className="text-button player-card__logout" type="button" onClick={handleLogout}>Sign out</button></div></div>
      </aside>
      <main className="main-content"><div className="main-content__inner">{apiStatus !== 'ready' && <p className={`api-status api-status--${apiStatus}`} role="status">{apiStatus === 'loading' ? 'Loading agency state…' : 'Backend unavailable. Showing the local fixture.'}</p>}{pages[activePage]}</div></main>
      <RuneDrawer selectedSlot={selectedSlot} runes={equippedRunes} runeInventory={runeInventory} isUpdating={isUpdatingLoadout} error={loadoutError} onClose={() => { setLoadoutError(null); setSelectedSlot(null) }} onEquipRune={equipRune} onUnequipRune={unequipRune} />
    </div>
  )
}

function AuthenticationGate({ title, description, onLogin }) {
  return (
    <main className="authentication-gate">
      <section className="authentication-gate__panel">
        <span className="brand__mark" aria-hidden="true">H</span>
        <p className="eyebrow">Hero Association</p>
        <h1>{title}</h1>
        <p>{description}</p>
        {onLogin && <button className="button button--primary" type="button" onClick={onLogin}>Sign in</button>}
      </section>
    </main>
  )
}

function ManagerOnboarding({ identity, managerName, error, isSubmitting, onManagerNameChange, onSubmit, onSignOut }) {
  return (
    <main className="authentication-gate">
      <section className="authentication-gate__panel manager-onboarding">
        <span className="brand__mark" aria-hidden="true">H</span>
        <p className="eyebrow">Welcome{identity?.username ? `, ${identity.username}` : ''}</p>
        <h1>Choose your manager name</h1>
        <p>Your manager name is public and unique. You can use 3 to 100 characters.</p>
        <form onSubmit={onSubmit}>
          <label className="manager-onboarding__field"><span>Manager name</span><input value={managerName} minLength="3" maxLength="100" autoComplete="nickname" autoFocus disabled={isSubmitting} onChange={(event) => onManagerNameChange(event.target.value)} placeholder="Wayfinder" /></label>
          {error && <p className="manager-onboarding__error" role="alert">{error}</p>}
          <button className="button button--primary" type="submit" disabled={isSubmitting}>{isSubmitting ? 'Creating…' : 'Create manager'}</button>
        </form>
        <button className="text-button manager-onboarding__sign-out" type="button" disabled={isSubmitting} onClick={onSignOut}>Sign out</button>
      </section>
    </main>
  )
}

function AgencyAccessGate({ managerName, onSignOut }) {
  return (
    <main className="authentication-gate">
      <section className="authentication-gate__panel agency-access-gate">
        <span className="brand__mark" aria-hidden="true">H</span>
        <p className="eyebrow">Manager {managerName}</p>
        <h1>No agency yet</h1>
        <p>You need an agency membership before you can manage heroes, quests, and inventory. Creating an agency and invitations are the next features.</p>
        <button className="text-button manager-onboarding__sign-out" type="button" onClick={onSignOut}>Sign out</button>
      </section>
    </main>
  )
}

export default App
