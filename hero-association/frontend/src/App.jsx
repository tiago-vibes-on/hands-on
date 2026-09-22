import { lazy, Suspense, useEffect, useState } from 'react'
import { addHeroToParty, changeHeroActivity, createParty, equipHeroRune, fetchAgencyState, removeHeroFromParty, unequipHeroRune } from './api/agency'
import { createBattle } from './combat/battle'
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
  { name: 'Brom Ironwall', alias: 'Ironwall', role: 'Warrior', level: 1, healthRecovery: 10, manaRecovery: 2, stamina: 58, color: 'gold', partyId: 'broken-pass-party', status: 'quest' },
  { name: 'Elara Moonweaver', alias: 'Moonweaver', role: 'Mage', level: 1, magicLevel: 15, healthRecovery: 2, manaRecovery: 10, spells: mageSpells, stamina: 24, color: 'violet', partyId: 'broken-pass-party', status: 'quest' },
  { name: 'Kael Swiftarrow', alias: 'Swiftarrow', role: 'Archer', level: 1, healthRecovery: 6, manaRecovery: 6, stamina: 91, color: 'teal', partyId: 'broken-pass-party', status: 'quest' },
  { name: 'Dorian Oakshield', alias: 'Oakshield', role: 'Warrior', level: 1, healthRecovery: 10, manaRecovery: 2, color: 'gold', activity: 'Training', status: 'agency' },
  { name: 'Runa Emberveil', alias: 'Emberveil', role: 'Mage', level: 1, healthRecovery: 2, manaRecovery: 10, color: 'violet', activity: 'Resting', status: 'agency' },
  { name: 'Lyra Hawkeye', alias: 'Hawkeye', role: 'Archer', level: 1, healthRecovery: 6, manaRecovery: 6, color: 'teal', activity: 'Training', status: 'agency' },
]

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
  },
  heroIds: ['ironwall', 'moonweaver', 'swiftarrow'],
}

const fallbackQuestHeroes = fallbackHeroes.filter((hero) => hero.partyId === fallbackParty.id)
const fallbackAgencyHeroes = fallbackHeroes.filter((hero) => hero.status === 'agency')

const fallbackMetrics = [
  { label: 'Gold', value: '2,480', detail: '+240 this week', icon: 'G' },
  { label: 'Reputation', value: '340', detail: 'Rank: Trusted', icon: 'R' },
  { label: 'Party capacity', value: '71 / 120', detail: '49 remaining', icon: 'C' },
]

const fallbackUpgrades = [
  { name: 'Training', level: 4, detail: 'Improves available hero training.' },
  { name: 'Rest', level: 3, detail: 'Restores stamina faster.' },
  { name: 'Size', level: 4, detail: 'Room for heroes and facilities.' },
  { name: 'Reputation', level: 3, detail: 'Unlocks better opportunities.' },
  { name: 'Intelligence', level: 3, detail: 'Reveals quest risks and rewards.' },
]

const heroColors = {
  WARRIOR: 'gold',
  MAGE: 'violet',
  ARCHER: 'teal',
}

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

