import { createApp, createRouter, toWebHandler } from 'h3'
import { createStorage } from 'unstorage'
import memoryDriver from 'unstorage/drivers/memory'
import { beforeEach, describe, expect, it } from 'vitest'
import type { BlobApiConfig, BlobApiDeps } from '../server/lib/blobApi'
import { checkSocket, createBlobApi, newCode, newSessionId, purgeExpired } from '../server/lib/blobApi'
import { SlidingWindowLimiter } from '../server/lib/rateLimit'
import { KeyedMutex } from '../server/lib/mutex'
import { SessionHub } from '../server/lib/sessionHub'
import { decodeBase64 } from '../shared/protocol/base64'
import { CODE_PATTERN, SESSION_ID_PATTERN } from '../shared/protocol/constants'
import { cryptoFixture } from './fixtures'

const BASE = 'http://editor.test'

let now = 1_800_000_000_000
let config: BlobApiConfig
let storage = createStorage({ driver: memoryDriver() })
let limiter = new SlidingWindowLimiter()
let handler: (request: Request) => Promise<Response>
let deps: BlobApiDeps

function defaults(): BlobApiConfig {
  return {
    payloadTtl: 86400, changesTtl: 3600, maxBlobBytes: 1024 * 1024, rateLimit: 30,
    rateLimitWindow: 600, trustProxy: false, uploadToken: '', liveRateLimit: 120, liveRateLimitWindow: 60
  }
}

beforeEach(() => {
  now = 1_800_000_000_000
  config = defaults()
  storage = createStorage({ driver: memoryDriver() })
  limiter = new SlidingWindowLimiter()
  deps = {
    storage: () => storage, config: () => config, limiter, liveLimiter: new SlidingWindowLimiter(),
    hub: new SessionHub(), locks: new KeyedMutex(), now: () => now
  }
  const api = createBlobApi(deps)
  const router = createRouter()
    .post('/api/sessions', api.createSession)
    .get('/api/sessions/:id', api.getSession)
    .put('/api/sessions/:id', api.replaceSession)
    .post('/api/sessions/:id/changes', api.createChanges)
    .get('/api/changes/:code', api.getChanges)
    .post('/api/changes/:code/result', api.createResult)
    .get('/api/changes/:code/result', api.getResult)
  const app = createApp().use(router)
  handler = toWebHandler(app)
})

const fixture = cryptoFixture()
const payloadBlob = decodeBase64(fixture.payload.blob)!
const changesBlob = decodeBase64(fixture.changes.blob)!
const resultBlob = decodeBase64(fixture.result.blob)!

function post(path: string, body: Uint8Array, headers: Record<string, string> = {}) {
  return handler(new Request(BASE + path, {
    method: 'POST',
    body: new Uint8Array(body),
    headers: { 'content-type': 'application/octet-stream', ...headers }
  }))
}

function put(path: string, body: Uint8Array, headers: Record<string, string> = {}) {
  return handler(new Request(BASE + path, { method: 'PUT', body: new Uint8Array(body), headers }))
}

function get(path: string) {
  return handler(new Request(BASE + path))
}

const tokens = new Map<string, string>()

async function upload(): Promise<string> {
  const response = await post('/api/sessions', payloadBlob)
  expect(response.status).toBe(201)
  const body = (await response.json()) as { id: string, token: string }
  tokens.set(body.id, body.token)
  return body.id
}

describe('happy path', () => {
  it('stores a payload, a change set, and serves both', async () => {
    const id = await upload()
    expect(id).toMatch(SESSION_ID_PATTERN)

    const fetched = await get(`/api/sessions/${id}`)
    expect(fetched.status).toBe(200)
    expect(fetched.headers.get('content-type')).toBe('application/octet-stream')
    expect(fetched.headers.get('cache-control')).toBe('no-store')
    expect(Number(fetched.headers.get('x-expires-at'))).toBe(now + 86400_000)
    expect(new Uint8Array(await fetched.arrayBuffer())).toEqual(payloadBlob)

    const saved = await post(`/api/sessions/${id}/changes`, changesBlob)
    expect(saved.status).toBe(201)
    const { code } = (await saved.json()) as { code: string }
    expect(code).toMatch(CODE_PATTERN)

    const changes = await get(`/api/changes/${code.toLowerCase()}`)
    expect(changes.status).toBe(200)
    expect(await changes.json()).toEqual({ session: id, data: fixture.changes.blob })
  })

  it('makes ids and codes of the specified shape', () => {
    for (let i = 0; i < 50; i++) {
      expect(newSessionId()).toMatch(SESSION_ID_PATTERN)
      expect(newCode()).toMatch(CODE_PATTERN)
    }
  })
})

