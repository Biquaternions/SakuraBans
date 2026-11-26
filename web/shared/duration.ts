// The plugin's duration syntax, mirrored from core/util/Durations.java.

const MINUTE = 60
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR
const WEEK = 7 * DAY
const MONTH = 30 * DAY
const YEAR = 365 * DAY

const UNITS = new Map<string, number>([
  ['s', 1], ['sec', 1], ['secs', 1], ['second', 1], ['seconds', 1],
  ['m', MINUTE], ['min', MINUTE], ['mins', MINUTE], ['minute', MINUTE], ['minutes', MINUTE],
  ['h', HOUR], ['hr', HOUR], ['hrs', HOUR], ['hour', HOUR], ['hours', HOUR],
  ['d', DAY], ['day', DAY], ['days', DAY],
  ['w', WEEK], ['wk', WEEK], ['week', WEEK], ['weeks', WEEK],
  ['mo', MONTH], ['mon', MONTH], ['month', MONTH], ['months', MONTH],
  ['y', YEAR], ['yr', YEAR], ['year', YEAR], ['years', YEAR]
])

// "ms" (milliseconds) is its own unit, not "m" + "s" (Durations.unit).
const MILLIS_PER_SECOND = 1000n
const PART = /^(\d{1,12})(?:\.(\d{1,6}))?([a-z]+)/
const ZERO = /^0+(?:\.0+)?([a-z]*)$/
const PERMANENT = new Set(['perm', 'permanent', 'forever'])
// Long.MAX_VALUE / 1000, in seconds, as Durations.MAX_SECONDS.
const MAX_SECONDS = 9223372036854775n
const MICROS = 1000000n

/**
 * Parses `90m`, `1.5h`, `1d12h`, `2w`, `1mo`, `1y`, `250ms` and the long unit names, in any case, to
 * milliseconds. Zero, unknown units, a missing unit, spaces between parts and values too large
 * are rejected (null), exactly as the plugin does.
 */
export function parseDuration(text: string): number | null {
  const input = text.trim().toLowerCase()
  if (input.length === 0) {
    return null
  }
  let rest = input
  let micros = 0n
  while (rest.length > 0) {
    const match = PART.exec(rest)
    if (!match) {
      return null
    }
    const name = match[3]!
    const unit = name === 'ms' ? null : UNITS.get(name)
    if (unit === undefined) {
      return null
    }
    const fraction = (match[2] ?? '').padEnd(6, '0')
    const amount = BigInt(match[1]!) * MICROS + BigInt(fraction)
    // micros counts micro-seconds: amount is the number scaled by 10^6.
    micros += unit === null ? amount / MILLIS_PER_SECOND : amount * BigInt(unit)
    if (micros > MAX_SECONDS * MICROS) {
      return null
    }
    rest = rest.slice(match[0].length)
  }
  // Round half up to milliseconds, as BigDecimal.setScale(0, HALF_UP) does for positives.
  const millis = (micros + 500n) / 1000n
  return millis > 0n ? Number(millis) : null
}

/** Whether the text is zero (`0`, `0s`), which parseDuration rejects. */
export function isZeroDuration(text: string): boolean {
  const match = ZERO.exec(text.trim().toLowerCase())
  return match !== null && (match[1] === '' || match[1] === 'ms' || UNITS.has(match[1]!))
}

export function isPermanentToken(text: string): boolean {
  return PERMANENT.has(text.trim().toLowerCase())
}

const COMPACT: ReadonlyArray<[string, number]> = [
  ['y', YEAR], ['d', DAY], ['h', HOUR], ['m', MINUTE], ['s', 1]
]

/** `1y2d3h4m5s` (as the plugin writes durations into files); whole seconds only. */
export function formatCompact(millis: number): string {
  let seconds = Math.max(0, Math.floor(millis / 1000))
  if (seconds === 0) {
    return '0s'
  }
  let out = ''
  for (const [unit, size] of COMPACT) {
    const count = Math.floor(seconds / size)
    if (count > 0) {
      out += `${count}${unit}`
      seconds -= count * size
    }
  }
  return out
}

const LONG: ReadonlyArray<[string, number]> = [
  ['year', YEAR], ['day', DAY], ['hour', HOUR], ['minute', MINUTE], ['second', 1]
]

/** "1 day, 12 hours and 5 seconds", for hints in the editor. */
export function describeDuration(millis: number): string {
  let seconds = Math.floor(millis / 1000)
  if (seconds <= 0) {
    return millis > 0 ? `${millis} ms` : '0 seconds'
  }
  const parts: string[] = []
  for (const [unit, size] of LONG) {
    const count = Math.floor(seconds / size)
    if (count > 0) {
      parts.push(`${count} ${unit}${count === 1 ? '' : 's'}`)
      seconds -= count * size
    }
  }
  return parts.length === 1 ? parts[0]! : `${parts.slice(0, -1).join(', ')} and ${parts.at(-1)}`
}

export interface DurationRules {
  /** `0`/`0s` is accepted (for example notifications.attempt-throttle). */
  zero?: boolean
  /** `permanent`, `perm`, `forever` are accepted (ladder steps). */
  permanent?: boolean
  /** `never` is accepted (settings documented as "a duration, or never"). */
  never?: boolean
  /** `none` (or empty) is accepted (ladder-expiry). */
  none?: boolean
}

export type DurationCheck
  = | { ok: true, kind: 'duration', millis: number }
    | { ok: true, kind: 'zero' | 'permanent' | 'never' | 'none' }
    | { ok: false, message: string }

export function checkDuration(text: string, rules: DurationRules = {}): DurationCheck {
  const trimmed = text.trim().toLowerCase()
  if (rules.none && (trimmed === '' || trimmed === 'none')) {
    return { ok: true, kind: 'none' }
  }
  if (rules.never && trimmed === 'never') {
    return { ok: true, kind: 'never' }
  }
  if (rules.permanent && isPermanentToken(trimmed)) {
    return { ok: true, kind: 'permanent' }
  }
  if (rules.zero && isZeroDuration(trimmed)) {
    return { ok: true, kind: 'zero' }
  }
  const millis = parseDuration(trimmed)
  if (millis !== null) {
    return { ok: true, kind: 'duration', millis }
  }
  const extras = [
    rules.permanent ? 'permanent' : null,
    rules.never ? 'never' : null,
    rules.none ? 'none' : null
  ].filter(Boolean)
  const tail = extras.length > 0 ? `, or ${extras.join(' or ')}` : ''
  if (trimmed === '') {
    return { ok: false, message: `Enter a duration (like 30m, 7d or 1mo)${tail}.` }
  }
  if (isZeroDuration(trimmed)) {
    return { ok: false, message: 'Must be longer than zero.' }
  }
  return { ok: false, message: `Not a duration (like 250ms, 30s, 1h30m, 7d, 2w, 1mo, 1y)${tail}.` }
}
