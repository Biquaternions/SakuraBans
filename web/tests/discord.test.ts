import { describe, expect, it } from 'vitest'
import { fillPlaceholders, parseDiscord, webhookPlaceholders } from '../shared/discord'

describe('parseDiscord', () => {
  it('reads bold, italic, underline, strikethrough and nesting', () => {
    expect(parseDiscord('a **b** *c* _d_ __e__ ~~f~~')).toEqual([
      { text: 'a ', style: {} },
      { text: 'b', style: { bold: true } },
      { text: ' ', style: {} },
      { text: 'c', style: { italic: true } },
      { text: ' ', style: {} },
      { text: 'd', style: { italic: true } },
      { text: ' ', style: {} },
      { text: 'e', style: { underline: true } },
      { text: ' ', style: {} },
      { text: 'f', style: { strike: true } }
    ])
    expect(parseDiscord('**bold *both***')).toEqual([
      { text: 'bold ', style: { bold: true } },
      { text: 'both', style: { bold: true, italic: true } }
    ])
  })

  it('keeps code literal and shows links as text', () => {
    expect(parseDiscord('`**x**` [site](https://example.org)')).toEqual([
      { text: '**x**', style: { code: true } },
      { text: ' ', style: {} },
      { text: 'site', style: { link: true } }
    ])
  })

  it('leaves unmatched markers, escapes and markup as plain text', () => {
    expect(parseDiscord('2 * 3 and \\*not\\*')).toEqual([{ text: '2 * 3 and *not*', style: {} }])
    expect(parseDiscord('<img src=x onerror=alert(1)>')).toEqual([{ text: '<img src=x onerror=alert(1)>', style: {} }])
  })
})

describe('placeholders', () => {
  it('fills known placeholders and keeps unknown ones', () => {
    expect(fillPlaceholders('{victim} by {operator} {nope}', { victim: 'Steve', operator: 'Alex' })).toBe('Steve by Alex {nope}')
  })

  it('knows which placeholders an event has', () => {
    expect(webhookPlaceholders('rollback')).toEqual(['victim', 'revoker', 'revoke_reason', 'count'])
    expect(webhookPlaceholders('edit')).toContain('edit')
    expect(webhookPlaceholders('ban')).not.toContain('edit')
  })
})