function mapAgencyState(state) {
  const parties = state.parties.map((party) => ({
    ...party,
    quest: party.quest?.title,
    questState: party.quest,
  }))
  const heroes = state.heroes.map((hero) => ({
    id: hero.id,
    name: hero.name,
    alias: hero.alias,
    role: titleCase(hero.heroClass),
    level: hero.level,
    magicLevel: hero.magicLevel,
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
  const activeParty = parties.find((party) => party.questState?.status === 'IN_PROGRESS') ?? parties[0]
  const questHeroes = heroes.filter((hero) => hero.partyId === activeParty?.id)
  const preparedParties = parties.filter((party) => !party.questState || party.questState.status !== 'IN_PROGRESS')
  const agencyHeroes = heroes.filter((hero) => hero.status === 'agency' && !hero.partyId)
  const runeInventory = state.runeInventory.map(({ rune, quantity }) => ({ ...mapRune(rune), quantity }))
  const equippedRunes = Object.fromEntries(heroes.map((hero) => [hero.alias, hero.runeSlots]))
  const upgrades = [
    { name: 'Training', level: state.agency.levels.training, detail: 'Improves available hero training.' },
    { name: 'Rest', level: state.agency.levels.rest, detail: 'Restores stamina faster.' },
    { name: 'Size', level: state.agency.levels.size, detail: 'Room for heroes and facilities.' },
    { name: 'Reputation', level: state.agency.levels.reputation, detail: 'Unlocks better opportunities.' },
    { name: 'Intelligence', level: state.agency.levels.intelligence, detail: 'Reveals quest risks and rewards.' },
  ]

  return {
    agency: state.agency,
    heroes,
    parties,
    activeParty,
    questHeroes,
    preparedParties,
    agencyHeroes,
    runeInventory,
    equippedRunes,
    upgrades,
    metrics: [
      { label: 'Gold', value: state.agency.gold.toLocaleString(), detail: 'Agency funds', icon: 'G' },
      { label: 'Reputation', value: state.agency.reputation.toLocaleString(), detail: 'Agency standing', icon: 'R' },
      { label: 'Active party', value: `${questHeroes.length} heroes`, detail: activeParty?.name ?? 'No active party', icon: 'P' },
    ],
  }
}

const fallbackGameState = {
  agency: { name: 'Dawnwatch Agency', leaderName: 'Tiago', levels: { agency: 4 } },
  heroes: fallbackHeroes,
  activeParty: fallbackParty,
  questHeroes: fallbackQuestHeroes,
  preparedParties: [],
  agencyHeroes: fallbackAgencyHeroes,
  runeInventory: initialRunes.map((rune) => ({ ...rune })),
  equippedRunes: initialEquippedRunes,
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
          <div><p className="eyebrow">{hero.role}</p><h2>{hero.alias}</h2><p className="hero-card__name">{hero.name} · Level {hero.level}</p>{hero.magicLevel && <p className="hero-card__magic-level">Magic Level {hero.magicLevel}</p>}<p className="hero-card__recovery">Recovery: +{hero.healthRecovery} health/s · +{hero.manaRecovery} mana/s</p></div>
          {hero.status === 'quest' ? (
            <>
              <div className="hero-card__details"><span>Experience</span><strong>{experienceGain(hero.stamina)}% XP gain from creatures</strong></div>
              <StaminaBar value={hero.stamina} />
            </>
          ) : <div className="hero-card__agency-activity"><span>At the agency</span><strong>{hero.activity}</strong><p>{hero.activity === 'Training' ? 'Improving for the next quest without a recovery bonus.' : 'Recovering stamina, health, and mana at 2× speed.'}</p><div className="hero-card__activity-actions" role="group" aria-label={`${hero.alias} agency activity`}><button className={`activity-button ${hero.activity === 'Training' ? 'activity-button--active' : ''}`} type="button" disabled={isUpdatingActivity || hero.activity === 'Training'} onClick={() => onChangeActivity(hero, 'TRAINING')}>Training</button><button className={`activity-button ${hero.activity === 'Resting' ? 'activity-button--active' : ''}`} type="button" disabled={isUpdatingActivity || hero.activity === 'Resting'} onClick={() => onChangeActivity(hero, 'RESTING')}>Resting</button></div>{partyId ? <button className="text-button hero-card__party-action" type="button" disabled={isUpdatingParty} onClick={() => onRemoveFromParty(hero, partyId)}>Remove from party</button> : preparedParties?.length > 0 && <label className="hero-card__party-select"><span>Assign to party</span><select defaultValue="" disabled={isUpdatingParty} onChange={(event) => { const selectedPartyId = event.target.value; event.target.value = ''; if (selectedPartyId) { onAddToParty(hero, selectedPartyId) } }}><option value="" disabled>Select a party</option>{preparedParties.map((party) => <option value={party.id} key={party.id}>{party.name}</option>)}</select></label>}</div>}
          <HeroLoadoutSlots hero={hero} runes={runes} onSelectRuneSlot={onSelectRuneSlot} />
        </article>
      ))}
    </div>
  )
}