describe('expiry', () => {
  it('refuses an expired payload and change set, and purges them', async () => {
    config.payloadTtl = 60
    config.changesTtl = 30
    const id = await upload()
    const saved = await post(`/api/sessions/${id}/changes`, changesBlob)
    const { code } = (await saved.json()) as { code: string }

    now += 31_000
    expect((await get(`/api/changes/${code}`)).status).toBe(404)
    expect((await get(`/api/sessions/${id}`)).status).toBe(200)

    now += 30_000
    const gone = await get(`/api/sessions/${id}`)
    expect(gone.status).toBe(404)
    expect(await gone.json()).toEqual({ error: 'not_found', message: expect.any(String) })
    expect((await post(`/api/sessions/${id}/changes`, changesBlob)).status).toBe(404)
  })

  it('purges expired blobs from storage', async () => {
    config.payloadTtl = 10
    await upload()
    await upload()
    expect(await storage.getKeys('session')).toHaveLength(2)
    expect(await storage.getKeys('meta')).toHaveLength(2)
    // Only a hash of the session token is stored.
    const meta = await storage.getItem<{ tokenHash: string }>((await storage.getKeys('meta'))[0]!)
    expect(meta?.tokenHash).toMatch(/^[0-9a-f]{64}$/)
    expect(JSON.stringify(await storage.getItem((await storage.getKeys('meta'))[0]!))).not.toContain(tokens.values().next().value)
    expect(await purgeExpired(storage, now)).toBe(0)
    expect(await purgeExpired(storage, now + 11_000)).toBe(4)
    expect(await storage.getKeys('session')).toHaveLength(0)
  })
})

describe('limits', () => {
  it('refuses a blob over the size limit by Content-Length and by bytes read', async () => {
    config.maxBlobBytes = 100
    const big = new Uint8Array(101).fill(1)
    const declared = await post('/api/sessions', big)
    expect(declared.status).toBe(413)
    expect(await declared.json()).toMatchObject({ error: 'too_large' })

    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(new Uint8Array(60).fill(1))
        controller.enqueue(new Uint8Array(60).fill(1))
        controller.close()
      }
    })
    const streamed = await handler(new Request(`${BASE}/api/sessions`, {
      method: 'POST', body: stream, duplex: 'half'
    } as RequestInit))
    expect(streamed.status).toBe(413)
  })

  it('refuses bodies that are empty or not sealed blobs', async () => {
    expect((await post('/api/sessions', new Uint8Array())).status).toBe(400)
    const wrong = await post('/api/sessions', new Uint8Array([2, 3, 4]))
    expect(wrong.status).toBe(400)
    expect(await wrong.json()).toMatchObject({ error: 'bad_blob' })
  })

  it('rate-limits uploads per address with a sliding window', async () => {
    config.rateLimit = 3
    config.rateLimitWindow = 60
    for (let i = 0; i < 3; i++) {
      await upload()
      now += 1000
    }
    const limited = await post('/api/sessions', payloadBlob)
    expect(limited.status).toBe(429)
    expect(Number(limited.headers.get('retry-after'))).toBeGreaterThan(0)
    expect(await limited.json()).toMatchObject({ error: 'rate_limited' })
    now += 58_000
    expect((await post('/api/sessions', payloadBlob)).status).toBe(201)
  })

  it('counts change uploads too, and trusts X-Forwarded-For only when told to', async () => {
    config.rateLimit = 1
    const id = await upload()
    expect((await post(`/api/sessions/${id}/changes`, changesBlob)).status).toBe(429)
    config.trustProxy = true
    const other = await post(`/api/sessions/${id}/changes`, changesBlob, { 'x-forwarded-for': 'forged, 203.0.113.9' })
    expect(other.status).toBe(201)
    expect((await post(`/api/sessions/${id}/changes`, changesBlob, { 'x-forwarded-for': 'other, 203.0.113.9' })).status).toBe(429)
  })
})

