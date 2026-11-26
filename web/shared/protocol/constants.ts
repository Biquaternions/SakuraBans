/** The newest `format` this site reads and writes (protocol section 8). */
export const FORMAT = 1

/** Formats this site can read. */
export const READABLE_FORMATS: readonly number[] = [1]

/** The first byte of every sealed blob (protocol section 4). */
export const SEALING_VERSION = 0x01

export const NONCE_BYTES = 12
export const TAG_BYTES = 16
export const KEY_BYTES = 32

export type BlobRole = 'payload' | 'changes' | 'result'

export function aadFor(role: BlobRole): string {
  return `sakurabans-editor:1:${role}`
}

/** Change-set codes (protocol section 3). */
export const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
export const CODE_LENGTH = 10
export const CODE_PATTERN = /^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{10}$/

/** Session ids: base64url of 16 bytes. */
export const SESSION_ID_PATTERN = /^[A-Za-z0-9_-]{22}$/
