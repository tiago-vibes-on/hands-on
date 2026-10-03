import { expect } from '@playwright/test'

function quantities(rows) {
  return Object.fromEntries(rows.filter((row) => row.quantity > 0).map((row) => [row.id, row.quantity]))
}

export function expectReturnedLoot(before, after, carried, reward = { gold: 0, items: {}, runes: {} }) {
  expect(after.gold).toBe(before.gold + carried.gold + reward.gold)
  for (const type of ['items', 'runes']) {
    const expected = quantities(before[type])
    for (const additions of [carried[type], reward[type] ?? {}]) {
      for (const [id, quantity] of Object.entries(additions)) expected[id] = (expected[id] ?? 0) + quantity
    }
    expect(quantities(after[type])).toEqual(expected)
  }
}

export const cavernReward = { gold: 160, items: { '019c4c00-0070-7000-8000-000000000002': 1 }, runes: {} }
