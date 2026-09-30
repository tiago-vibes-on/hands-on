import { lazy, Suspense, useEffect, useRef, useState } from 'react'
import { addHeroToParty, ApiRequestError, beginLogin, beginRegistration, cancelMarketOrder, changeHeroActivity, createAgency, createFeedPost, createManager, createMarketOrder, createParty, equipHeroRune, fetchAccount, fetchAgencyState, fetchMarketOrders, fetchRecruits, fetchSession, logout, recruitHero, recruitHeroForAgency, removeHeroFromParty, setHeroBorrowingFee, startQuest, transferGold, unequipHeroRune } from './api/agency'
import { initialEquippedRunes, initialRunes } from './data/inventory'
import { mageSpells } from './data/spells'
import './App.css'

const MapPage = lazy(() => import('./expedition/MapPage'))
const expeditionEnabled = import.meta.env.VITE_EXPEDITION_ENABLED === 'true'

const navigation = [
  { id: 'overview', label: 'Overview', icon: '◇' },
  { id: 'heroes', label: 'Heroes', icon: '♙' },
  { id: 'quests', label: 'Quests', icon: '⚔' },
  ...(expeditionEnabled ? [{ id: 'map', label: 'Map', icon: '◈' }] : []),
  { id: 'agency', label: 'Agency', icon: '⌂' },
  { id: 'market', label: 'Market', icon: '⇄' },
  { id: 'feed', label: 'Feed', icon: '◌' },
]