describe('upload token', () => {
  it('is required for new sessions only when configured', async () => {
    config.uploadToken = 's3cret'
    const missing = await post('/api/sessions', payloadBlob)
    expect(missing.status).toBe(401)
    expect(await missing.json()).toMatchObject({ error: 'bad_token' })
    expect((await post('/api/sessions', payloadBlob, { authorization: 'Bearer wrong' })).status).toBe(401)
    expect((await post('/api/sessions', payloadBlob, { authorization: 'Bearer s3cret' })).status).toBe(201)
  })

  it('is never needed by the browser routes', async () => {
    config.uploadToken = 's3cret'
    const created = await post('/api/sessions', payloadBlob, { authorization: 'Bearer s3cret' })
    const { id } = (await created.json()) as { id: string }
    expect((await get(`/api/sessions/${id}`)).status).toBe(200)
    expect((await post(`/api/sessions/${id}/changes`, changesBlob)).status).toBe(201)
  })
})

describe('unknown ids and codes', () => {
  it('answers 404 with the JSON error shape', async () => {
    for (const path of ['/api/sessions/AAAAAAAAAAAAAAAAAAAAAA', '/api/sessions/short', '/api/changes/ABCDEFGHJK',
      '/api/changes/0O1IABCDEF']) {
      const response = await get(path)
      expect(response.status).toBe(404)
      expect(await response.json()).toEqual({ error: 'not_found', message: expect.any(String) })
    }
    expect((await post('/api/sessions/AAAAAAAAAAAAAAAAAAAAAA/changes', changesBlob)).status).toBe(404)
  })
})

async function saveChanges(id: string): Promise<string> {
  const response = await post(`/api/sessions/${id}/changes`, changesBlob)
  expect(response.status).toBe(201)
  return ((await response.json()) as { code: string }).code
}

function auth(id: string) {
  return { authorization: `Bearer ${tokens.get(id)}` }
}

describe('session token and results', () => {
  it('returns a 32-byte base64url token with the session', async () => {
    const response = await post('/api/sessions', payloadBlob)
    const body = (await response.json()) as { id: string, token: string }
    expect(body.token).toMatch(/^[A-Za-z0-9_-]{43}$/)
  })

  it('stores one result per code with the session token, readable until the change set expires', async () => {
    config.changesTtl = 60
    const id = await upload()
    const code = await saveChanges(id)
    expect((await get(`/api/changes/${code}/result`)).status).toBe(404)
    expect((await post(`/api/changes/${code}/result`, resultBlob)).status).toBe(401)
    expect((await post(`/api/changes/${code}/result`, resultBlob, { authorization: 'Bearer nope' })).status).toBe(401)
    const other = await upload()
    expect((await post(`/api/changes/${code}/result`, resultBlob, auth(other))).status).toBe(401)
    expect((await post(`/api/changes/${code}/result`, resultBlob, auth(id))).status).toBe(201)
    const again = await post(`/api/changes/${code}/result`, resultBlob, auth(id))
    expect(again.status).toBe(409)
    expect(await again.json()).toMatchObject({ error: 'exists' })
    const fetched = await get(`/api/changes/${code.toLowerCase()}/result`)
    expect(fetched.status).toBe(200)
    expect(new Uint8Array(await fetched.arrayBuffer())).toEqual(resultBlob)
    now += 61_000
    expect((await get(`/api/changes/${code}/result`)).status).toBe(404)
  })

  it('checks result blobs like uploads and refuses unknown codes', async () => {
    expect((await post('/api/changes/ABCDEFGHJK/result', resultBlob)).status).toBe(404)
    const session = await upload()
    const code = await saveChanges(session)
    expect((await post(`/api/changes/${code}/result`, new Uint8Array([9, 9]), auth(session))).status).toBe(400)
    config.maxBlobBytes = 10
    expect((await post(`/api/changes/${code}/result`, resultBlob, auth(session))).status).toBe(413)
  })

  it('result routes have their own looser limit that does not count as uploads', async () => {
    config.rateLimit = 2
    config.liveRateLimit = 3
    const id = await upload()
    const code = await saveChanges(id)
    for (let i = 0; i < 3; i++) {
      expect((await get(`/api/changes/${code}/result`)).status).toBe(404)
    }
    expect((await get(`/api/changes/${code}/result`)).status).toBe(429)
    expect((await post('/api/sessions', payloadBlob)).status).toBe(429)
  })
})

