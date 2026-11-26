import { describe, expect, it } from 'vitest'
import {
  checkDuration, describeDuration, formatCompact, isPermanentToken, isZeroDuration, parseDuration
} from '../shared/duration'

const S = 1000
const M = 60 * S
const H = 60 * M
const D = 24 * H

describe('parseDuration (mirrors Durations.parse)', () => {
  it.each([
    ['30s', 30 * S], ['10m', 10 * M], ['1h30m', H + 30 * M], ['7d', 7 * D], ['2w', 14 * D],
    ['1mo', 30 * D], ['1y', 365 * D], ['90m', 90 * M], ['1.5h', 1.5 * H], ['1d12h', 36 * H],
    ['1D', D], ['  2H ', 2 * H], ['3 '.trim() + 'days', 3 * D], ['1hour30minutes', H + 30 * M],
    ['1week', 7 * D], ['2months', 60 * D], ['1yr', 365 * D], ['0.001s', 1], ['0.0005s', 1],
    ['250ms', 250], ['1ms', 1], ['1s500ms', 1500], ['1.5ms', 2]
  ])('%s', (text, millis) => {
    expect(parseDuration(text)).toBe(millis)
  })

  it.each(['', '   ', '0', '0s', '0.0004s', '10', 'h', '10x', '1msx', '1h 30m', '-1h', '1.h',
    '1.1234567h', '1234567890123s', 'permanent', 'never', '1h-'])('rejects %j', (text) => {
    expect(parseDuration(text)).toBeNull()
  })

  it('rejects values too large for a millisecond count', () => {
    expect(parseDuration('999999999999y')).toBeNull()
    expect(parseDuration('292000y')).not.toBeNull()
  })
})

describe('zero and permanent tokens', () => {
  it('recognises zero', () => {
    expect(isZeroDuration('0')).toBe(true)
    expect(isZeroDuration('0s')).toBe(true)
    expect(isZeroDuration('00.00m')).toBe(true)
    expect(isZeroDuration('0ms')).toBe(true)
    expect(isZeroDuration('0x')).toBe(false)
    expect(isZeroDuration('1s')).toBe(false)
  })

  it('recognises permanent', () => {
    for (const token of ['perm', 'permanent', 'FOREVER']) {
      expect(isPermanentToken(token)).toBe(true)
    }
    expect(isPermanentToken('never')).toBe(false)
  })
})

describe('formatting', () => {
  it('formats compactly like the plugin', () => {
    expect(formatCompact(0)).toBe('0s')
    expect(formatCompact(90 * M)).toBe('1h30m')
    expect(formatCompact(400 * D + 5 * S)).toBe('1y35d5s')
    expect(parseDuration(formatCompact(400 * D + 5 * S))).toBe(400 * D + 5 * S)
  })

  it('describes in words', () => {
    expect(describeDuration(D + 12 * H + 5 * S)).toBe('1 day, 12 hours and 5 seconds')
    expect(describeDuration(30 * D)).toBe('30 days')
  })
})

describe('checkDuration rules', () => {
  it('applies the extra words only where allowed', () => {
    expect(checkDuration('permanent', { permanent: true })).toEqual({ ok: true, kind: 'permanent' })
    expect(checkDuration('permanent').ok).toBe(false)
    expect(checkDuration('never', { never: true })).toEqual({ ok: true, kind: 'never' })
    expect(checkDuration('none', { none: true })).toEqual({ ok: true, kind: 'none' })
    expect(checkDuration('0s', { zero: true })).toEqual({ ok: true, kind: 'zero' })
    expect(checkDuration('0s')).toEqual({ ok: false, message: 'Must be longer than zero.' })
    expect(checkDuration('7d')).toEqual({ ok: true, kind: 'duration', millis: 7 * D })
  })
})
