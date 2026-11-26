// The editor's backend (protocol section 3): a short-lived store of opaque, sealed blobs.
// The handlers are built from their collaborators so tests can run them without Nitro.

import { createHash, randomBytes, timingSafeEqual } from 'node:crypto'
import type { H3Event } from 'h3'
import {
  defineEventHandler, getHeader, getRequestIP, getRequestWebStream, getRouterParam,
  setResponseHeader, setResponseStatus
} from 'h3'
import type { Storage } from 'unstorage'
import { encodeBase64, encodeBase64Url, decodeBase64 } from '../../shared/protocol/base64'
import {
  CODE_ALPHABET, CODE_LENGTH, CODE_PATTERN, SEALING_VERSION, SESSION_ID_PATTERN
} from '../../shared/protocol/constants'
import type { SlidingWindowLimiter } from './rateLimit'
import type { KeyedMutex } from './mutex'
import type { Role, SessionHub } from './sessionHub'

export interface BlobApiConfig {
  payloadTtl: number
  changesTtl: number
  maxBlobBytes: number
  rateLimit: number
  rateLimitWindow: number
  trustProxy: boolean
  uploadToken: string
  /** presence, watch and the result routes (protocol section 3). */
  liveRateLimit: number
  liveRateLimitWindow: number
}

export interface BlobApiDeps {
  storage: () => Storage
  config: (event: H3Event) => BlobApiConfig
  limiter: SlidingWindowLimiter
  liveLimiter: SlidingWindowLimiter
  hub: SessionHub
  /** Per-session and per-code locks for read-modify-write updates (one process). */
  locks: KeyedMutex
  now: () => number
}

/** Small per-session record, kept apart from the payload blob so updating it is cheap. */
interface SessionMeta {
  expiresAt: number
  /** SHA-256 of the session token (hex); the token itself is never stored. */
  tokenHash: string
  /** Codes of the session's change sets, oldest first. */
  codes: string[]
  /** 1 for the payload of POST /api/sessions, plus one per PUT. */
  revision: number
}

interface StoredBlob {
  expiresAt: number
  data: string
  session?: string
}

const SESSION_PREFIX = 'session'
const META_PREFIX = 'meta'
const CHANGES_PREFIX = 'changes'
const RESULT_PREFIX = 'result'

class ApiError extends Error {
  constructor(readonly status: number, readonly code: string, message: string,
    readonly headers: Record<string, string> = {}) {
    super(message)
  }
}

function errorBody(event: H3Event, error: ApiError) {
  setResponseStatus(event, error.status)
  for (const [name, value] of Object.entries(error.headers)) {
    setResponseHeader(event, name, value)
  }
  return { error: error.code, message: error.message }
}

function handle(run: (event: H3Event) => Promise<unknown>) {
  return defineEventHandler(async (event) => {
    setResponseHeader(event, 'cache-control', 'no-store')
    try {
      return await run(event)
    } catch (error) {
      if (error instanceof ApiError) {
        return errorBody(event, error)
      }
      throw error
    }
  })
}

export function newSessionId(): string {
  return encodeBase64Url(randomBytes(16))
}

export function newCode(): string {
  // 256 is a multiple of the alphabet's 32 letters, so masking the bytes isn't biased.
  return [...randomBytes(CODE_LENGTH)].map(byte => CODE_ALPHABET[byte & 31]).join('')
}

/** The client address, from X-Forwarded-For only when the server is told to trust it. */
export function clientAddress(event: H3Event, trustProxy: boolean): string {
  if (trustProxy) {
    const forwarded = getHeader(event, 'x-forwarded-for')
    if (forwarded) {
      // The rightmost entry is the one the nearest proxy added; earlier ones can be forged.
      const last = forwarded.split(',').map(part => part.trim()).filter(Boolean).at(-1)
      if (last) {
        return last
      }
    }
  }
  return getRequestIP(event) ?? 'unknown'
}

function sameToken(given: string, expected: string): boolean {
  const a = createHash('sha256').update(given).digest()
  const b = createHash('sha256').update(expected).digest()
  return timingSafeEqual(a, b)
}

export function tokenHash(token: string): string {
  return createHash('sha256').update(token).digest('hex')
}

function matchesHash(given: string, hash: string): boolean {
  const a = createHash('sha256').update(given).digest()
  const b = Buffer.from(hash, 'hex')
  return b.length === a.length && timingSafeEqual(a, b)
}

/** The token of an `Authorization: Bearer <token>` header, or null. */
export function bearer(header: string | null | undefined): string | null {
  const match = /^Bearer\s+(\S+)\s*$/i.exec((header ?? '').trim())
  return match ? match[1]! : null
}

