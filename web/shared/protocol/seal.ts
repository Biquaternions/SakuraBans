// Sealing per protocol section 4:
// blob = 0x01 | nonce (12 bytes) | AES-256-GCM(key, nonce, aad, gzip(utf8(json)))

import { decodeBase64Url, encodeBase64Url } from './base64'
import { aadFor, KEY_BYTES, NONCE_BYTES, SEALING_VERSION, TAG_BYTES } from './constants'
import type { BlobRole } from './constants'

/** The one error for a blob that doesn't open; it carries no detail on purpose. */
export class SealError extends Error {
  constructor() {
    super('This data could not be decrypted with the key in the link.')
    this.name = 'SealError'
  }
}

const encoder = new TextEncoder()

// WebCrypto wants ArrayBuffer-backed views; copying also detaches us from caller buffers.
function view(bytes: Uint8Array): Uint8Array<ArrayBuffer> {
  const copy = new Uint8Array(new ArrayBuffer(bytes.length))
  copy.set(bytes)
  return copy
}

async function pipe(bytes: Uint8Array, transform: CompressionStream | DecompressionStream):
Promise<Uint8Array> {
  const stream = new Blob([view(bytes)]).stream().pipeThrough(transform)
  return new Uint8Array(await new Response(stream).arrayBuffer())
}

export function gzip(bytes: Uint8Array): Promise<Uint8Array> {
  return pipe(bytes, new CompressionStream('gzip'))
}

export function gunzip(bytes: Uint8Array): Promise<Uint8Array> {
  return pipe(bytes, new DecompressionStream('gzip'))
}

export function parseKey(text: string): Uint8Array | null {
  const bytes = decodeBase64Url(text.trim())
  return bytes !== null && bytes.length === KEY_BYTES ? bytes : null
}

export function formatKey(key: Uint8Array): string {
  return encodeBase64Url(key)
}

export function generateKey(): Uint8Array {
  return crypto.getRandomValues(new Uint8Array(KEY_BYTES))
}

function importKey(key: Uint8Array): Promise<CryptoKey> {
  if (key.length !== KEY_BYTES) {
    throw new SealError()
  }
  return crypto.subtle.importKey('raw', view(key), { name: 'AES-GCM' }, false,
    ['encrypt', 'decrypt'])
}

/** Seals plaintext bytes (already UTF-8 JSON). `nonce` is for tests only. */
export async function sealBytes(key: Uint8Array, role: BlobRole, plaintext: Uint8Array,
  nonce: Uint8Array = crypto.getRandomValues(new Uint8Array(NONCE_BYTES))): Promise<Uint8Array> {
  if (nonce.length !== NONCE_BYTES) {
    throw new Error('nonce must be 12 bytes')
  }
  const cryptoKey = await importKey(key)
  const compressed = await gzip(plaintext)
  const sealed = new Uint8Array(await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv: view(nonce), additionalData: encoder.encode(aadFor(role)), tagLength: 128 },
    cryptoKey, view(compressed)))
  const blob = new Uint8Array(1 + NONCE_BYTES + sealed.length)
  blob[0] = SEALING_VERSION
  blob.set(nonce, 1)
  blob.set(sealed, 1 + NONCE_BYTES)
  return blob
}

/** Opens a blob to its plaintext bytes; throws SealError for anything that doesn't open. */
export async function openBytes(key: Uint8Array, role: BlobRole, blob: Uint8Array):
Promise<Uint8Array> {
  if (blob.length < 1 + NONCE_BYTES + TAG_BYTES || blob[0] !== SEALING_VERSION) {
    throw new SealError()
  }
  try {
    const cryptoKey = await importKey(key)
    const compressed = await crypto.subtle.decrypt(
      { name: 'AES-GCM', iv: view(blob.subarray(1, 1 + NONCE_BYTES)),
        additionalData: encoder.encode(aadFor(role)), tagLength: 128 },
      cryptoKey, view(blob.subarray(1 + NONCE_BYTES)))
    return await gunzip(new Uint8Array(compressed))
  } catch {
    throw new SealError()
  }
}

export async function sealJson(key: Uint8Array, role: BlobRole, value: unknown):
Promise<Uint8Array> {
  return sealBytes(key, role, encoder.encode(JSON.stringify(value)))
}

/** Opens a blob and parses its JSON (unvalidated: run it through the schemas). */
export async function openJson(key: Uint8Array, role: BlobRole, blob: Uint8Array):
Promise<unknown> {
  const bytes = await openBytes(key, role, blob)
  try {
    return JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes)) as unknown
  } catch {
    throw new SealError()
  }
}
