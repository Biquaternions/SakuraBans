import type { H3Event } from 'h3'
import type { BlobApiConfig } from '../lib/blobApi'
import { createBlobApi } from '../lib/blobApi'
import { SlidingWindowLimiter } from '../lib/rateLimit'
import { KeyedMutex } from '../lib/mutex'
import { SessionHub } from '../lib/sessionHub'

function number(value: unknown, fallback: number): number {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback
}

function flag(value: unknown): boolean {
  return value === true || value === 'true' || value === '1'
}

export function editorConfig(event?: H3Event): BlobApiConfig & {
  storageDriver: string
  storageDir: string
  redisUrl: string
  purgeInterval: number
  socketSilence: number
  socketMaxBrowsers: number
} {
  const raw = useRuntimeConfig(event).editor
  return {
    storageDriver: String(raw.storageDriver || 'fs'),
    storageDir: String(raw.storageDir || './.data/editor'),
    redisUrl: String(raw.redisUrl || ''),
    payloadTtl: number(raw.payloadTtl, 86400),
    changesTtl: number(raw.changesTtl, 3600),
    maxBlobBytes: number(raw.maxBlobBytes, 1048576),
    rateLimit: number(raw.rateLimit, 30),
    rateLimitWindow: number(raw.rateLimitWindow, 600),
    trustProxy: flag(raw.trustProxy),
    uploadToken: String(raw.uploadToken ?? ''),
    liveRateLimit: number(raw.liveRateLimit, 120),
    liveRateLimitWindow: number(raw.liveRateLimitWindow, 60),
    purgeInterval: number(raw.purgeInterval, 600),
    socketSilence: number(raw.socketSilence, 60) || 60,
    socketMaxBrowsers: number(raw.socketMaxBrowsers, 5) || 5
  }
}

export const EDITOR_STORAGE = 'editor'

/** Live apply connections: in this process's memory only (protocol section 9, "Hosting"). */
export const sessionHub = new SessionHub()

export const editorDeps = {
  storage: () => useStorage(EDITOR_STORAGE),
  config: (event: H3Event) => editorConfig(event),
  limiter: new SlidingWindowLimiter(),
  liveLimiter: new SlidingWindowLimiter(),
  hub: sessionHub,
  locks: new KeyedMutex(),
  now: () => Date.now()
}

export const editorApi = createBlobApi(editorDeps)