async function readBlob(event: H3Event, max: number): Promise<Uint8Array> {
  const length = getHeader(event, 'content-length')
  if (length !== undefined && /^\d+$/.test(length) && Number(length) > max) {
    throw new ApiError(413, 'too_large', `The blob is larger than ${max} bytes.`)
  }
  const stream = getRequestWebStream(event)
  if (!stream) {
    throw new ApiError(400, 'empty', 'The request has no body.')
  }
  const reader = stream.getReader()
  const chunks: Uint8Array[] = []
  let total = 0
  for (;;) {
    const { done, value } = await reader.read()
    if (done) {
      break
    }
    const chunk = typeof value === 'string' ? new TextEncoder().encode(value) : new Uint8Array(value)
    total += chunk.length
    if (total > max) {
      await reader.cancel().catch(() => undefined)
      throw new ApiError(413, 'too_large', `The blob is larger than ${max} bytes.`)
    }
    chunks.push(chunk)
  }
  const blob = new Uint8Array(total)
  let offset = 0
  for (const chunk of chunks) {
    blob.set(chunk, offset)
    offset += chunk.length
  }
  if (blob.length === 0) {
    throw new ApiError(400, 'empty', 'The request has no body.')
  }
  if (blob[0] !== SEALING_VERSION) {
    throw new ApiError(400, 'bad_blob', 'The body is not a sealed blob.')
  }
  return blob
}

function rateLimit(event: H3Event, deps: BlobApiDeps, config: BlobApiConfig, live = false) {
  const address = clientAddress(event, config.trustProxy)
  const wait = live
    ? deps.liveLimiter.hit(address, config.liveRateLimit, config.liveRateLimitWindow * 1000, deps.now())
    : deps.limiter.hit(address, config.rateLimit, config.rateLimitWindow * 1000, deps.now())
  if (wait > 0) {
    throw new ApiError(429, 'rate_limited', 'Too many uploads. Try again later.',
      { 'retry-after': String(Math.ceil(wait / 1000)) })
  }
}

async function load<T extends { expiresAt: number } = StoredBlob>(storage: Storage, key: string,
  now: number): Promise<T | null> {
  const stored = await storage.getItem<T>(key)
  if (!stored || typeof stored !== 'object' || typeof stored.expiresAt !== 'number') {
    return null
  }
  if (stored.expiresAt <= now) {
    await storage.removeItem(key).catch(() => undefined)
    return null
  }
  return stored
}

async function store(storage: Storage, key: string, value: StoredBlob | SessionMeta, ttlSeconds: number) {
  // `ttl` is used by drivers that expire natively (Redis); the others rely on expiresAt.
  await storage.setItem(key, value, { ttl: ttlSeconds })
}

async function unusedKey(storage: Storage, prefix: string, make: () => string): Promise<string> {
  for (let attempt = 0; attempt < 5; attempt++) {
    const id = make()
    if (!(await storage.hasItem(`${prefix}:${id}`))) {
      return id
    }
  }
  throw new ApiError(503, 'busy', 'Could not allocate an id. Try again.')
}

/** Removes expired blobs; returns how many. Filesystem storage has no native expiry. */
export async function purgeExpired(storage: Storage, now: number): Promise<number> {
  let removed = 0
  for (const prefix of [SESSION_PREFIX, META_PREFIX, CHANGES_PREFIX, RESULT_PREFIX]) {
    for (const key of await storage.getKeys(prefix)) {
      const stored = await storage.getItem<{ expiresAt?: unknown }>(key).catch(() => null)
      if (!stored || typeof stored.expiresAt !== 'number' || stored.expiresAt <= now) {
        await storage.removeItem(key).catch(() => undefined)
        removed++
      }
    }
  }
  return removed
}

function ttlLeft(expiresAt: number, now: number): number {
  return Math.max(1, Math.ceil((expiresAt - now) / 1000))
}

function sessionId(event: H3Event): string {
  return getRouterParam(event, 'id') ?? ''
}

function changeCode(event: H3Event): string {
  return (getRouterParam(event, 'code') ?? '').toUpperCase()
}

async function loadMeta(storage: Storage, id: string, now: number): Promise<SessionMeta> {
  const meta = SESSION_ID_PATTERN.test(id)
    ? await load<SessionMeta>(storage, `${META_PREFIX}:${id}`, now)
    : null
  if (!meta) {
    throw new ApiError(404, 'not_found', 'This session does not exist or has expired.')
  }
  return meta
}

