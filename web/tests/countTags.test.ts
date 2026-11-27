import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { NETWORK_COUNT_TAGS, PLAYER_COUNT_TAGS, messageTagsKnown } from '../shared/editor/countTags'
import { FALLBACK_MESSAGE_PLACEHOLDERS } from '../shared/editor/templates'
import { unknownTags } from '../shared/minimessage'

const MESSAGE = fileURLToPath(new URL('../../core/src/main/java/net/serlith/sakurabans/core/message/', import.meta.url))

/** The string literals of a `List.of(...)` constant in a Java file of the plugin. */
function javaList(file: string, constant: string): string[] {
  const source = readFileSync(MESSAGE + file, 'utf8')
  const start = source.indexOf(`${constant} = List.of(`)
  expect(start).toBeGreaterThan(-1)
  const body = source.slice(start, source.indexOf(');', start))
  return [...body.matchAll(/"([a-z_]+)"/g)].map(match => match[1]!)
}

describe('count tags (S73)', () => {
  it('match the plugin', () => {
    expect(NETWORK_COUNT_TAGS).toEqual(javaList('NetworkTotals.java', 'TAGS'))
    expect(PLAYER_COUNT_TAGS).toEqual(javaList('Placeholders.java', 'PLAYER_COUNT_TAGS'))
  })

  it('are known in every message', () => {
    expect(unknownTags('<total_bans> <active_warns> <reason>', messageTagsKnown(['reason']))).toEqual([])
    expect(unknownTags('<player_bans>', messageTagsKnown(['reason']))).toEqual(['player_bans'])
  })

  it('give templates the player counts without a payload', () => {
    for (const names of Object.values(FALLBACK_MESSAGE_PLACEHOLDERS)) {
      expect(names).toEqual(expect.arrayContaining([...PLAYER_COUNT_TAGS]))
    }
  })
})
