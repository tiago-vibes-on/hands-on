export const MAX_LIVE_GAP_MILLISECONDS = 2_000

/** Recent server events are history, not a backlog to replay after an absence. */
export function selectLiveBattleEvents(previousTime, lastSequence, snapshot, visible) {
  const now = snapshot.currentTimeMilliseconds
  const events = snapshot.events ?? []
  const latestSequence = events.reduce((latest, event) => Math.max(latest, event.sequenceNumber), lastSequence)
  const resync = !visible || now - previousTime > MAX_LIVE_GAP_MILLISECONDS

  return {
    resync,
    latestSequence,
    events: resync ? [] : events
      .filter((event) => event.sequenceNumber > lastSequence
        && event.occurredAtMilliseconds > previousTime
        && event.occurredAtMilliseconds <= now)
      .sort((left, right) => left.sequenceNumber - right.sequenceNumber),
  }
}
