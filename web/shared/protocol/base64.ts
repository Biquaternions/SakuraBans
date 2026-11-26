// Base64 helpers that work the same in the browser and on Node (no Buffer).

const ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'
const LOOKUP = new Map<string, number>([...ALPHABET].map((char, index) => [char, index]))

export function encodeBase64(bytes: Uint8Array): string {
  let out = ''
  for (let i = 0; i < bytes.length; i += 3) {
    const a = bytes[i]!
    const b = i + 1 < bytes.length ? bytes[i + 1]! : 0
    const c = i + 2 < bytes.length ? bytes[i + 2]! : 0
    const triple = (a << 16) | (b << 8) | c
    out += ALPHABET[(triple >> 18) & 63]! + ALPHABET[(triple >> 12) & 63]!
    out += i + 1 < bytes.length ? ALPHABET[(triple >> 6) & 63]! : '='
    out += i + 2 < bytes.length ? ALPHABET[triple & 63]! : '='
  }
  return out
}

/** Decodes standard base64 (padding optional). Returns null for anything that isn't base64. */
export function decodeBase64(text: string): Uint8Array | null {
  const clean = text.replace(/=+$/, '')
  if (clean.length % 4 === 1 || !/^[A-Za-z0-9+/]*$/.test(clean)) {
    return null
  }
  const out = new Uint8Array(Math.floor(clean.length * 3 / 4))
  let buffer = 0
  let bits = 0
  let index = 0
  for (const char of clean) {
    buffer = (buffer << 6) | LOOKUP.get(char)!
    bits += 6
    if (bits >= 8) {
      bits -= 8
      out[index++] = (buffer >> bits) & 0xff
    }
  }
  return out
}

export function encodeBase64Url(bytes: Uint8Array): string {
  return encodeBase64(bytes).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

/** Decodes base64url without padding (a trailing "=" is tolerated). Null when malformed. */
export function decodeBase64Url(text: string): Uint8Array | null {
  if (!/^[A-Za-z0-9_-]*=*$/.test(text)) {
    return null
  }
  return decodeBase64(text.replace(/-/g, '+').replace(/_/g, '/'))
}