async function loadChanges(storage: Storage, code: string, now: number): Promise<StoredBlob> {
  const stored = CODE_PATTERN.test(code)
    ? await load(storage, `${CHANGES_PREFIX}:${code}`, now)
    : null
  if (!stored || typeof stored.session !== 'string') {
    throw new ApiError(404, 'not_found', 'This code does not exist or has expired.')
  }
  return stored
}

export function createBlobApi(deps: BlobApiDeps) {
  return {
    createSession: handle(async (event) => {
      const config = deps.config(event)
      rateLimit(event, deps, config)
      if (config.uploadToken !== '') {
        const header = getHeader(event, 'authorization') ?? ''
        const match = /^Bearer\s+(.+)$/i.exec(header.trim())
        if (!match || !sameToken(match[1]!.trim(), config.uploadToken)) {
          throw new ApiError(401, 'bad_token', 'A valid upload token is required.',
            { 'www-authenticate': 'Bearer' })
        }
      }
      const blob = await readBlob(event, config.maxBlobBytes)
      const storage = deps.storage()
      const id = await unusedKey(storage, SESSION_PREFIX, newSessionId)
      const token = encodeBase64Url(randomBytes(32))
      const expiresAt = deps.now() + config.payloadTtl * 1000
      await store(storage, `${SESSION_PREFIX}:${id}`, { expiresAt, data: encodeBase64(blob) },
        config.payloadTtl)
      await store(storage, `${META_PREFIX}:${id}`,
        { expiresAt, tokenHash: tokenHash(token), codes: [], revision: 1 }, config.payloadTtl)
      setResponseStatus(event, 201)
      setResponseHeader(event, 'x-expires-at', String(expiresAt))
      return { id, token }
    }),

    getSession: handle(async (event) => {
      const id = getRouterParam(event, 'id') ?? ''
      const stored = SESSION_ID_PATTERN.test(id)
        ? await load(deps.storage(), `${SESSION_PREFIX}:${id}`, deps.now())
        : null
      const blob = stored ? decodeBase64(stored.data) : null
      if (!stored || !blob) {
        throw new ApiError(404, 'not_found', 'This session does not exist or has expired.')
      }
      const meta = await load<SessionMeta>(deps.storage(), `${META_PREFIX}:${id}`, deps.now())
      setResponseHeader(event, 'content-type', 'application/octet-stream')
      setResponseHeader(event, 'x-expires-at', String(stored.expiresAt))
      setResponseHeader(event, 'x-revision', String(meta?.revision ?? 1))
      return Buffer.from(blob)
    }),

    replaceSession: handle(async (event) => {
      const config = deps.config(event)
      rateLimit(event, deps, config, true)
      const storage = deps.storage()
      const id = sessionId(event)
      const known = await loadMeta(storage, id, deps.now())
      const token = bearer(getHeader(event, 'authorization'))
      if (token === null || !matchesHash(token, known.tokenHash)) {
        throw new ApiError(401, 'bad_token', 'The session token is missing or wrong.',
          { 'www-authenticate': 'Bearer' })
      }
      const blob = await readBlob(event, config.maxBlobBytes)
      const revision = await deps.locks.run(`session:${id}`, async () => {
        const meta = await loadMeta(storage, id, deps.now())
        const ttl = ttlLeft(meta.expiresAt, deps.now())
        // The expiry stays where it was: a PUT doesn't extend the session.
        await store(storage, `${SESSION_PREFIX}:${id}`, { expiresAt: meta.expiresAt, data: encodeBase64(blob) }, ttl)
        meta.revision = (meta.revision ?? 1) + 1
        await store(storage, `${META_PREFIX}:${id}`, meta, ttl)
        return meta.revision
      })
      return { revision }
    }),

    createChanges: handle(async (event) => {
      const config = deps.config(event)
      const id = sessionId(event)
      rateLimit(event, deps, config)
      const storage = deps.storage()
      await loadMeta(storage, id, deps.now())
      const blob = await readBlob(event, config.maxBlobBytes)
      const code = await unusedKey(storage, CHANGES_PREFIX, newCode)
      const expiresAt = deps.now() + config.changesTtl * 1000
      await store(storage, `${CHANGES_PREFIX}:${code}`,
        { expiresAt, session: id, data: encodeBase64(blob) }, config.changesTtl)
      // Under the session's lock, so two saves at the same instant both end up in the list.
      await deps.locks.run(`session:${id}`, async () => {
        const meta = await loadMeta(storage, id, deps.now())
        meta.codes.push(code)
        await store(storage, `${META_PREFIX}:${id}`, meta, ttlLeft(meta.expiresAt, deps.now()))
      })
      deps.hub.changesSaved(id, code)
      setResponseStatus(event, 201)
      setResponseHeader(event, 'x-expires-at', String(expiresAt))
      return { code }
    }),

    getChanges: handle(async (event) => {
      const stored = await loadChanges(deps.storage(), changeCode(event), deps.now())
      setResponseHeader(event, 'x-expires-at', String(stored.expiresAt))
      return { session: stored.session, data: stored.data }
    }),

    createResult: handle(async (event) => {
      const config = deps.config(event)
      rateLimit(event, deps, config, true)
      const storage = deps.storage()
      const code = changeCode(event)
      const changes = await loadChanges(storage, code, deps.now())
      const meta = await load<SessionMeta>(storage, `${META_PREFIX}:${changes.session}`, deps.now())
      const token = bearer(getHeader(event, 'authorization'))
      if (!meta || token === null || !matchesHash(token, meta.tokenHash)) {
        throw new ApiError(401, 'bad_token', 'The session token is missing or wrong.',
          { 'www-authenticate': 'Bearer' })
      }
      const blob = await readBlob(event, config.maxBlobBytes)
      // Check and write under the code's lock, so of two simultaneous posts exactly one wins.
      await deps.locks.run(`result:${code}`, async () => {
        if (await load(storage, `${RESULT_PREFIX}:${code}`, deps.now())) {
          throw new ApiError(409, 'exists', 'A result for this code exists already.')
        }
        // A result lives as long as its change set.
        await store(storage, `${RESULT_PREFIX}:${code}`,
          { expiresAt: changes.expiresAt, data: encodeBase64(blob) }, ttlLeft(changes.expiresAt, deps.now()))
      })
      deps.hub.resultPosted(changes.session!, code)
      setResponseStatus(event, 201)
      return { code }
    }),

    getResult: handle(async (event) => {
      const config = deps.config(event)
      rateLimit(event, deps, config, true)
      const code = changeCode(event)
      const stored = CODE_PATTERN.test(code)
        ? await load(deps.storage(), `${RESULT_PREFIX}:${code}`, deps.now())
        : null
      const blob = stored ? decodeBase64(stored.data) : null
      if (!stored || !blob) {
        throw new ApiError(404, 'not_found', 'There is no result for this code (yet).')
      }
      setResponseHeader(event, 'content-type', 'application/octet-stream')
      return Buffer.from(blob)
    })
  }
}