describe('socket checks and notices', () => {
  function frames(): { peer: { send: (text: string) => void, close: (code: number) => void }, got: unknown[], closed: number[] } {
    const got: unknown[] = []
    const closed: number[] = []
    return { got, closed, peer: { send: text => got.push(JSON.parse(text)), close: code => closed.push(code) } }
  }

  it('refuses unknown sessions, bad roles and plugin sockets without the token', async () => {
    const id = await upload()
    const base = { address: 'a', authorization: null }
    expect(await checkSocket(deps, config, { ...base, id: 'AAAAAAAAAAAAAAAAAAAAAA', role: 'browser' })).toMatchObject({ ok: false, status: 404 })
    expect(await checkSocket(deps, config, { ...base, id, role: 'admin' })).toMatchObject({ ok: false, status: 400 })
    expect(await checkSocket(deps, config, { ...base, id, role: 'plugin' })).toMatchObject({ ok: false, status: 401 })
    expect(await checkSocket(deps, config, { ...base, id, role: 'plugin', authorization: 'Bearer wrong' })).toMatchObject({ ok: false, status: 401 })
    expect(await checkSocket(deps, config, { ...base, id, role: 'browser' })).toMatchObject({ ok: true, role: 'browser' })
    expect(await checkSocket(deps, config, { ...base, id, role: 'plugin', authorization: auth(id).authorization })).toMatchObject({ ok: true, role: 'plugin' })
    config.payloadTtl = 1
    const short = await upload()
    now += 2000
    expect(await checkSocket(deps, config, { ...base, id: short, role: 'browser' })).toMatchObject({ ok: false, status: 404 })
  })

  it('lists pending codes: no result yet and not expired, oldest first', async () => {
    config.changesTtl = 100
    const id = await upload()
    const a = await saveChanges(id)
    now += 50_000
    const b = await saveChanges(id)
    const c = await saveChanges(id)
    expect((await post(`/api/changes/${b}/result`, resultBlob, auth(id))).status).toBe(201)
    now += 60_000
    const check = await checkSocket(deps, config, { id, role: 'plugin', authorization: auth(id).authorization, address: 'a' })
    expect(check).toMatchObject({ ok: true, pending: [c] })
    void a
  })

  it('tells the plugin about saved change sets and browsers about results', async () => {
    const id = await upload()
    const plugin = frames()
    const browser = frames()
    deps.hub.join(plugin.peer, id, 'plugin', now + 1e6, now, [])
    deps.hub.join(browser.peer, id, 'browser', now + 1e6, now)
    const code = await saveChanges(id)
    expect(plugin.got).toContainEqual({ type: 'changes', code })
    expect((await post(`/api/changes/${code}/result`, resultBlob, auth(id))).status).toBe(201)
    expect(browser.got).toContainEqual({ type: 'result', code })
    expect(plugin.got).not.toContainEqual({ type: 'result', code })
  })

  it('counts socket connections against the looser limit', async () => {
    config.liveRateLimit = 1
    const id = await upload()
    expect(await checkSocket(deps, config, { id, role: 'browser', authorization: null, address: 'x' })).toMatchObject({ ok: true })
    expect(await checkSocket(deps, config, { id, role: 'browser', authorization: null, address: 'x' })).toMatchObject({ ok: false, status: 429 })
  })
})

