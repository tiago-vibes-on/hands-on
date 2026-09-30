import assert from 'node:assert/strict'
import test from 'node:test'
import { selectLiveBattleEvents } from './liveBattleEvents.js'

const event = (sequenceNumber, occurredAtMilliseconds) => ({ sequenceNumber, occurredAtMilliseconds })

test('a returning viewer jumps to current state instead of replaying missed events', () => {
  const current = { currentTimeMilliseconds: 9_000, events: [event(8, 7_000), event(9, 8_000)] }
  const result = selectLiveBattleEvents(2_000, 3, current, true)
  assert.equal(result.resync, true)
  assert.equal(result.latestSequence, 9)
  assert.deepEqual(result.events, [])

  const next = selectLiveBattleEvents(9_000, result.latestSequence,
    { currentTimeMilliseconds: 10_000, events: [event(9, 8_000), event(10, 9_700)] }, true)
  assert.equal(next.resync, false)
  assert.deepEqual(next.events.map((item) => item.sequenceNumber), [10])
})

test('a hidden viewer skips even a short animation backlog', () => {
  const result = selectLiveBattleEvents(2_000, 3,
    { currentTimeMilliseconds: 3_000, events: [event(4, 2_500)] }, false)
  assert.equal(result.resync, true)
  assert.deepEqual(result.events, [])
})

test('live playback animates only events newer than the prior snapshot', () => {
  const result = selectLiveBattleEvents(3_000, 4,
    { currentTimeMilliseconds: 4_000, events: [event(4, 2_900), event(6, 3_700), event(5, 3_200)] }, true)
  assert.equal(result.resync, false)
  assert.deepEqual(result.events.map((item) => item.sequenceNumber), [5, 6])
})
