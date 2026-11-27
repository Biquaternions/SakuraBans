import { describe, expect, it } from 'vitest'
import { editorTabs, recentSubject, recentText } from '../shared/editor/recent'
import { payloadSchema } from '../shared/protocol/schema'
import type { RecentChange } from '../shared/protocol/schema'
import { fixtureJson, fixturePayload } from './fixtures'

describe('recent changes (protocol 5.6)', () => {
  it('the payload fixture carries them, newest first', () => {
    const recent = fixturePayload().recentChanges ?? []
    expect(recent.map(change => change.kind)).toEqual(['settings', 'messages', 'templates'])
    expect(recent.map(change => change.at)).toEqual([...recent.map(change => change.at)].sort((a, b) => b - a))
  })

  it('carry no value: a settings entry names keys only', () => {
    for (const change of fixturePayload().recentChanges ?? []) {
      expect(Object.keys(change).sort()).toEqual(['action', 'actor', 'at', 'keys', 'kind', 'locale', 'scope', 'target'])
    }
  })

  it('are optional: a payload without them parses and has none', () => {
    const json = structuredClone(fixtureJson('payload-v1.json')) as Record<string, unknown>
    delete json.recentChanges
    const result = payloadSchema.safeParse(json)
    expect(result.error?.issues ?? []).toEqual([])
    expect(result.data?.recentChanges).toBeUndefined()
  })

  it('are ignored, not fatal, when they are not what this site knows', () => {
    const json = structuredClone(fixtureJson('payload-v1.json')) as Record<string, unknown>
    json.recentChanges = [{ at: 'yesterday' }]
    const result = payloadSchema.safeParse(json)
    expect(result.error?.issues ?? []).toEqual([])
    expect(result.data?.recentChanges).toBeUndefined()
    expect(result.data?.settings).toBeDefined()
  })

  it('read as a sentence', () => {
    const [settings, messages, templates] = fixturePayload().recentChanges as [RecentChange, RecentChange, RecentChange]
    expect(recentText(settings)).toBe('Alex updated settings reasons.default in server:skywars')
    expect(recentText(messages)).toBe('Console updated message screen.ban (en_US) in global')
    expect(recentText(templates)).toBe('Mod deleted template lifesteal in global')
    expect(recentSubject({ ...settings, keys: [] })).toBe('settings')
    expect(recentText({ ...settings, keys: [] })).toBe('Alex updated settings in server:skywars')
    expect(recentText({ ...templates, action: 'apply' })).toBe('Mod apply template lifesteal in global')
  })
})

describe('the Recent changes tab', () => {
  it('comes last, and only when there are changes', () => {
    expect(editorTabs(['settings', 'messages', 'templates'], 3)).toEqual(['settings', 'messages', 'templates', 'recent'])
    expect(editorTabs(['settings', 'messages', 'templates'], 0)).toEqual(['settings', 'messages', 'templates'])
    expect(editorTabs([], 2)).toEqual(['recent'])
  })

  it('the fixture payload has entries to show in it', () => {
    expect(editorTabs(['settings'], (fixturePayload().recentChanges ?? []).length)).toEqual(['settings', 'recent'])
  })
})
