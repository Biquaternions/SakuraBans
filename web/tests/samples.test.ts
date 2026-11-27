import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { sampleFor } from '../app/utils/samples'

const PLACEHOLDERS = fileURLToPath(new URL('../../core/src/main/java/net/serlith/sakurabans/core/message/Placeholders.java', import.meta.url))

describe('placeholder samples', () => {
  it('give the country tag a country and /geoip its code', () => {
    const tag = /COUNTRY_TAG = "([a-z_]+)"/.exec(readFileSync(PLACEHOLDERS, 'utf8'))?.[1]
    expect(tag).toBe('country')
    expect(sampleFor(tag!)).toBe('United Kingdom')
    expect(sampleFor('code')).toBe('GB')
  })
})
