import http from 'k6/http'
import { Counter } from 'k6/metrics'

const { appUrl, sessions } = JSON.parse(open('/session/session.json'))
const burstNotLimited = new Counter('market_burst_not_limited')
const burstRateLimited = new Counter('market_burst_rate_limited')
const otherUserNotLimited = new Counter('market_other_user_not_limited')
const sustainedNotLimited = new Counter('market_sustained_not_limited')
const sustainedRateLimited = new Counter('market_sustained_rate_limited')
const unexpected = new Counter('market_unexpected')

http.setResponseCallback(http.expectedStatuses(400, 429))

export const options = {
  insecureSkipTLSVerify: true, // The isolated k3d lab uses a local development CA.
  batchPerHost: 20,
  scenarios: {
    burst: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 1,
      exec: 'burst',
      maxDuration: '20s',
    },
    sustained: {
      executor: 'constant-arrival-rate',
      rate: 20,
      timeUnit: '1s',
      duration: '10s',
      preAllocatedVUs: 10,
      maxVUs: 30,
      startTime: '5s',
      exec: 'sustained',
      gracefulStop: '1s',
    },
  },
  thresholds: {
    market_burst_not_limited: ['count==5'],
    market_burst_rate_limited: ['count==1'],
    market_other_user_not_limited: ['count==1'],
    'iterations{scenario:sustained}': ['count>=198', 'count<=202'],
    market_sustained_not_limited: ['count>0', 'count<=55'],
    market_sustained_rate_limited: ['count>=143'],
    market_unexpected: ['count==0'],
    dropped_iterations: ['count==0'],
  },
}

function orderRequest(session) {
  return {
    method: 'POST',
    url: `${appUrl}/api/v1/market/orders`,
    body: JSON.stringify({ agencyId: session.agencyId }), // Core rejects incomplete order data.
    params: {
      redirects: 0,
      headers: {
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': session.csrfToken,
        Cookie: session.cookieHeader,
      },
      tags: { name: 'POST /api/v1/market/orders' },
    },
  }
}

function count(response, notLimited, rateLimited) {
  if (response.status === 400) {
    notLimited.add(1)
  } else if (response.status === 429 && response.headers['Retry-After'] === '1' && response.headers['X-Hero-Association-Rate-Limit-Layer'] === 'envoy') {
    rateLimited.add(1)
  } else {
    unexpected.add(1)
    console.error(`Unexpected market response: HTTP ${response.status}`)
  }
}

export function burst() {
  const requests = Array.from({ length: 6 }, (_, index) => orderRequest(sessions[index % 2]))
  for (const response of http.batch(requests)) {
    count(response, burstNotLimited, burstRateLimited)
  }
  const otherUserOrder = orderRequest(sessions[2])
  const otherUserResponse = http.post(otherUserOrder.url, otherUserOrder.body, otherUserOrder.params)
  if (otherUserResponse.status === 400) {
    otherUserNotLimited.add(1)
  } else {
    unexpected.add(1)
    console.error(`Unexpected second-user response: HTTP ${otherUserResponse.status}`)
  }
}

export function sustained() {
  const request = orderRequest(sessions[__VU % 2])
  count(http.post(request.url, request.body, request.params), sustainedNotLimited, sustainedRateLimited)
}
