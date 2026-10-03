import { request } from './agency.js'

export function fetchQuests() { return request('/api/v1/quests') }
export function sendQuestCommand({ action, targetId, commandId }) {
  const path = action === 'accept' ? `/api/v1/quests/${targetId}/accept`
    : action === 'cancel' ? `/api/v1/quests/assignments/${targetId}/cancel` : null
  if (!path) throw new Error('Unsupported Quest command.')
  return request(path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ commandId }) })
}
