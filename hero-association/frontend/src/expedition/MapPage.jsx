import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiRequestError } from '../api/agency'
import { commandExpedition, connectExpedition, fetchActiveExpedition, fetchMaps, startExpedition, newUuidV7 } from '../api/expedition'
import { readPending, savePending, clearPending, definitiveFailure } from '../api/pendingCommand'
import CombatScene from '../combat/CombatScene'
import { shouldAutoContinue, spellAvailability, toLoadoutHeroes, toPhaserBattle } from './expeditionCombat'
import './MapPage.css'

const phases = {
  FIGHTING: 'In battle',
  AWAITING_CONTINUE: 'Encounter complete',
  DUNGEON_COMPLETED: 'Dungeon complete',
  WIPED: 'Party defeated',
  SETTLEMENT_PENDING: 'Returning to agency',
}

const skills = [['MELEE', 'Melee'], ['DISTANCE', 'Distance'], ['MAGIC', 'Magic'], ['SHIELD', 'Shield']]

async function sendAction({ action, agencyId, managerId, partyId, mapId, expeditionId, stateVersion },
  acceptRun, setError, setPendingAction, inFlightRef) {
  if (inFlightRef.current) return
  inFlightRef.current = action
  setPendingAction(action)
  setError(null)
  const key = `hero-association:expedition-entry:${managerId}`
  try {
    const intent = action === 'start' ? readPending(key) ?? savePending(key, { agencyId, partyId, mapId, expeditionId: newUuidV7(), commandId: newUuidV7() }) : null
    const current = action === 'start'
      ? await startExpedition(intent)
      : await commandExpedition({ expeditionId, action, expectedVersion: stateVersion })
    acceptRun(current)
    if (action === 'start') clearPending(key)
  } catch (failure) {
    if (failure instanceof ApiRequestError && (failure.status === 409 || failure.status === 404)) {
      try { acceptRun(await fetchActiveExpedition()) } catch { /* Keep the last known snapshot. */ }
    }
    if (action === 'start' && definitiveFailure(failure)) clearPending(key)
    setError(failure.message)
  } finally {
    inFlightRef.current = null
    setPendingAction(null)
  }
}

function FightView({ run, knownHeroes, lastBattle }) {
  const currentBattle = toPhaserBattle(run, knownHeroes)
  const displayed = currentBattle ? { fightId: run.fight.fightId, battle: currentBattle }
    : run.lastOutcome?.fightId === lastBattle?.fightId
      ? { fightId: lastBattle.fightId, battle: toPhaserBattle(lastBattle.run, knownHeroes) } : null
  const loadoutHeroes = displayed?.battle.heroes ?? toLoadoutHeroes(run, knownHeroes)
  const combatTime = displayed?.battle.currentTimeMilliseconds ?? 0
  return <section className="panel map-fight" aria-label="Current encounter">
    <div className="map-fight__heading">
      <div><p className="eyebrow">{run.map.name} · Floor {run.floor} · Encounter {run.encounterIndex}</p><h2>{phases[run.phase] ?? run.phase}</h2></div>
      {currentBattle && <span className="map-fight__time">{Math.floor(currentBattle.currentTimeMilliseconds / 1000)}s</span>}
    </div>
    {run.lastOutcome && <p className="map-fight__outcome">Last fight: {run.lastOutcome.status === "HERO_VICTORY" ? "Victory" : "Defeat"}</p>}
    {displayed ? <div className="map-fight__canvas-scroll" role="region" aria-label="Battle field" tabIndex={0}>
      <CombatScene key={displayed.fightId} battle={displayed.battle} />
    </div>
      : <p className="map-fight__outcome">Waiting for the next encounter.</p>}
    <section className="map-loadout" aria-label="Party loadout">
      <h3>Party loadout</h3>
      <div className="map-loadout__heroes">
        {loadoutHeroes.map((hero) => <article className="map-loadout__hero" key={hero.id}>
          <h4>{hero.name}</h4>
          <p>Runes</p>
          <ul>{hero.runes.map((rune, slot) => rune && <li key={slot}>
            <span aria-hidden="true">{rune.symbol}</span> <strong>{rune.name}</strong>
            <small>{rune.stats}</small>
          </li>)}</ul>
          {!hero.runes.some(Boolean) && <small>No runes equipped</small>}
          {hero.spells.length > 0 && <><p>Spells</p><ul>{hero.spells.map((spell) => <li key={spell.id}>
            <span aria-hidden="true">{spell.symbol}</span> <strong>{spell.name}</strong>
            <small>{spell.target} · Magic Level {spell.requiredMagicLevel} · {spell.manaCost} mana · {spell.cooldown / 1_000}s cooldown</small>
            <small>{spellAvailability(spell, hero, combatTime)}</small>
          </li>)}</ul></>}
        </article>)}
      </div>
    </section>
  </section>
}