describe('revisions', () => {
  it('starts at 1, and each PUT with the token replaces the payload and adds one', async () => {
    const id = await upload()
    const first = await get(`/api/sessions/${id}`)
    expect(first.headers.get('x-revision')).toBe('1')
    const expiresAt = first.headers.get('x-expires-at')
    const fresh = changesBlob // any sealed blob: the server never reads it
    now += 5000
    const replaced = await put(`/api/sessions/${id}`, fresh, auth(id))
    expect(replaced.status).toBe(200)
    expect(await replaced.json()).toEqual({ revision: 2 })
    const second = await get(`/api/sessions/${id}`)
    expect(second.headers.get('x-revision')).toBe('2')
    expect(second.headers.get('x-expires-at')).toBe(expiresAt)
    expect(new Uint8Array(await second.arrayBuffer())).toEqual(fresh)
    expect(await (await put(`/api/sessions/${id}`, payloadBlob, auth(id))).json()).toEqual({ revision: 3 })
  })

  it('refuses a PUT without the token, for unknown sessions, too large or not sealed', async () => {
    const id = await upload()
    expect((await put(`/api/sessions/${id}`, payloadBlob)).status).toBe(401)
    expect((await put(`/api/sessions/${id}`, payloadBlob, { authorization: 'Bearer nope' })).status).toBe(401)
    expect((await put('/api/sessions/AAAAAAAAAAAAAAAAAAAAAA', payloadBlob, auth(id))).status).toBe(404)
    expect((await put(`/api/sessions/${id}`, new Uint8Array([7]), auth(id))).status).toBe(400)
    config.maxBlobBytes = 10
    expect((await put(`/api/sessions/${id}`, payloadBlob, auth(id))).status).toBe(413)
    expect((await get(`/api/sessions/${id}`)).headers.get('x-revision')).toBe('1')
  })

  it('counts PUTs against the looser limit, not as uploads', async () => {
    config.rateLimit = 1
    config.liveRateLimit = 2
    const id = await upload()
    expect((await put(`/api/sessions/${id}`, payloadBlob, auth(id))).status).toBe(200)
    expect((await put(`/api/sessions/${id}`, payloadBlob, auth(id))).status).toBe(200)
    expect((await put(`/api/sessions/${id}`, payloadBlob, auth(id))).status).toBe(429)
  })

  it('gives every concurrent PUT its own revision', async () => {
    const id = await upload()
    const results = await Promise.all(Array.from({ length: 8 }, () => put(`/api/sessions/${id}`, payloadBlob, auth(id))))
    const revisions = await Promise.all(results.map(async response => ((await response.json()) as { revision: number }).revision))
    expect(revisions.sort((a, b) => a - b)).toEqual([2, 3, 4, 5, 6, 7, 8, 9])
  })
})

describe('races', () => {
  it('keeps every code of change sets saved at the same instant, in order', async () => {
    const id = await upload()
    const responses = await Promise.all(Array.from({ length: 12 }, () => post(`/api/sessions/${id}/changes`, changesBlob)))
    const codes = await Promise.all(responses.map(async response => ((await response.json()) as { code: string }).code))
    const check = await checkSocket(deps, config, { id, role: 'plugin', authorization: auth(id).authorization, address: 'a' })
    expect(check.ok && check.pending.length).toBe(12)
    expect(check.ok && [...check.pending].sort()).toEqual([...codes].sort())
  })

  it('lets exactly one of simultaneous results for a code win', async () => {
    const id = await upload()
    const code = await saveChanges(id)
    const statuses = await Promise.all(Array.from({ length: 6 }, () =>
      post(`/api/changes/${code}/result`, resultBlob, auth(id)).then(response => response.status)))
    expect(statuses.filter(status => status === 201)).toHaveLength(1)
    expect(statuses.filter(status => status === 409)).toHaveLength(5)
  })
})
