import { useCallback, useEffect, useState } from 'react'
import { ApiRequestError } from '../api/agency'
import { commandExpedition, connectExpedition, fetchActiveExpedition, startExpedition } from '../api/expedition'
import CombatScene from '../combat/CombatScene'
import { toPhaserBattle } from './expeditionCombat'
import './MapPage.css'

const phases = {
  FIGHTING: 'In battle',
  AWAITING_CONTINUE: 'Encounter complete',
  WIPED: 'Party defeated',
  SETTLEMENT_PENDING: 'Returning to agency',
}

function FightView({ run, knownHeroes, lastBattle }) {
  const currentBattle = toPhaserBattle(run, knownHeroes)
  const displayed = currentBattle ? { fightId: run.fight.fightId, battle: currentBattle }
    : run.lastOutcome?.fightId === lastBattle?.fightId
      ? { fightId: lastBattle.fightId, battle: toPhaserBattle(lastBattle.run, knownHeroes) } : null
  return <section className="panel map-fight" aria-label="Current encounter">
    <div className="map-fight__heading">
      <div><p className="eyebrow">Troll Field · Encounter {run.encounterIndex}</p><h2>{phases[run.phase] ?? run.phase}</h2></div>
      {currentBattle && <span className="map-fight__time">{Math.floor(currentBattle.currentTimeMilliseconds / 1000)}s</span>}
    </div>
    {run.lastOutcome && <p className="map-fight__outcome">Last fight: {run.lastOutcome.status === "HERO_VICTORY" ? "Victory" : "Defeat"}</p>}
    {displayed ? <CombatScene key={displayed.fightId} battle={displayed.battle} />
      : <p className="map-fight__outcome">Waiting for the next encounter.</p>}
  </section>
}

export default function MapPage({ agencyId, managerId, heroes, preparedParties }) {
  const [run, setRun] = useState(null)
  const [lastBattle, setLastBattle] = useState(null)
  const [loading, setLoading] = useState(true)
  const [unavailable, setUnavailable] = useState(false)
  const [error, setError] = useState(null)
  const [pendingAction, setPendingAction] = useState(null)
  const [selectedPartyId, setSelectedPartyId] = useState('')
  const [socketEpoch, setSocketEpoch] = useState(0)
  const [socketStatus, setSocketStatus] = useState('connecting')
  const expeditionId = run?.expeditionId

  const acceptRun = useCallback((next) => {
    setRun(next)
    if (next?.fight?.visual) setLastBattle({ fightId: next.fight.fightId, run: next })
  }, [])

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
    if (!expeditionId) return undefined
    let active = true
    let retryTimer
    const socket = connectExpedition(expeditionId)
    socket.onopen = () => {
      if (!active) socket.close()
      else setSocketStatus('connected')
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

  async function submit(action) {
    if (pendingAction) return
    setPendingAction(action)
    setError(null)
    try {
      const current = action === 'start'
        ? await startExpedition({ agencyId, partyId })
        : await commandExpedition({ expeditionId, action, expectedVersion: run.stateVersion })
      acceptRun(current)
    } catch (failure) {
      if (failure instanceof ApiRequestError && (failure.status === 409 || failure.status === 404)) {
        try { acceptRun(await fetchActiveExpedition()) } catch { /* Keep the last known snapshot. */ }
      }
      setError(failure.message)
    } finally {
      setPendingAction(null)
    }
  }

  return <>
    <header className="page-heading"><div><p className="eyebrow">Explore</p><h1>Map</h1><p className="page-heading__description">Enter a field with your party. Battles run on the server; you choose when to continue or return.</p></div></header>
    {loading && <p className="map-notice" role="status">Checking your active expedition…</p>}
    {unavailable && <p className="map-notice" role="status">The Map is not enabled in this environment yet.</p>}
    {error && !unavailable && <p className="inline-error" role="alert">{error}</p>}
    {!loading && !unavailable && !run && <section className="panel map-entry">
      <p className="eyebrow">First destination</p><h2>Troll Field</h2>
      <p>Take one of your prepared personal-hero parties into a field of Trolls. The first encounter starts when you enter.</p>
      {eligibleParties.length > 0 ? <div className="map-entry__actions">
        <label><span>Party</span><select value={partyId} onChange={(event) => setSelectedPartyId(event.target.value)} disabled={Boolean(pendingAction)}>{eligibleParties.map((party) => <option key={party.id} value={party.id}>{party.name} · {party.heroIds.length} heroes</option>)}</select></label>
        <button className="button button--primary" type="button" disabled={Boolean(pendingAction)} onClick={() => submit('start')}>{pendingAction === 'start' ? 'Entering…' : 'Enter field'}</button>
      </div> : <p>Prepare a party with personal heroes before entering. The server checks that at least one can fight.</p>}
    </section>}
    {run && <>
      <FightView run={run} knownHeroes={heroes} lastBattle={lastBattle} />
      <div className="map-controls">
        <span className="map-controls__connection" role="status">{socketStatus === 'connected' ? 'Live' : 'Reconnecting to battle…'}</span>
        {run.phase === 'AWAITING_CONTINUE' && <button className="button button--primary" type="button" disabled={Boolean(pendingAction)} onClick={() => submit('continue')}>{pendingAction === 'continue' ? 'Continuing…' : 'Continue'}</button>}
        {run.phase !== 'SETTLEMENT_PENDING' && !run.returnRequested && <button className="button button--secondary" type="button" disabled={Boolean(pendingAction)} onClick={() => submit('return')}>{run.phase === 'FIGHTING' ? 'Return after this fight' : 'Return to agency'}</button>}
        {run.returnRequested && <span>Return requested; this fight will finish first.</span>}
        {run.phase === 'SETTLEMENT_PENDING' && <span>Applying your expedition results…</span>}
      </div>
    </>}
  </>
}
