import { useCallback, useEffect, useRef, useState } from 'react'
import { fetchQuests, sendQuestCommand } from '../api/quest'
import { newUuidV7 } from '../api/uuid'
import { readPending, savePending, clearPending, definitiveFailure } from '../api/pendingCommand'

const objectives = { KILL_COUNT: 'Defeat creatures', BOSS_DEFEAT: 'Defeat the boss', DUNGEON_COMPLETION: 'Complete the dungeon' }
function Rewards({ reward, itemInventory, runeInventory }) {
  const items = new Map(itemInventory.map((item) => [item.id, item.name]))
  const runes = new Map(runeInventory.map((rune) => [rune.id, rune.name]))
  return <p>{reward.gold} gold{Object.entries(reward.items).map(([id, count]) => <span key={id}> · {items.get(id) ?? 'Item'} × {count}</span>)}{Object.entries(reward.runes).map(([id, count]) => <span key={id}> · {runes.get(id) ?? 'Rune'} × {count}</span>)}</p>
}
export default function QuestPage({ managerId, itemInventory = [], runeInventory = [] }) {
  const key = `hero-association:quest-command:${managerId}`
  const [board, setBoard] = useState(null)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)
  const [pending, setPending] = useState(() => readPending(key))
  const inFlight = useRef(false)
  const refresh = useCallback(async () => { const next = await fetchQuests(); setBoard(next); return next }, [])
  useEffect(() => {
    let active = true
    const load = () => fetchQuests().then((next) => { if (active) setBoard(next) }).catch((failure) => { if (active) setError(failure.message) })
    load()
    const timer = window.setInterval(load, 5000)
    return () => { active = false; window.clearInterval(timer) }
  }, [managerId])
  async function submit(action, targetId) {
    if (inFlight.current) return
    const intent = readPending(key) ?? savePending(key, { action, targetId, commandId: newUuidV7() })
    setPending(intent); inFlight.current = true; setBusy(true); setError(null)
    try {
      await sendQuestCommand(intent)
      clearPending(key); setPending(null)
      await refresh()
    } catch (failure) {
      if (definitiveFailure(failure)) { clearPending(key); setPending(null) }
      setError(failure.message)
    } finally { inFlight.current = false; setBusy(false) }
  }
  const assignment = board?.activeAssignment
  return <>
    <header className="page-heading"><div><p className="eyebrow">Optional objectives</p><h1>Quests</h1><p className="page-heading__description">Accept one Quest at the agency, then explore the Map. Progress is saved on return; a fulfilled Quest pays its reward once.</p></div></header>
    {error && <p className="inline-error" role="alert">{error}</p>}
    {pending && <section className="panel"><p>The last Quest command still needs confirmation.</p><button className="button button--primary" disabled={busy} onClick={() => submit(pending.action, pending.targetId)}>Retry Quest command</button></section>}
    {!board && <p role="status">Loading Quest board…</p>}
    {board && !board.atAgency && <p role="status">Return to the agency before accepting or cancelling a Quest.</p>}
    {assignment && <section className="panel quest-card" aria-label="Active Quest"><div><span className="status">{assignment.status === 'REWARD_PENDING' ? 'Applying reward' : 'Active'}</span><h2>{assignment.definition.title}</h2><p>{assignment.definition.description}</p><p>Saved progress: {assignment.progress} / {assignment.definition.required}</p><Rewards reward={assignment.definition.reward} itemInventory={itemInventory} runeInventory={runeInventory} /><button className="button button--secondary" disabled={busy || Boolean(pending) || !board.atAgency || assignment.status !== 'ACTIVE'} onClick={() => submit('cancel', assignment.assignmentId)}>Cancel Quest</button></div></section>}
    {board && <section className="quest-list" aria-label="Quest board">{board.definitions.map((definition) => <article className="panel quest-card" key={definition.definitionId}><div><h2>{definition.title}</h2><p>{definition.description}</p><p>{objectives[definition.objective]} · {definition.required} required</p><Rewards reward={definition.reward} itemInventory={itemInventory} runeInventory={runeInventory} /><button className="button button--primary" disabled={busy || Boolean(pending) || Boolean(assignment) || !board.atAgency} onClick={() => submit('accept', definition.definitionId)}>Accept Quest</button></div></article>)}</section>}
    {board?.recentAssignments.length > 0 && <section className="panel" aria-label="Quest history"><h2>Recent Quests</h2><ul>{board.recentAssignments.map((past) => <li key={past.assignmentId}>{past.definition.title} · {past.status === 'COMPLETED' ? 'Completed — reward paid' : 'Cancelled'}</li>)}</ul></section>}
  </>
}