function ProgressView({ run, itemInventory, runeInventory }) {
  const itemNames = new Map(itemInventory.map((item) => [item.id, item.name]))
  const runeNames = new Map(runeInventory.map((rune) => [rune.id, rune.name]))
  const carriedItems = Object.entries(run.carried?.items ?? {})
  const carriedRunes = Object.entries(run.carried?.runes ?? {})
  return <section className="panel map-progress" aria-label="Expedition progress">
    <div className="map-progress__heading"><h2>Expedition progress</h2>
      <p>Hero totals update after each fight. Expedition changes are saved when the party returns.</p></div>
    <div className="map-progress__carried">
      <div><span>Carried gold</span><strong>{run.carried?.gold ?? 0}</strong></div>
      <div><span>Items</span><strong>{carriedItems.reduce((sum, [, count]) => sum + count, 0)}</strong></div>
      <div><span>Runes</span><strong>{carriedRunes.reduce((sum, [, count]) => sum + count, 0)}</strong></div>
    </div>
    {(carriedItems.length > 0 || carriedRunes.length > 0) && <ul className="map-progress__loot">
      {carriedItems.map(([id, count]) => <li key={id}>{itemNames.get(id) ?? 'Item'} × {count}</li>)}
      {carriedRunes.map(([id, count]) => <li key={id}>{runeNames.get(id) ?? 'Rune'} × {count}</li>)}
    </ul>}
    {run.quest?.pin.assignment && <div className="map-progress__quest"><h3>{run.quest.pin.assignment.definition.title}</h3><p>{run.quest.pin.eligible ? `${run.quest.progress} / ${run.quest.pin.assignment.definition.required} · saved on return` : 'This destination does not count toward your Quest.'}</p></div>}
    <div className="map-progress__heroes">{run.heroes.map((hero) => <article key={hero.heroId}>
      <h3>{hero.name}</h3>
      <p>XP {hero.experience} · Stamina {(hero.staminaMilliseconds / 3_600_000).toFixed(1)}h / 48h</p>
      <ul>{skills.map(([key, label]) => <li key={key}><span>{label}</span>
        <strong>{hero.skillPoints?.[key] ?? 0} points</strong></li>)}</ul>
    </article>)}</div>
  </section>
}