const fallbackHeroes = [
  { name: 'Brom Ironwall', alias: 'Ironwall', role: 'Warrior', level: 1, currentHealth: 300, maxHealth: 300, currentMana: 50, maxMana: 50, healthRecovery: 10, manaRecovery: 2, stamina: 58, color: 'gold', partyId: 'broken-pass-party', status: 'quest' },
  { name: 'Elara Moonweaver', alias: 'Moonweaver', role: 'Mage', level: 1, magicLevel: 15, currentHealth: 100, maxHealth: 100, currentMana: 500, maxMana: 500, healthRecovery: 2, manaRecovery: 10, spells: mageSpells, stamina: 24, color: 'violet', partyId: 'broken-pass-party', status: 'quest' },
  { name: 'Kael Swiftarrow', alias: 'Swiftarrow', role: 'Archer', level: 1, currentHealth: 200, maxHealth: 200, currentMana: 200, maxMana: 200, healthRecovery: 6, manaRecovery: 6, stamina: 91, color: 'teal', partyId: 'broken-pass-party', status: 'quest' },
  { name: 'Dorian Oakshield', alias: 'Oakshield', role: 'Warrior', level: 1, borrowingFeeGold: 0, currentHealth: 300, maxHealth: 300, currentMana: 50, maxMana: 50, healthRecovery: 10, manaRecovery: 2, color: 'gold', activity: 'Training', status: 'agency' },
  { name: 'Runa Emberveil', alias: 'Emberveil', role: 'Mage', level: 1, borrowingFeeGold: 25, currentHealth: 100, maxHealth: 100, currentMana: 500, maxMana: 500, healthRecovery: 2, manaRecovery: 10, color: 'violet', activity: 'Resting', status: 'agency' },
  { name: 'Lyra Hawkeye', alias: 'Hawkeye', role: 'Archer', level: 1, borrowingFeeGold: 100, currentHealth: 200, maxHealth: 200, currentMana: 200, maxMana: 200, healthRecovery: 6, manaRecovery: 6, color: 'teal', activity: 'Training', status: 'agency' },
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
  { id: 'ironridge-buy-magic', ownerType: 'AGENCY', ownerId: 'ironridge-exchange', ownerName: 'Ironridge Exchange', itemId: 'magic-crystal', itemCode: 'magic-crystal', itemName: 'Magic Crystal', itemSymbol: '◇', side: 'BUY', quantityRemaining: 2, priceGoldPerItem: 100, createdAt: '2026-01-01T11:50:00Z' },
  { id: 'ironridge-sell-iron', ownerType: 'AGENCY', ownerId: 'ironridge-exchange', ownerName: 'Ironridge Exchange', itemId: 'iron-ingot', itemCode: 'iron-ingot', itemName: 'Iron Ingot', itemSymbol: '▰', side: 'SELL', quantityRemaining: 12, priceGoldPerItem: 16, createdAt: '2026-01-01T11:55:00Z' },
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
  const heroes = [...state.heroes, ...(state.personalHeroes ?? [])].map((hero) => ({
    id: hero.id,
    name: hero.name,
    alias: hero.alias,
    role: titleCase(hero.heroClass),
    level: hero.level,
    magicLevel: hero.magicLevel,
    meleeLevel: hero.meleeLevel,
    distanceLevel: hero.distanceLevel,
    shieldLevel: hero.shieldLevel,
    ownerManagerId: hero.ownerManagerId,
    borrowingFeeGold: hero.borrowingFeeGold ?? 0,
    currentHealth: hero.currentHealth,
    maxHealth: hero.maxHealth,
    currentMana: hero.currentMana,
    maxMana: hero.maxMana,
    healthRecovery: hero.healthRecoveryPerSecond,
    manaRecovery: hero.manaRecoveryPerSecond,
    stamina: hero.stamina,
    color: heroColors[hero.heroClass],
    partyId: hero.partyId,
    status: hero.activity === 'ON_QUEST' ? 'quest' : hero.ownerManagerId ? 'personal' : 'agency',
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
  const resolvedQuests = state.quests.filter((quest) => quest.status === 'COMPLETED' || quest.status === 'FAILED')
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
    resolvedQuests,
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
  agency: { id: 'dawnwatch-agency', name: 'Dawnwatch Agency', leaderId: 'user1', leaderName: 'User 1', levels: { agency: 4 } },
  heroes: fallbackHeroes,
  activeParty: fallbackParty,
  activeParties: [fallbackParty],
  questHeroes: fallbackQuestHeroes,
  preparedParties: [],
  availableQuests: fallbackAvailableQuests,
  resolvedQuests: [],
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
  const isLoadoutLocked = hero.status === 'quest'

  return (
    <div className="hero-loadout" role="group" aria-label={`${hero.alias} rune slots`}>
      <div className="hero-loadout__group">
        <span>Runes</span>
        <div className="hero-loadout__slots" role="group" aria-label="Five rune slots">
          {runeSlots.map((rune, index) => (
            <button className={`hero-loadout__slot hero-loadout__slot--rune ${rune ? 'hero-loadout__slot--equipped' : ''}`} type="button" key={index} disabled={isLoadoutLocked} title={isLoadoutLocked ? 'Rune loadout is locked while this hero is on a quest.' : undefined} aria-label={`${hero.alias} rune slot ${index + 1}${rune ? `: ${rune.name}` : ', empty'}${isLoadoutLocked ? '. Locked while on quest.' : ''}`} onClick={() => onSelectRuneSlot(hero, index)}>
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
  const quest = activeParty?.questState
  const progress = quest ? (quest.creaturesDefeated / quest.creaturesRequired) * 100 : 0

  return (
    <>
      <PageHeading
        eyebrow={agency.name}
        title="Your agency is ready"
        description={quest ? 'Prepare your heroes, send a party on a quest, and grow your name.' : 'Your new agency is ready for its first heroes.'}
        action={<button className="button button--primary" type="button" onClick={() => onNavigate('heroes')}>View heroes</button>}
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
        {activeParty ? (
          <article className="panel active-quest">
            <div className="panel__header">
              <div><p className="eyebrow">Active quest</p><h2>{activeParty.quest}</h2></div>
              <span className="status status--progress">In progress</span>
            </div>
            <p className="active-quest__description">Defeat {quest.creaturesRequired} {quest.creatureName.toLowerCase()} to complete this quest.</p>
            <div className="quest-progress">
              <div className="quest-progress__labels"><span>Creatures defeated</span><strong>{quest.creaturesDefeated} / {quest.creaturesRequired}</strong></div>
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
        ) : (
          <article className="panel active-quest empty-state">
            <div className="panel__header"><div><p className="eyebrow">Next step</p><h2>No heroes yet</h2></div></div>
            <p className="active-quest__description">Hero recruiting is the next gameplay feature. Your agency starts empty by design.</p>
          </article>
        )}
        <article className="panel agency-level">
          <div className="panel__header">
            <div><p className="eyebrow">Agency progress</p><h2>Agency level {agency.levels.agency}</h2></div>
            <button className="text-button" type="button" onClick={() => onNavigate('agency')}>Manage</button>
          </div>
          <div className="level-orb" aria-label={`Agency level ${agency.levels.agency}`}>{agency.levels.agency}</div>
          <p>Next level unlocks more training and rest upgrades.</p>
          <div className="level-progress"><span style={{ width: '0%' }} /></div>
          <small>Agency experience will be earned through quests.</small>
        </article>
      </section>
      <section className="panel roster-panel">
        <div className="panel__header">
          <div><p className="eyebrow">{activeParty ? 'Active quest' : 'Agency roster'}</p><h2>{activeParty ? 'Party' : 'Heroes'}</h2></div>
          <button className="text-button" type="button" onClick={() => onNavigate('heroes')}>View all</button>
        </div>
        <div className="hero-list">
          {questHeroes.length > 0 ? questHeroes.map((hero) => (
            <article className="hero-row" key={hero.alias}>
              <HeroAvatar hero={hero} />
              <div className="hero-row__identity"><strong>{hero.alias}</strong><span>{hero.role} · Level {hero.level}</span></div>
              <div className="hero-row__training">Earning experience from creatures</div>
              <StaminaBar value={hero.stamina} />
            </article>
          )) : <p className="empty-state__message">No heroes have been recruited yet.</p>}
        </div>
      </section>
    </>
  )
}

function BorrowingFeeEditor({ hero, onSetBorrowingFee, isUpdatingBorrowingFee }) {
  const [feeGold, setFeeGold] = useState(String(hero.borrowingFeeGold ?? 0))

  function handleSubmit(event) {
    event.preventDefault()
    const amount = Number(feeGold)
    if (feeGold.trim() !== '' && Number.isSafeInteger(amount) && amount >= 0) {
      onSetBorrowingFee(hero, amount)
    }
  }

  return <form className="hero-card__fee-editor" onSubmit={handleSubmit}><label><span>Fee per quest (gold)</span><input type="number" min="0" step="1" value={feeGold} disabled={isUpdatingBorrowingFee} onChange={(event) => setFeeGold(event.target.value)} /></label><button className="button button--secondary" type="submit" disabled={isUpdatingBorrowingFee || feeGold.trim() === '' || !Number.isSafeInteger(Number(feeGold)) || Number(feeGold) < 0 || Number(feeGold) === hero.borrowingFeeGold}>Save fee</button></form>
}

function HeroCards({ roster, runes, onSelectRuneSlot, onChangeActivity, isUpdatingActivity, preparedParties, onAddToParty, onRemoveFromParty, partyId, isUpdatingParty, onSetBorrowingFee, isUpdatingBorrowingFee }) {
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
          ) : hero.ownerManagerId ? (
            <div className="hero-card__agency-activity">
              <span>Personal hero</span><strong>{hero.activity}</strong>
              <p>Only this hero's Manager can change the party.</p>
              {partyId && <button className="text-button hero-card__party-action" type="button" disabled={isUpdatingParty} onClick={() => onRemoveFromParty(hero, partyId)}>Remove from party</button>}
            </div>
          ) : <div className="hero-card__agency-activity"><span>At the agency</span><strong>{hero.activity}</strong><p>{hero.activity === 'Training' ? 'Recovering health and mana at the base class rate.' : 'Recovering health and mana at 2× the base class rate. Stamina recovery is not implemented yet.'}</p><div className="hero-card__activity-actions" role="group" aria-label={`${hero.alias} agency activity`}><button className={`activity-button ${hero.activity === 'Training' ? 'activity-button--active' : ''}`} type="button" disabled={isUpdatingActivity || hero.activity === 'Training'} onClick={() => onChangeActivity(hero, 'TRAINING')}>Training</button><button className={`activity-button ${hero.activity === 'Resting' ? 'activity-button--active' : ''}`} type="button" disabled={isUpdatingActivity || hero.activity === 'Resting'} onClick={() => onChangeActivity(hero, 'RESTING')}>Resting</button></div>{partyId ? <button className="text-button hero-card__party-action" type="button" disabled={isUpdatingParty} onClick={() => onRemoveFromParty(hero, partyId)}>Remove from party</button> : preparedParties?.length > 0 && <label className="hero-card__party-select"><span>Assign to party</span><select defaultValue="" disabled={isUpdatingParty} onChange={(event) => { const selectedPartyId = event.target.value; event.target.value = ''; if (selectedPartyId) { onAddToParty(hero, selectedPartyId) } }}><option value="" disabled>Select a party</option>{preparedParties.map((party) => <option value={party.id} key={party.id}>{party.name}</option>)}</select></label>}</div>}
          {!hero.ownerManagerId && <div className="hero-card__fee"><span>Borrowing fee</span><strong>{hero.borrowingFeeGold ?? 0} gold per quest</strong>{onSetBorrowingFee && <BorrowingFeeEditor key={`${hero.id}:${hero.borrowingFeeGold}`} hero={hero} onSetBorrowingFee={onSetBorrowingFee} isUpdatingBorrowingFee={isUpdatingBorrowingFee} />}</div>}
          {!hero.ownerManagerId && <HeroLoadoutSlots hero={hero} runes={runes} onSelectRuneSlot={onSelectRuneSlot} />}
        </article>
      ))}
    </div>
  )
}

function Heroes({ agency, manager, heroes, activeParties, agencyHeroes, preparedParties, runes, availableRecruits, isRecruitingHero, recruitmentError, isUpdatingActivity, activityError, isUpdatingParty, partyError, isCreatingParty, partyName, onPartyNameChange, onCreateParty, onCancelCreateParty, onStartCreateParty, onRecruitHero, onSelectRuneSlot, onChangeActivity, onAddToParty, onRemoveFromParty, onSetBorrowingFee, isUpdatingBorrowingFee, borrowingFeeError }) {
  const personalHeroes = heroes.filter((hero) => hero.ownerManagerId === manager?.id)

  return (
    <>
      <PageHeading eyebrow={agency.name} title="Heroes" description="Recruit heroes, prepare parties at the agency, then send one on a quest." action={<button className="button button--primary" type="button" disabled={heroes.length === 0} onClick={onStartCreateParty}>Create party</button>} />
      <section className="hero-group personal-roster" aria-labelledby="personal-roster-heading">
        <div className="hero-group__header"><div><p className="eyebrow">Your assets</p><h2 id="personal-roster-heading">Personal heroes</h2><p>These heroes belong to you, separately from the agency. Assign them to your prepared party to send them on a quest.</p></div><span className="status">{manager?.gold ?? 0} personal gold</span></div>
        <div className="personal-roster__list">
          {personalHeroes.map((hero) => (
            <article className="panel personal-roster__hero" key={hero.id}>
              <HeroAvatar hero={hero} />
              <div>
                <p className="eyebrow">{hero.role} · Level {hero.level}</p>
                <h3>{hero.name}</h3>
                <p>Health {hero.currentHealth} / {hero.maxHealth} · Mana {hero.currentMana} / {hero.maxMana} · Stamina {hero.stamina}%</p>
                <small>Melee {hero.meleeLevel} · Distance {hero.distanceLevel} · Magic {hero.magicLevel} · Shield {hero.shieldLevel}</small>
                {hero.status === 'quest' ? <span className="status status--progress">On quest</span>
                  : hero.partyId ? <button className="text-button personal-roster__action" type="button" disabled={isUpdatingParty} onClick={() => onRemoveFromParty(hero, hero.partyId)}>Remove from party</button>
                  : preparedParties.length > 0 ? <label className="personal-roster__assign"><span>Assign to party</span><select defaultValue="" disabled={isUpdatingParty} onChange={(event) => { const partyId = event.target.value; event.target.value = ''; if (partyId) { onAddToParty(hero, partyId) } }}><option value="" disabled>Select a party</option>{preparedParties.map((party) => <option key={party.id} value={party.id}>{party.name}</option>)}</select></label>
                  : <small className="personal-roster__hint">Create a party to assign this hero.</small>}
              </div>
            </article>
          ))}
        </div>
        <p className="personal-roster__inventory">Personal inventory: {manager?.items?.length ?? 0} item types · {manager?.runes?.length ?? 0} rune types</p>
      </section>
      <section className="panel recruitment-board" aria-labelledby="recruitment-heading">
        <div className="panel__header"><div><p className="eyebrow">Recruitment board</p><h2 id="recruitment-heading">Available heroes</h2><p className="recruitment-board__description">The initial recruits are free. Recruit for yourself by default, or for the agency if you lead it.</p></div><span className="status">{availableRecruits.length} available</span></div>
        {recruitmentError && <p className="inline-error recruitment-board__error" role="alert">{recruitmentError}</p>}
        {availableRecruits.length > 0 ? <div className="recruitment-board__list">{availableRecruits.map((recruit) => <article className="recruitment-card" key={recruit.id}><HeroAvatar hero={{ ...recruit, color: heroColors[recruit.heroClass] }} /><div className="recruitment-card__identity"><p className="eyebrow">{titleCase(recruit.heroClass)}</p><h3>{recruit.alias}</h3><p>{recruit.name} · Level {recruit.level}</p></div><div className="recruitment-card__resources"><span>Health {recruit.health} · Mana {recruit.mana}</span><small>Recovery: +{recruit.healthRecoveryPerSecond} health/s · +{recruit.manaRecoveryPerSecond} mana/s</small></div><div className="recruitment-card__actions"><button className="button button--primary" type="button" disabled={isRecruitingHero} aria-label={`Recruit ${recruit.alias} for me`} onClick={() => onRecruitHero(recruit.id)}>{isRecruitingHero ? 'Recruiting…' : 'Recruit for me'}</button>{agency.leaderId === manager?.id && <button className="button button--secondary" type="button" disabled={isRecruitingHero} aria-label={`Recruit ${recruit.alias} for agency`} onClick={() => onRecruitHero(recruit.id, 'agency')}>{isRecruitingHero ? 'Recruiting…' : 'Recruit for agency'}</button>}</div></article>)}</div> : <p className="recruitment-board__empty">No recruits are currently available.</p>}
      </section>
      {activeParties.map((party) => {
        const partyHeroes = heroes.filter((hero) => hero.partyId === party.id)
        return <section className="hero-group" aria-labelledby={`party-${party.id}`} key={party.id}><div className="hero-group__header party-card"><div><p className="eyebrow">Active party</p><h2 id={`party-${party.id}`}>{party.name}</h2><p>On quest: {party.quest} · {partyHeroes.length} heroes.</p></div><span className="status status--progress">{partyHeroes.length} in party</span></div><HeroCards roster={partyHeroes} runes={runes} onSelectRuneSlot={onSelectRuneSlot} /></section>
      })}
      {isCreatingParty && <form className="party-form" onSubmit={onCreateParty}><label><span>Party name</span><input value={partyName} maxLength="100" autoFocus disabled={isUpdatingParty} onChange={(event) => onPartyNameChange(event.target.value)} placeholder="Forest Scouts" /></label><div className="party-form__actions"><button className="button button--primary" type="submit" disabled={isUpdatingParty}>{isUpdatingParty ? 'Creating…' : 'Create party'}</button><button className="text-button" type="button" disabled={isUpdatingParty} onClick={onCancelCreateParty}>Cancel</button></div></form>}
      {partyError && <p className="inline-error" role="alert">{partyError}</p>}
      {borrowingFeeError && <p className="inline-error" role="alert">{borrowingFeeError}</p>}
      {preparedParties.map((party) => {
        const partyHeroes = heroes.filter((hero) => hero.partyId === party.id)
        const heroCountLabel = partyHeroes.length === 1 ? 'hero is' : 'heroes are'
        return <section className="hero-group" aria-labelledby={`party-${party.id}`} key={party.id}><div className="hero-group__header party-card"><div><p className="eyebrow">Prepared party</p><h2 id={`party-${party.id}`}>{party.name}</h2><p>{partyHeroes.length === 0 ? 'No heroes assigned yet.' : `${partyHeroes.length} ${heroCountLabel} ready at the agency.`}</p></div><span className="status">{partyHeroes.length} in party</span></div><HeroCards roster={partyHeroes} runes={runes} onSelectRuneSlot={onSelectRuneSlot} onChangeActivity={onChangeActivity} isUpdatingActivity={isUpdatingActivity} partyId={party.id} onRemoveFromParty={onRemoveFromParty} isUpdatingParty={isUpdatingParty} onSetBorrowingFee={agency.leaderId === manager?.id ? onSetBorrowingFee : undefined} isUpdatingBorrowingFee={isUpdatingBorrowingFee} /></section>
      })}
      <section className="hero-group" aria-labelledby="agency-heroes-heading">
        <div className="hero-group__header"><div><p className="eyebrow">Agency roster</p><h2 id="agency-heroes-heading">Unassigned heroes</h2><p>Heroes can train or rest while they wait for a prepared party.</p></div><span className="status">{agencyHeroes.length} unassigned</span></div>
        {activityError && <p className="inline-error" role="alert">{activityError}</p>}
        <HeroCards roster={agencyHeroes} runes={runes} onSelectRuneSlot={onSelectRuneSlot} onChangeActivity={onChangeActivity} isUpdatingActivity={isUpdatingActivity} isUpdatingParty={isUpdatingParty} preparedParties={preparedParties} onAddToParty={onAddToParty} onSetBorrowingFee={agency.leaderId === manager?.id ? onSetBorrowingFee : undefined} isUpdatingBorrowingFee={isUpdatingBorrowingFee} />
      </section>
    </>
  )
}

function AvailableQuestCard({ quest, preparedParties, heroes, managerGold, isStartingQuest, onStartQuest }) {
  const [partyId, setPartyId] = useState('')
  const hasPreparedParty = preparedParties.length > 0
  const selectedParty = preparedParties.find((party) => party.id === partyId)
  const borrowingFeeGold = (selectedParty?.heroIds ?? []).reduce((total, heroId) => {
    const hero = heroes.find((candidate) => candidate.id === heroId)
    return total + (hero && !hero.ownerManagerId ? hero.borrowingFeeGold ?? 0 : 0)
  }, 0)
  const canAfford = managerGold >= borrowingFeeGold

  return (
    <article className="panel quest-card">
      <div className="quest-card__content">
        <div><span className="status">Available</span><h2>{quest.title}</h2><p>{quest.description}</p></div>
        <div className="quest-card__facts"><span className="quest-card__fact"><b>{quest.minimumHeroes}–{quest.maximumHeroes}</b><small>heroes</small></span><span className="quest-card__fact"><b>{quest.durationMinutes}m</b><small>estimated</small></span><span className="quest-card__fact"><b>{quest.goldReward}</b><small>gold reward</small></span></div>
      </div>
      <div className="quest-card__start">
        {hasPreparedParty ? <><label><span>Prepared party</span><select value={partyId} disabled={isStartingQuest} onChange={(event) => setPartyId(event.target.value)}><option value="" disabled>Select a party</option>{preparedParties.map((party) => <option value={party.id} key={party.id}>{party.name} · {party.heroIds.length} heroes</option>)}</select></label>{selectedParty && <p className="quest-card__borrowing-fee">Agency hero fee: <strong>{borrowingFeeGold} gold</strong> at quest start · Your gold: {managerGold}{!canAfford && <span className="inline-error"> · Insufficient personal gold</span>}</p>}<button className="button button--primary" type="button" disabled={isStartingQuest || !partyId || !canAfford} onClick={() => onStartQuest(quest.id, partyId, borrowingFeeGold)}>{isStartingQuest ? 'Starting…' : 'Start quest'}</button></> : <p>Create a prepared party before starting this quest.</p>}
      </div>
    </article>
  )
}

function ResolvedQuestCard({ quest }) {
  const completed = quest.status === 'COMPLETED'

  return (
    <article className="panel quest-card quest-card--resolved">
      <div className="quest-card__content">
        <div><span className="status">{completed ? 'Completed' : 'Failed'}</span><h2>{quest.title}</h2><p>{completed ? 'The party completed this quest and returned to the agency.' : 'The party was defeated and returned to the agency to recover.'}</p></div>
        <div className="quest-card__facts"><span className="quest-card__fact"><b>{quest.creaturesDefeated} / {quest.creaturesRequired}</b><small>defeated</small></span><span className="quest-card__fact"><b>{quest.goldReward}</b><small>gold pending</small></span></div>
      </div>
    </article>
  )
}

function Quests({ activeParty, activeParties, availableQuests, resolvedQuests, preparedParties, heroes, managerGold, isStartingQuest, questError, onStartQuest }) {
  const quest = activeParty?.questState
  return (
    <>
      <PageHeading eyebrow="Quest board" title="Quests" description={expeditionEnabled ? "Quest objectives and progress live here. Visit Map to watch battles." : "Quest objectives and progress live here."} />
      {questError && <p className="inline-error" role="alert">{questError}</p>}
      <section className="quest-list">
        {activeParty ? (
          <article className="panel quest-card quest-card--active">
            <div className="quest-card__content">
              <div><span className="status status--progress">In progress</span><h2>{quest.title}</h2><p>{quest.description}</p><p>Objective: Defeat {quest.creaturesRequired} {quest.creatureName.toLowerCase()}.</p></div>
              <div className="quest-card__facts"><span className="quest-card__fact"><b>{quest.creaturesDefeated} / {quest.creaturesRequired}</b><small>defeated</small></span><span className="quest-card__fact"><b>{activeParty.heroIds?.length ?? 0}</b><small>heroes</small></span><span className="quest-card__fact"><b>Active</b><small>quest</small></span></div>
            </div>
          </article>
        ) : <article className="panel quest-card empty-state"><div><p className="eyebrow">No active quest</p><h2>No quest in progress</h2><p>{expeditionEnabled ? "Choose an available quest below, or visit Map to battle without a quest." : "Choose an available quest below."}</p></div></article>}
        {activeParties.slice(1).map((party) => <article className="panel quest-card" key={party.id}><div className="quest-card__content"><div><span className="status status--progress">In progress</span><h2>{party.quest}</h2><p>{party.questState.description}</p></div><div className="quest-card__facts"><span className="quest-card__fact"><b>{party.questState.creaturesDefeated} / {party.questState.creaturesRequired}</b><small>defeated</small></span><span className="quest-card__fact"><b>{party.heroIds.length}</b><small>heroes</small></span></div></div></article>)}
        {availableQuests.map((availableQuest) => <AvailableQuestCard quest={availableQuest} preparedParties={preparedParties} heroes={heroes} managerGold={managerGold} isStartingQuest={isStartingQuest} onStartQuest={onStartQuest} key={availableQuest.id} />)}
        {resolvedQuests.map((resolvedQuest) => <ResolvedQuestCard quest={resolvedQuest} key={resolvedQuest.id} />)}
      </section>
    </>
  )
}

function Agency({ agency, manager, canTransferAgencyGold, upgrades, runeInventory, itemInventory, isTransferringGold, transferError, transferNotice, onTransferGold }) {
  const itemQuantity = itemInventory.reduce((total, item) => total + item.quantity, 0)
  const runeQuantity = runeInventory.reduce((total, rune) => total + rune.quantity, 0)
  const [depositAgencyName, setDepositAgencyName] = useState(agency.name)
  const [recipientManagerName, setRecipientManagerName] = useState('')
  const [depositAmount, setDepositAmount] = useState('')
  const [withdrawAmount, setWithdrawAmount] = useState('')

  async function submitDeposit(event) {
    event.preventDefault()
    if (await onTransferGold({ direction: 'MANAGER_TO_AGENCY', agencyName: depositAgencyName, amountGold: Number(depositAmount) })) {
      setDepositAmount('')
    }
  }

  async function submitWithdrawal(event) {
    event.preventDefault()
    if (await onTransferGold({ direction: 'AGENCY_TO_MANAGER', agencyName: agency.name, managerName: recipientManagerName, amountGold: Number(withdrawAmount) })) {
      setWithdrawAmount('')
      setRecipientManagerName('')
    }
  }

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
      <section className="panel gold-transfer">
        <div className="panel__header"><div><p className="eyebrow">Treasury</p><h2>Move gold</h2></div><span className="status">{agency.gold} agency gold</span></div>
        <p className="gold-transfer__hint">Transfers move existing gold without a fee or agency earnings share. Enter the exact agency or Manager name.</p>
        <div className="gold-transfer__forms">
          <form className="gold-transfer__form" aria-label="Send personal gold to an agency" onSubmit={submitDeposit}>
            <h3>Send to an agency</h3>
            <p>Your wallet: {manager?.gold ?? 0} gold. You can send to any agency.</p>
            <label><span>Agency name</span><input type="text" value={depositAgencyName} maxLength={100} required disabled={isTransferringGold} onChange={(event) => setDepositAgencyName(event.target.value)} /></label>
            <label><span>Gold amount</span><input type="number" min="1" max={manager?.gold ?? 0} step="1" value={depositAmount} required disabled={isTransferringGold} onChange={(event) => setDepositAmount(event.target.value)} /></label>
            <button className="button button--primary" type="submit" disabled={isTransferringGold || (manager?.gold ?? 0) < 1}>Send gold</button>
          </form>
          {canTransferAgencyGold && <form className="gold-transfer__form" aria-label="Send agency gold to a Manager" onSubmit={submitWithdrawal}>
            <h3>Send from agency</h3>
            <p>Only the agency leader can send treasury gold to any Manager, including themselves.</p>
            <label><span>Recipient Manager name</span><input type="text" value={recipientManagerName} maxLength={100} required disabled={isTransferringGold} onChange={(event) => setRecipientManagerName(event.target.value)} /></label>
            <label><span>Gold amount</span><input type="number" min="1" max={agency.gold} step="1" value={withdrawAmount} required disabled={isTransferringGold} onChange={(event) => setWithdrawAmount(event.target.value)} /></label>
            <button className="button button--secondary" type="submit" disabled={isTransferringGold || agency.gold < 1}>Send gold</button>
          </form>}
        </div>
        {transferError && <p className="inline-error" role="alert">{transferError}</p>}
        {transferNotice && <p className="gold-transfer__notice" role="status">{transferNotice}</p>}
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

function Market({ agency, manager, canTradeAgency, itemInventory, marketOrders, isSubmittingOrder, marketError, onCreateOrder, onCancelOrder }) {
  const [ownerType, setOwnerType] = useState('MANAGER')
  const [side, setSide] = useState('BUY')
  const [itemId, setItemId] = useState('')
  const [quantity, setQuantity] = useState(1)
  const [priceGoldPerItem, setPriceGoldPerItem] = useState(100)
  const buyOrders = marketOrders.filter((order) => order.side === 'BUY')
  const sellOrders = marketOrders.filter((order) => order.side === 'SELL')
  const effectiveOwnerType = canTradeAgency ? ownerType : 'MANAGER'
  const knownItems = new Map()
  for (const item of itemInventory) knownItems.set(item.id, { id: item.id, name: item.name })
  for (const order of marketOrders) {
    if (!knownItems.has(order.itemId)) knownItems.set(order.itemId, { id: order.itemId, name: order.itemName })
  }
  for (const item of manager?.items ?? []) {
    if (!knownItems.has(item.id)) knownItems.set(item.id, { id: item.id, name: item.code })
  }
  const ownerInventory = effectiveOwnerType === 'AGENCY' ? itemInventory : (manager?.items ?? [])
  const availableItems = side === 'SELL'
    ? ownerInventory.filter((item) => item.quantity > 0).map((item) => ({ id: item.id, name: knownItems.get(item.id)?.name ?? item.code, quantity: item.quantity }))
    : [...knownItems.values()]
  const selectedItemId = availableItems.some((item) => item.id === itemId) ? itemId : (availableItems[0]?.id ?? '')

  async function submitOrder(event) {
    event.preventDefault()
    if (!selectedItemId) return
    const wasCreated = await onCreateOrder({ ownerType: effectiveOwnerType, side, itemId: selectedItemId, quantity, priceGoldPerItem })
    if (wasCreated) {
      setQuantity(1)
      setPriceGoldPerItem(100)
    }
  }

  function orderList(orders, emptyMessage) {
    if (orders.length === 0) {
      return <p className="offer-panel__empty">{emptyMessage}</p>
    }

    return orders.map((order) => {
      const canCancel = (order.ownerType === 'MANAGER' && order.ownerId === manager?.id)
        || (order.ownerType === 'AGENCY' && canTradeAgency && order.ownerId === agency.id)
      return <div className="offer-row" key={order.id}><span><b>{order.itemSymbol}</b>{order.itemName}<small>{order.ownerName} · {order.ownerType === 'MANAGER' ? 'Manager' : 'Agency'}</small></span><strong>{order.priceGoldPerItem} gold</strong><small>{order.quantityRemaining} available</small>{canCancel && <button className="text-button" type="button" disabled={isSubmittingOrder} onClick={() => onCancelOrder(order.id)}>Cancel</button>}</div>
    })
  }

  return (
    <>
      <PageHeading eyebrow="Community exchange" title="Market" description="Place buy and sell offers. When an offer matches, the seller pays a 10% fee." />
      <form className="panel market-form" onSubmit={submitOrder}>
        <label><span>Trading as</span><select value={effectiveOwnerType} disabled={isSubmittingOrder} onChange={(event) => setOwnerType(event.target.value)}><option value="MANAGER">{manager?.displayName ?? 'Your Manager'} · {manager?.gold ?? 0} gold</option>{canTradeAgency && <option value="AGENCY">{agency.name} · {agency.gold} gold</option>}</select></label>
        <label><span>Order type</span><select value={side} disabled={isSubmittingOrder} onChange={(event) => setSide(event.target.value)}><option value="BUY">Buy</option><option value="SELL">Sell</option></select></label>
        <label><span>Item</span><select value={selectedItemId} disabled={isSubmittingOrder} onChange={(event) => setItemId(event.target.value)}>{availableItems.map((item) => <option key={item.id} value={item.id}>{item.name}{side === 'SELL' ? ` · ${item.quantity} available` : ''}</option>)}</select></label>
        <label><span>Quantity</span><input type="number" min="1" value={quantity} disabled={isSubmittingOrder} onChange={(event) => setQuantity(Math.max(1, Number(event.target.value) || 1))} /></label>
        <label><span>Gold per item</span><input type="number" min="1" value={priceGoldPerItem} disabled={isSubmittingOrder} onChange={(event) => setPriceGoldPerItem(Math.max(1, Number(event.target.value) || 1))} /></label>
        <button className="button button--primary" type="submit" disabled={isSubmittingOrder || !selectedItemId}>{isSubmittingOrder ? 'Placing…' : 'Place order'}</button>
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
  const [agencyName, setAgencyName] = useState('')
  const [agencyError, setAgencyError] = useState(null)
  const [isCreatingAgency, setIsCreatingAgency] = useState(false)
  const [accountRefresh, setAccountRefresh] = useState(0)
  const [runeInventory, setRuneInventory] = useState(() => initialRunes.map((rune) => ({ ...rune })))
  const [equippedRunes, setEquippedRunes] = useState(() => Object.fromEntries(Object.entries(initialEquippedRunes).map(([hero, runes]) => [hero, [...runes]])))
  const [selectedSlot, setSelectedSlot] = useState(null)
  const [isUpdatingLoadout, setIsUpdatingLoadout] = useState(false)
  const [loadoutError, setLoadoutError] = useState(null)
  const [isUpdatingActivity, setIsUpdatingActivity] = useState(false)
  const [activityError, setActivityError] = useState(null)
  const [isUpdatingParty, setIsUpdatingParty] = useState(false)
  const [partyError, setPartyError] = useState(null)
  const [isUpdatingBorrowingFee, setIsUpdatingBorrowingFee] = useState(false)
  const [borrowingFeeError, setBorrowingFeeError] = useState(null)
  const [isCreatingParty, setIsCreatingParty] = useState(false)
  const [partyName, setPartyName] = useState('')
  const [isStartingQuest, setIsStartingQuest] = useState(false)
  const [questError, setQuestError] = useState(null)
  const [isPostingFeed, setIsPostingFeed] = useState(false)
  const [feedError, setFeedError] = useState(null)
  const [marketOrders, setMarketOrders] = useState(fallbackMarketOrders)
  const [isSubmittingMarketOrder, setIsSubmittingMarketOrder] = useState(false)
  const [marketError, setMarketError] = useState(null)
  const [isTransferringGold, setIsTransferringGold] = useState(false)
  const [transferError, setTransferError] = useState(null)
  const [transferNotice, setTransferNotice] = useState(null)
  const [availableRecruits, setAvailableRecruits] = useState([])
  const [isRecruitingHero, setIsRecruitingHero] = useState(false)
  const [recruitmentError, setRecruitmentError] = useState(null)
  const stateRefreshInFlight = useRef(false)

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

        const [rawState, orders, recruits, currentAccount] = await Promise.all([fetchAgencyState(activeAgencyId), fetchMarketOrders(), fetchRecruits(), fetchAccount()])
        const state = mapAgencyState(rawState)
        if (cancelled) {
          return
        }

        setAccount(currentAccount)
        setGameState(state)
        setRuneInventory(state.runeInventory)
        setEquippedRunes(state.equippedRunes)
        setMarketOrders(orders)
        setAvailableRecruits(recruits)
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

  async function handleRecruitHero(recruitId, owner = 'personal') {
    if (apiStatus !== 'ready') {
      setRecruitmentError('Recruiting heroes requires the backend connection.')
      return
    }

    setIsRecruitingHero(true)
    setRecruitmentError(null)
    try {
      if (owner === 'agency') {
        await recruitHeroForAgency({ agencyId: gameState.agency.id, recruitId })
      } else {
        await recruitHero(recruitId)
      }
      setAvailableRecruits((recruits) => recruits.filter((recruit) => recruit.id !== recruitId))
      const [state, updatedAccount] = await Promise.all([
        fetchAgencyState(gameState.agency.id),
        fetchAccount(),
      ])
      applyRemoteAgencyState(state)
      setAccount(updatedAccount)
    } catch (error) {
      setRecruitmentError(error.message)
    } finally {
      setIsRecruitingHero(false)
    }
  }

  async function updateHeroBorrowingFee(hero, feeGold) {
    if (apiStatus !== 'ready') {
      setBorrowingFeeError('Changing borrowing fees requires the backend connection.')
      return
    }

    setIsUpdatingBorrowingFee(true)
    setBorrowingFeeError(null)
    try {
      const state = await setHeroBorrowingFee({
        agencyId: gameState.agency.id,
        heroId: hero.id,
        feeGold,
      })
      applyRemoteAgencyState(state)
    } catch (error) {
      setBorrowingFeeError(error.message)
    } finally {
      setIsUpdatingBorrowingFee(false)
    }
  }

  async function handleStartQuest(questId, partyId, expectedBorrowingFeeGold) {
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
        expectedBorrowingFeeGold,
      })
      applyRemoteAgencyState(state)
      try {
        setAccount(await fetchAccount())
      } catch {
        setQuestError('Quest started, but your wallet could not be refreshed. Reload to see its current balance.')
      }
    } catch (error) {
      if (error.status === 409) {
        try {
          const [state, updatedAccount] = await Promise.all([fetchAgencyState(gameState.agency.id), fetchAccount()])
          applyRemoteAgencyState(state)
          setAccount(updatedAccount)
        } catch {
          // Keep the original rejection visible even if refreshing fails.
        }
      }
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

  async function handleTransferGold(transfer) {
    if (apiStatus !== 'ready') {
      setTransferError('Gold transfers require the backend connection.')
      return false
    }
    if (!Number.isSafeInteger(transfer.amountGold) || transfer.amountGold < 1) {
      setTransferError('Enter a positive whole number of gold.')
      return false
    }

    setIsTransferringGold(true)
    setTransferError(null)
    setTransferNotice(null)
    try {
      const result = await transferGold(transfer)
      const [state, updatedAccount] = await Promise.all([fetchAgencyState(gameState.agency.id), fetchAccount()])
      applyRemoteAgencyState(state)
      setAccount(updatedAccount)
      setTransferNotice(result.direction === 'MANAGER_TO_AGENCY'
        ? `Moved ${result.amountGold} gold to ${result.agencyName}.`
        : `Moved ${result.amountGold} gold from ${result.agencyName} to ${result.managerName}.`)
      return true
    } catch (error) {
      setTransferError(error.message)
      return false
    } finally {
      setIsTransferringGold(false)
    }
  }

  async function handleCreateMarketOrder(order) {
    if (apiStatus !== 'ready') {
      setMarketError('Market orders require the backend connection.')
      return false
    }

    setIsSubmittingMarketOrder(true)
    setMarketError(null)
    try {
      await createMarketOrder({ ...order, agencyId: order.ownerType === 'AGENCY' ? gameState.agency.id : null })
      const [state, updatedAccount] = await Promise.all([fetchAgencyState(gameState.agency.id), fetchAccount()])
      applyRemoteAgencyState(state)
      setAccount(updatedAccount)
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
      await cancelMarketOrder({ orderId })
      const [state, updatedAccount] = await Promise.all([fetchAgencyState(gameState.agency.id), fetchAccount()])
      applyRemoteAgencyState(state)
      setAccount(updatedAccount)
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

  async function handleCreateAgency(event) {
    event.preventDefault()
    const name = agencyName.trim()
    if (!name) {
      setAgencyError('Enter an agency name.')
      return
    }

    setIsCreatingAgency(true)
    setAgencyError(null)
    try {
      await createAgency(name)
      setAgencyName('')
      setSessionStatus('loading')
      setAccountRefresh((current) => current + 1)
    } catch (error) {
      setAgencyError(error.message)
    } finally {
      setIsCreatingAgency(false)
    }
  }

  if (sessionStatus === 'loading') {
    return <AuthenticationGate title="Checking your session" description="Connecting to Hero Association…" />
  }

  if (sessionStatus === 'anonymous') {
    return <AuthenticationGate title="Welcome to Hero Association" description="Sign in or create an account to manage your agency." onLogin={beginLogin} onRegistration={beginRegistration} />
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
    return <AgencyAccessGate managerName={account?.manager?.displayName} agencyName={agencyName} error={agencyError} isSubmitting={isCreatingAgency} onAgencyNameChange={setAgencyName} onSubmit={handleCreateAgency} onSignOut={handleLogout} />
  }

  const pages = {
    overview: <Overview agency={gameState.agency} metrics={gameState.metrics} activeParty={gameState.activeParty} questHeroes={gameState.questHeroes} onNavigate={setActivePage} />,
    heroes: <Heroes agency={gameState.agency} manager={account?.manager} heroes={gameState.heroes} activeParties={gameState.activeParties} agencyHeroes={gameState.agencyHeroes} preparedParties={gameState.preparedParties.filter((party) => party.ownerManagerId === account?.manager?.id)} runes={equippedRunes} availableRecruits={availableRecruits} isRecruitingHero={isRecruitingHero} recruitmentError={recruitmentError} isUpdatingActivity={isUpdatingActivity} activityError={activityError} isUpdatingParty={isUpdatingParty} partyError={partyError} isCreatingParty={isCreatingParty} partyName={partyName} onPartyNameChange={setPartyName} onCreateParty={handleCreateParty} onCancelCreateParty={cancelCreatingParty} onStartCreateParty={startCreatingParty} onRecruitHero={handleRecruitHero} onSelectRuneSlot={(hero, slotIndex) => { setLoadoutError(null); setSelectedSlot({ hero, slotIndex }) }} onChangeActivity={updateHeroActivity} onAddToParty={assignHeroToParty} onRemoveFromParty={removeHeroFromPreparedParty} onSetBorrowingFee={updateHeroBorrowingFee} isUpdatingBorrowingFee={isUpdatingBorrowingFee} borrowingFeeError={borrowingFeeError} />,
    quests: <Quests activeParty={gameState.activeParty} activeParties={gameState.activeParties} availableQuests={gameState.availableQuests} resolvedQuests={gameState.resolvedQuests} preparedParties={gameState.preparedParties.filter((party) => party.ownerManagerId === account?.manager?.id)} heroes={gameState.heroes} managerGold={account?.manager?.gold ?? 0} isStartingQuest={isStartingQuest} questError={questError} onStartQuest={handleStartQuest} />,
    ...(expeditionEnabled ? { map: <Suspense fallback={<p role="status">Loading Map…</p>}><MapPage key={gameState.agency.id} agencyId={gameState.agency.id} managerId={account?.manager?.id} heroes={gameState.heroes} preparedParties={gameState.preparedParties} /></Suspense> } : {}),
    agency: <Agency agency={gameState.agency} manager={account?.manager} canTransferAgencyGold={account?.agencyMemberships?.some((membership) => membership.agencyId === gameState.agency.id && membership.role === 'LEADER')} upgrades={gameState.upgrades} runeInventory={runeInventory} itemInventory={gameState.itemInventory} isTransferringGold={isTransferringGold} transferError={transferError} transferNotice={transferNotice} onTransferGold={handleTransferGold} />,
    market: <Market agency={gameState.agency} manager={account?.manager} canTradeAgency={account?.agencyMemberships?.some((membership) => membership.agencyId === gameState.agency.id && membership.role === 'LEADER')} itemInventory={gameState.itemInventory} marketOrders={marketOrders} isSubmittingOrder={isSubmittingMarketOrder} marketError={marketError} onCreateOrder={handleCreateMarketOrder} onCancelOrder={handleCancelMarketOrder} />,
    feed: <Feed agency={gameState.agency} heroes={gameState.heroes.filter((hero) => !hero.ownerManagerId)} feedPosts={gameState.feedPosts} itemInventory={gameState.itemInventory} isPostingFeed={isPostingFeed} feedError={feedError} onCreatePost={handleCreateFeedPost} />,
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
        <div className="sidebar__bottom"><div className="player-card"><span className="player-card__avatar">{account?.manager?.displayName?.slice(0, 1).toUpperCase() ?? session?.identity?.username?.slice(0, 1).toUpperCase() ?? 'U'}</span><span><strong>{account?.manager?.displayName ?? session?.identity?.username ?? gameState.agency.leaderName}</strong><small>{account?.manager ? 'Manager' : 'Signed in'}</small></span><button className="text-button player-card__logout" type="button" onClick={handleLogout}>Sign out</button></div></div>
      </aside>
      <main className="main-content"><div className="main-content__inner">{apiStatus !== 'ready' && <p className={`api-status api-status--${apiStatus}`} role="status">{apiStatus === 'loading' ? 'Loading agency state…' : 'Backend unavailable. Showing the local fixture.'}</p>}{pages[activePage]}</div></main>
      <RuneDrawer selectedSlot={selectedSlot} runes={equippedRunes} runeInventory={runeInventory} isUpdating={isUpdatingLoadout} error={loadoutError} onClose={() => { setLoadoutError(null); setSelectedSlot(null) }} onEquipRune={equipRune} onUnequipRune={unequipRune} />
    </div>
  )
}

function AuthenticationGate({ title, description, onLogin, onRegistration }) {
  return (
    <main className="authentication-gate">
      <section className="authentication-gate__panel">
        <span className="brand__mark" aria-hidden="true">H</span>
        <p className="eyebrow">Hero Association</p>
        <h1>{title}</h1>
        <p>{description}</p>
        {onLogin && <div className="authentication-gate__actions">
          <button className="button button--primary" type="button" onClick={onLogin}>Sign in</button>
          {onRegistration && <button className="button button--secondary" type="button" onClick={onRegistration}>Create account</button>}
        </div>}
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

function AgencyAccessGate({ managerName, agencyName, error, isSubmitting, onAgencyNameChange, onSubmit, onSignOut }) {
  return (
    <main className="authentication-gate">
      <section className="authentication-gate__panel agency-access-gate">
        <span className="brand__mark" aria-hidden="true">H</span>
        <p className="eyebrow">Manager {managerName}</p>
        <h1>Create your agency</h1>
        <p>Start with a Level 1 agency. Your three starter heroes remain yours, not agency assets.</p>
        <form onSubmit={onSubmit}>
          <label className="manager-onboarding__field"><span>Agency name</span><input value={agencyName} minLength="3" maxLength="100" autoComplete="organization" autoFocus disabled={isSubmitting} onChange={(event) => onAgencyNameChange(event.target.value)} placeholder="Wayfinder Guild" /></label>
          {error && <p className="manager-onboarding__error" role="alert">{error}</p>}
          <button className="button button--primary" type="submit" disabled={isSubmitting}>{isSubmitting ? 'Creating…' : 'Create agency'}</button>
        </form>
        <button className="text-button manager-onboarding__sign-out" type="button" disabled={isSubmitting} onClick={onSignOut}>Sign out</button>
      </section>
    </main>
  )
}

export default App
