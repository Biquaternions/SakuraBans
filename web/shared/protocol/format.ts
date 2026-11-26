// Format detection before schema validation (protocol section 8).

import { FORMAT, READABLE_FORMATS } from './constants'
import { payloadSchema } from './schema'
import type { Payload } from './schema'

export type PayloadResult
  = | { ok: true, payload: Payload }
    | { ok: false, reason: 'newer-format', format: number }
    | { ok: false, reason: 'older-format', format: number }
    | { ok: false, reason: 'invalid', issues: string[] }

export function readPayload(value: unknown): PayloadResult {
  const format = typeof value === 'object' && value !== null && 'format' in value
    ? (value as { format: unknown }).format
    : undefined
  if (typeof format !== 'number' || !Number.isInteger(format)) {
    return { ok: false, reason: 'invalid', issues: ['format: missing or not a number'] }
  }
  if (format > FORMAT) {
    return { ok: false, reason: 'newer-format', format }
  }
  if (!READABLE_FORMATS.includes(format)) {
    return { ok: false, reason: 'older-format', format }
  }
  const parsed = payloadSchema.safeParse(value)
  if (!parsed.success) {
    return {
      ok: false,
      reason: 'invalid',
      issues: parsed.error.issues.slice(0, 20).map(issue =>
        `${issue.path.join('.') || '(root)'}: ${issue.message}`)
    }
  }
  return { ok: true, payload: parsed.data }
}