function Heroes({ agency, heroes, activeParty, questHeroes, agencyHeroes, preparedParties, runes, isUpdatingActivity, activityError, isUpdatingParty, partyError, isCreatingParty, partyName, onPartyNameChange, onCreateParty, onCancelCreateParty, onStartCreateParty, onSelectRuneSlot, onChangeActivity, onAddToParty, onRemoveFromParty }) {
  return (
    <>
      <PageHeading eyebrow={agency.name} title="Heroes" description="Prepare parties at the agency, then send one on a quest." action={<button className="button button--primary" type="button" onClick={onStartCreateParty}>Create party</button>} />
      <section className="hero-group" aria-labelledby="quest-party-heading">
        <div className="hero-group__header party-card"><div><p className="eyebrow">Active party</p><h2 id="quest-party-heading">{activeParty.name}</h2><p>On quest: {activeParty.quest} · {questHeroes.length} heroes.</p></div><span className="status status--progress">{questHeroes.length} in party</span></div>
        <HeroCards roster={questHeroes} runes={runes} onSelectRuneSlot={onSelectRuneSlot} />
      </section>
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

function Quests({ activeParty, battle, combatLog, isCombatExpanded, onBattleChange, onCombatEvent, onResetBattle, onToggleCombat }) {
  const quest = activeParty.questState
  const defeatedCreatures = battle.status === 'victory' ? quest.creaturesRequired : quest.creaturesDefeated

  function handleQuestCardKeyDown(event) {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      onToggleCombat()
    }
  }

  return (
    <>
      <PageHeading eyebrow="Quest board" title="Quests" description="Choose a party that can finish the job and return safely." action={<button className="button button--primary" type="button">Find quests</button>} />
      <section className="quest-list">
        <article className={`panel quest-card quest-card--active ${isCombatExpanded ? 'quest-card--expanded' : ''}`}>
          <div className="quest-card__trigger" role="button" tabIndex="0" aria-expanded={isCombatExpanded} onClick={onToggleCombat} onKeyDown={handleQuestCardKeyDown}>
            <div><span className="status status--progress">{battle.status === 'in-progress' ? 'In progress' : battle.status}</span><h2>{quest.title}</h2><p>Defeat {quest.creaturesRequired} {quest.creatureName.toLowerCase()} to complete this quest.</p></div>
            <div className="quest-card__facts"><span className="quest-card__fact"><b>{defeatedCreatures} / {quest.creaturesRequired}</b><small>defeated</small></span><span className="quest-card__fact"><b>{activeParty.heroIds?.length ?? 0}</b><small>heroes</small></span><span className="quest-card__fact"><b>Active</b><small>quest</small></span><span className="quest-card__expand-icon" aria-hidden="true">{isCombatExpanded ? '−' : '+'}</span></div>
          </div>
          {isCombatExpanded && (
            <div className="combat-panel">
              <div className="combat-panel__header">
                <div><p className="eyebrow">Current encounter</p></div>
                {battle.status === 'in-progress' ? <span className="combat-panel__hint">Each combatant attacks on its own timer.</span> : <button className="text-button" type="button" onClick={onResetBattle}>Reset encounter</button>}
              </div>
              <Suspense fallback={<div className="combat-scene combat-scene--loading">Preparing the battlefield…</div>}>
                <CombatScene battle={battle} onBattleChange={onBattleChange} onCombatEvent={onCombatEvent} />
              </Suspense>
              <div className="combat-log" aria-live="polite">
                <strong>Combat log</strong>
                <ul>{combatLog.map((event, index) => <li key={`${event}-${index}`}>{event}</li>)}</ul>
              </div>
            </div>
          )}
        </article>
        <article className="panel quest-card">
          <div className="quest-card__content">
            <div><span className="status">Available</span><h2>Lost Courier</h2><p>Find the missing courier in the old forest.</p></div>
            <div className="quest-card__facts"><span className="quest-card__fact"><b>1–2</b><small>heroes</small></span><span className="quest-card__fact"><b>30m</b><small>estimated</small></span><span className="quest-card__fact"><b>85</b><small>gold reward</small></span></div>
          </div>
        </article>
      </section>
    </>
  )
}

function Agency({ agency, upgrades, runeInventory }) {
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
        <div className="panel__header"><div><p className="eyebrow">Agency storage</p><h2>Rune inventory</h2></div><span className="status">{runeInventory.reduce((total, rune) => total + rune.quantity, 0)} runes</span></div>
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

function Market() {
  return (
    <>
      <PageHeading eyebrow="Community exchange" title="Market" description="Place buy and sell offers. A 10% fee applies when offers match." action={<button className="button button--primary" type="button">Create offer</button>} />
      <section className="market-grid">
        <article className="panel offer-panel">
          <div className="panel__header"><h2>Buy offers</h2><span className="status">4 offers</span></div>
          <div className="offer-row"><span>Magic Crystal</span><strong>98 gold</strong><small>12 available</small></div>
          <div className="offer-row"><span>Iron Ingot</span><strong>16 gold</strong><small>48 available</small></div>
        </article>
        <article className="panel offer-panel">
          <div className="panel__header"><h2>Sell offers</h2><span className="status">6 offers</span></div>
          <div className="offer-row"><span>Magic Crystal</span><strong>105 gold</strong><small>8 available</small></div>
          <div className="offer-row"><span>Iron Ingot</span><strong>21 gold</strong><small>31 available</small></div>
        </article>
      </section>
    </>
  )
}

function Feed({ agency, heroes }) {
  const moonweaver = heroes.find((hero) => hero.alias === 'Moonweaver') ?? heroes[0]

  return (
    <>
      <PageHeading eyebrow="Community" title="Feed" description="Updates from agencies, managers, and the heroes who make their names known." action={<button className="button button--primary" type="button">Write post</button>} />
      <section className="feed-list">
        <article className="panel feed-post">
          <div className="feed-post__author"><span className="feed-post__mark">D</span><div><strong>{agency.name}</strong><small>12 minutes ago</small></div></div>
          <p>The party has reached Broken Pass. The road will be open again soon.</p>
          <div className="feed-post__item">Quest in progress · Trolls at Broken Pass</div>
        </article>
        <article className="panel feed-post">
          <div className="feed-post__author"><HeroAvatar hero={moonweaver} size="small" /><div><strong>{moonweaver.name}</strong><small>1 hour ago</small></div></div>
          <p>Rested, prepared, and ready for whatever waits beyond the pass.</p>
        </article>
      </section>
    </>
  )
}

function App() {
  const [activePage, setActivePage] = useState('overview')
  const [gameState, setGameState] = useState(fallbackGameState)
  const [apiStatus, setApiStatus] = useState('loading')
  const [runeInventory, setRuneInventory] = useState(() => initialRunes.map((rune) => ({ ...rune })))
  const [equippedRunes, setEquippedRunes] = useState(() => Object.fromEntries(Object.entries(initialEquippedRunes).map(([hero, runes]) => [hero, [...runes]])))
  const [battle, setBattle] = useState(() => createBattle(1, initialEquippedRunes))
  const [combatLog, setCombatLog] = useState([])
  const [isCombatExpanded, setIsCombatExpanded] = useState(false)
  const [selectedSlot, setSelectedSlot] = useState(null)
  const [isUpdatingLoadout, setIsUpdatingLoadout] = useState(false)
  const [loadoutError, setLoadoutError] = useState(null)
  const [isUpdatingActivity, setIsUpdatingActivity] = useState(false)
  const [activityError, setActivityError] = useState(null)
  const [isUpdatingParty, setIsUpdatingParty] = useState(false)
  const [partyError, setPartyError] = useState(null)
  const [isCreatingParty, setIsCreatingParty] = useState(false)
  const [partyName, setPartyName] = useState('')

  useEffect(() => {
    let cancelled = false

    async function loadAgencyState() {
      try {
        const state = mapAgencyState(await fetchAgencyState())
        if (cancelled) {
          return
        }

        setGameState(state)
        setRuneInventory(state.runeInventory)
        setEquippedRunes(state.equippedRunes)
        setBattle((currentBattle) => createBattle(currentBattle.encounterId + 1, state.equippedRunes))
        setApiStatus('ready')
      } catch {
        if (!cancelled) {
          setApiStatus('unavailable')
        }
      }
    }

    loadAgencyState()

    return () => {
      cancelled = true
    }
  }, [])

  function addCombatEvent(message) {
    setCombatLog((events) => [message, ...events].slice(0, 4))
  }

  function resetBattle() {
    setBattle((currentBattle) => createBattle(currentBattle.encounterId + 1, equippedRunes))
    setCombatLog([])
  }

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

  const pages = {
    overview: <Overview agency={gameState.agency} metrics={gameState.metrics} activeParty={gameState.activeParty} questHeroes={gameState.questHeroes} onNavigate={setActivePage} />,
    heroes: <Heroes agency={gameState.agency} heroes={gameState.heroes} activeParty={gameState.activeParty} questHeroes={gameState.questHeroes} agencyHeroes={gameState.agencyHeroes} preparedParties={gameState.preparedParties} runes={equippedRunes} isUpdatingActivity={isUpdatingActivity} activityError={activityError} isUpdatingParty={isUpdatingParty} partyError={partyError} isCreatingParty={isCreatingParty} partyName={partyName} onPartyNameChange={setPartyName} onCreateParty={handleCreateParty} onCancelCreateParty={cancelCreatingParty} onStartCreateParty={startCreatingParty} onSelectRuneSlot={(hero, slotIndex) => { setLoadoutError(null); setSelectedSlot({ hero, slotIndex }) }} onChangeActivity={updateHeroActivity} onAddToParty={assignHeroToParty} onRemoveFromParty={removeHeroFromPreparedParty} />,
    quests: <Quests activeParty={gameState.activeParty} battle={battle} combatLog={combatLog} isCombatExpanded={isCombatExpanded} onBattleChange={setBattle} onCombatEvent={addCombatEvent} onResetBattle={resetBattle} onToggleCombat={() => setIsCombatExpanded((expanded) => !expanded)} />,
    agency: <Agency agency={gameState.agency} upgrades={gameState.upgrades} runeInventory={runeInventory} />,
    market: <Market />,
    feed: <Feed agency={gameState.agency} heroes={gameState.heroes} />,
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
        <div className="sidebar__bottom"><div className="player-card"><span className="player-card__avatar">T</span><span><strong>{gameState.agency.leaderName}</strong><small>Agency leader</small></span></div></div>
      </aside>
      <main className="main-content"><div className="main-content__inner">{apiStatus !== 'ready' && <p className={`api-status api-status--${apiStatus}`} role="status">{apiStatus === 'loading' ? 'Loading agency state…' : 'Backend unavailable. Showing the local fixture.'}</p>}{pages[activePage]}</div></main>
      <RuneDrawer selectedSlot={selectedSlot} runes={equippedRunes} runeInventory={runeInventory} isUpdating={isUpdatingLoadout} error={loadoutError} onClose={() => { setLoadoutError(null); setSelectedSlot(null) }} onEquipRune={equipRune} onUnequipRune={unequipRune} />
    </div>
  )
}

export default App