export type SocketCheck
  = | { ok: true, session: string, role: Role, expiresAt: number, pending: string[] }
    | { ok: false, status: number, error: string, message: string }

/**
 * The checks of a socket upgrade (protocol section 3): rate limit, a live session, and for the
 * plugin its session token. `pending`: change sets of the session without a result, not expired.
 */
export async function checkSocket(deps: BlobApiDeps, config: BlobApiConfig, request: {
  id: string
  role: string | null
  authorization: string | null
  address: string
}): Promise<SocketCheck> {
  const now = deps.now()
  const wait = deps.liveLimiter.hit(request.address, config.liveRateLimit, config.liveRateLimitWindow * 1000, now)
  if (wait > 0) {
    return { ok: false, status: 429, error: 'rate_limited', message: 'Too many requests. Try again later.' }
  }
  if (request.role !== 'browser' && request.role !== 'plugin') {
    return { ok: false, status: 400, error: 'bad_role', message: 'role must be browser or plugin.' }
  }
  const storage = deps.storage()
  const meta = SESSION_ID_PATTERN.test(request.id)
    ? await load<SessionMeta>(storage, `${META_PREFIX}:${request.id}`, now)
    : null
  if (!meta) {
    return { ok: false, status: 404, error: 'not_found', message: 'This session does not exist or has expired.' }
  }
  if (request.role === 'plugin') {
    const token = bearer(request.authorization)
    if (token === null || !matchesHash(token, meta.tokenHash)) {
      return { ok: false, status: 401, error: 'bad_token', message: 'The session token is missing or wrong.' }
    }
  }
  const pending: string[] = []
  if (request.role === 'plugin') {
    for (const code of meta.codes) {
      const changes = await load(storage, `${CHANGES_PREFIX}:${code}`, now)
      if (changes && !(await load(storage, `${RESULT_PREFIX}:${code}`, now))) {
        pending.push(code)
      }
    }
  }
  return { ok: true, session: request.id, role: request.role, expiresAt: meta.expiresAt, pending }
}
