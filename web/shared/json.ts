// Small JSON helpers: canonical comparison and cloning.

import type { JsonValue } from './protocol/schema'

/** JSON with object keys sorted, so two values compare equal regardless of key order. */
export function canonicalJson(value: unknown): string {
  return JSON.stringify(sortKeys(value))
}

function sortKeys(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(sortKeys)
  }
  if (typeof value === 'object' && value !== null) {
    const sorted: Record<string, unknown> = {}
    for (const key of Object.keys(value).sort()) {
      sorted[key] = sortKeys((value as Record<string, unknown>)[key])
    }
    return sorted
  }
  return value
}

export function jsonEqual(a: unknown, b: unknown): boolean {
  return canonicalJson(a) === canonicalJson(b)
}

export function cloneJson<T extends JsonValue | object>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

/** Removes a key from a record (reactive proxies included). */
export function removeKey(record: object, key: string): void {
  Reflect.deleteProperty(record, key)
}