export default function MapPage({ agencyId, managerId, heroes, preparedParties, itemInventory, runeInventory }) {
  const [run, setRun] = useState(null)
  const [lastBattle, setLastBattle] = useState(null)
  const [loading, setLoading] = useState(true)
  const [unavailable, setUnavailable] = useState(false)
  const [error, setError] = useState(null)
  const [pendingAction, setPendingAction] = useState(null)
  const [selectedPartyId, setSelectedPartyId] = useState('')
  const [maps, setMaps] = useState([])
  const [mapError, setMapError] = useState(null)
  const [selectedMapId, setSelectedMapId] = useState('')
  const [socketEpoch, setSocketEpoch] = useState(0)
  const [socketStatus, setSocketStatus] = useState('connecting')
  const [autoContinue, setAutoContinue] = useState(false)
  const autoContinueAttemptRef = useRef(null)
  const inFlightRef = useRef(null)
  const expeditionId = run?.expeditionId

  const acceptRun = useCallback((next) => {
    setRun(next)
    const entryKey = `hero-association:expedition-entry:${managerId}`
    if (next?.expeditionId === readPending(entryKey)?.expeditionId) clearPending(entryKey)
    if (next?.fight?.visual) setLastBattle({ fightId: next.fight.fightId, run: next })
  }, [managerId])

  useEffect(() => {
    let cancelled = false
    fetchActiveExpedition().then((active) => {
      if (!cancelled) {
        acceptRun(active)
        setUnavailable(false)
        setError(null)
      }
    }).catch((failure) => {
      if (!cancelled) {
        setUnavailable(failure instanceof ApiRequestError && failure.status === 404)
        setError(failure.message)
      }
    }).finally(() => {
      if (!cancelled) setLoading(false)
    })
    return () => { cancelled = true }
  }, [agencyId, acceptRun])

  useEffect(() => {
    let active = true
    fetchMaps().then((catalog) => { if (active) { setMaps(catalog); setMapError(null) } })
      .catch((failure) => { if (active) setMapError(failure.message) })
    return () => { active = false }
  }, [agencyId, expeditionId])

  useEffect(() => {
    if (!expeditionId) return undefined
    let active = true
    let retryTimer
    const socket = connectExpedition(expeditionId)
    socket.onopen = () => {
      if (!active) socket.close()
      else {
        setSocketStatus('connected')
        setError(null)
      }
    }
    socket.onmessage = (event) => {
      try {
        const message = JSON.parse(event.data)
        if (active && message.type === 'snapshot' && message.snapshot?.expeditionId === expeditionId) {
          acceptRun(message.snapshot)
        }
      } catch {
        // An invalid frame is ignored; reconnect obtains an authoritative snapshot.
      }
    }
    socket.onerror = () => setSocketStatus('reconnecting')
    socket.onclose = async () => {
      if (!active) return
      setSocketStatus('reconnecting')
      try {
        const current = await fetchActiveExpedition()
        if (!active) return
        acceptRun(current)
        if (current?.expeditionId === expeditionId) {
          retryTimer = window.setTimeout(() => setSocketEpoch((epoch) => epoch + 1), 1_500)
        } else {
          setSocketStatus('disconnected')
        }
      } catch (failure) {
        if (!active) return
        setError(failure.message)
        retryTimer = window.setTimeout(() => setSocketEpoch((epoch) => epoch + 1), 3_000)
      }
    }
    return () => {
      active = false
      window.clearTimeout(retryTimer)
      if (socket.readyState === WebSocket.OPEN) socket.close()
    }
  }, [expeditionId, socketEpoch, acceptRun])

  const heroesById = new Map(heroes.map((hero) => [hero.id, hero]))
  const eligibleParties = preparedParties.filter((party) => party.ownerManagerId === managerId
    && party.heroIds.length > 0
    && party.heroIds.every((id) => heroesById.get(id)?.ownerManagerId === managerId))
  const partyId = eligibleParties.some((party) => party.id === selectedPartyId) ? selectedPartyId : eligibleParties[0]?.id

  const destination = maps.find((map) => map.definitionId === selectedMapId) ?? maps[0]
  const mapId = destination?.definitionId
  const pendingEntry = readPending(`hero-association:expedition-entry:${managerId}`)
  const stateVersion = run?.stateVersion
  function submit(action) {
    if (inFlightRef.current) return
    sendAction({ action, agencyId, managerId, partyId, mapId, expeditionId, stateVersion },
      acceptRun, setError, setPendingAction, inFlightRef)
  }

  const autoContinueReady = shouldAutoContinue(run, autoContinue, pendingAction)
  useEffect(() => {
    if (!autoContinueReady) return undefined
    const attemptKey = [expeditionId, stateVersion].join(':')
    if (autoContinueAttemptRef.current === attemptKey) return undefined
    const timer = window.setTimeout(() => {
      if (inFlightRef.current) return
      autoContinueAttemptRef.current = attemptKey
      sendAction({ action: 'continue', agencyId, partyId, expeditionId, stateVersion },
        acceptRun, setError, setPendingAction, inFlightRef)
    }, 1_500)
    return () => window.clearTimeout(timer)
  }, [autoContinueReady, agencyId, partyId, expeditionId, stateVersion, acceptRun])

  return <>
    <header className="page-heading"><div><p className="eyebrow">Explore</p><h1>Map</h1><p className="page-heading__description">Choose a field or dungeon for your party. Battles run on the server; continue manually or enable auto-continue while this page is open.</p></div></header>
    {loading && <p className="map-notice" role="status">Checking your active expedition…</p>}
    {unavailable && <p className="map-notice" role="status">The Map is not enabled in this environment yet.</p>}
    {error && !unavailable && <p className="inline-error" role="alert">{error}</p>}
    {!loading && !unavailable && !run && <section className="panel map-entry">
      <p className="eyebrow">Destination</p><h2>{destination?.name ?? 'Choose a destination'}</h2>
      <p>Fields repeat encounters. Dungeons finish after their final floor. The first battle starts when you enter.</p>
      {mapError && <p className="inline-error" role="alert">{mapError}</p>}
      {pendingEntry && <p role="status">Retry entry to confirm your previous destination and party.</p>}
      {eligibleParties.length > 0 ? <div className="map-entry__actions">
        <label><span>Destination</span><select value={mapId ?? ''} onChange={(event) => setSelectedMapId(event.target.value)} disabled={Boolean(pendingAction) || Boolean(pendingEntry)}>{maps.map((map) => <option key={map.definitionId} value={map.definitionId}>{map.name} · {map.kind === 'DUNGEON' ? `${map.floors.length} floors` : 'Field'}</option>)}</select></label>
        <label><span>Party</span><select value={partyId} onChange={(event) => setSelectedPartyId(event.target.value)} disabled={Boolean(pendingAction) || Boolean(pendingEntry)}>{eligibleParties.map((party) => <option key={party.id} value={party.id}>{party.name} · {party.heroIds.length} heroes</option>)}</select></label>
        <button className="button button--primary" type="button" disabled={Boolean(pendingAction) || (!mapId && !pendingEntry)} onClick={() => submit('start')}>{pendingAction === 'start' ? 'Entering…' : pendingEntry ? 'Retry entry' : destination?.kind === 'DUNGEON' ? 'Enter dungeon' : 'Enter field'}</button>
      </div> : <p>Prepare a party with personal heroes before entering. The server checks that at least one can fight.</p>}
    </section>}
    {run && <>
      <FightView run={run} knownHeroes={heroes} lastBattle={lastBattle} />
      <ProgressView run={run} itemInventory={itemInventory} runeInventory={runeInventory} />
      <div className="map-controls">
        <span className="map-controls__connection" role="status">{socketStatus === 'connected' ? 'Live' : 'Reconnecting to battle…'}</span>
        <label className="map-controls__auto">
          <input type="checkbox" checked={autoContinue}
            disabled={run.returnRequested || ['SETTLEMENT_PENDING', 'DUNGEON_COMPLETED'].includes(run.phase)}
            onChange={(event) => {
              autoContinueAttemptRef.current = null
              setAutoContinue(event.target.checked)
            }} />
          <span>Auto-continue</span>
        </label>
        {run.phase === 'DUNGEON_COMPLETED' && <span>All floors cleared. Return to save your results.</span>}
        {run.phase === 'AWAITING_CONTINUE' && <button className="button button--primary" type="button" disabled={Boolean(pendingAction)} onClick={() => submit('continue')}>{pendingAction === 'continue' ? 'Continuing…' : 'Continue'}</button>}
        {run.phase !== 'SETTLEMENT_PENDING' && !run.returnRequested && <button className="button button--secondary" type="button" disabled={Boolean(pendingAction)} onClick={() => submit('return')}>{run.phase === 'FIGHTING' ? 'Return after this fight' : 'Return to agency'}</button>}
        {run.returnRequested && <span>Return requested; this fight will finish first.</span>}
        {run.phase === 'SETTLEMENT_PENDING' && <span>Applying your expedition results…</span>}
      </div>
    </>}
  </>
}
