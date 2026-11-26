// Resolution of settings and messages per server (protocol 5.1 and 5.3). The payloads are the
// fixture with scopes and servers replaced by the test, so every rule is set up explicitly.

import { describe, expect, it } from 'vitest'
import { initialDraft } from '../shared/editor/draft'
import {
  categoriesOf, effectiveMessageOverrides, effectiveOverrides, inheritedMessage, inheritedValue,
  resolveMessage, resolveSetting, unknownServer
} from '../shared/editor/layers'
import type { JsonValue, Payload, ServerInfo, SettingField } from '../shared/protocol/schema'
import { fixturePayload } from './fixtures'

const KEY = 'test.key'

function field(overrides: Partial<SettingField> = {}): SettingField {
  return { key: KEY, group: 'config', type: 'STRING', description: '', options: [], min: null, max: null,
    default: 'default', level: 'SCOPED', secret: false, ...overrides }
}

function server(name: string, own: string[] = [], file: Record<string, JsonValue> = {}): ServerInfo {
  return { id: `id-${name}`, name, categories: own, platform: 'paper', pluginVersion: '1', lastSeenAt: 0, fileValues: file }
}

function payload(scopes: Record<string, Record<string, JsonValue>>, servers: ServerInfo[], fields = [field()]): Payload {
  const base = structuredClone(fixturePayload())
  base.meta.server = servers[0]?.name ?? ''
  base.settings = {
    fields: [...fields, { ...field({ key: 'scopes.categories', type: 'MAP', default: {}, level: 'GLOBAL' }) }],
    scopes: Object.entries({ global: {}, ...scopes }).map(([scope, overrides]) => ({ scope, version: 1, overrides })),
    servers
  }
  return base
}

function resolve(p: Payload, name: string) {
  const draft = initialDraft(p)
  const target = p.settings!.servers.find(item => item.name === name) ?? unknownServer(name)
  return resolveSetting(p.settings!.fields[0]!, target, effectiveOverrides(p, draft))
}

describe('settings resolution', () => {
  it('takes server, then category, then global, then file, then default', () => {
    const lobby = server('lobby', ['hub'], { [KEY]: 'file' })
    const layers = {
      'global': { [KEY]: 'global' },
      'category:hub': { [KEY]: 'category' },
      'server:lobby': { [KEY]: 'server' }
    }
    expect(resolve(payload(layers, [lobby]), 'lobby')).toMatchObject({ value: 'server', layer: { kind: 'server' } })
    const { 'server:lobby': _server, ...noServer } = layers
    void _server
    expect(resolve(payload(noServer, [lobby]), 'lobby')).toMatchObject({ value: 'category', layer: { kind: 'category', scope: 'category:hub' } })
    expect(resolve(payload({ global: { [KEY]: 'global' } }, [lobby]), 'lobby')).toMatchObject({ value: 'global', layer: { kind: 'global' } })
    expect(resolve(payload({}, [lobby]), 'lobby')).toMatchObject({ value: 'file', layer: { kind: 'file', server: 'lobby' } })
    expect(resolve(payload({}, [server('lobby')]), 'lobby')).toMatchObject({ value: 'default', layer: { kind: 'default' } })
  })

  it('orders categories by scopes.categories, then the server\'s own; the first with the key wins', () => {
    const bw = server('bw', ['own-b', 'pvp'], { 'scopes.categories': { pvp: ['bw'], minigames: ['bw'] } })
    const p = payload({
      'category:minigames': { [KEY]: 'minigames' },
      'category:pvp': { [KEY]: 'pvp' },
      'category:own-b': { [KEY]: 'own' }
    }, [bw])
    expect(categoriesOf(bw, effectiveOverrides(p, initialDraft(p)))).toEqual(['pvp', 'minigames', 'own-b'])
    const resolved = resolve(p, 'bw')
    expect(resolved.value).toBe('pvp')
    expect(resolved.shadowed).toEqual(['minigames', 'own-b'])
  })

  it('reads scopes.categories from global when overridden there, else from the server\'s file', () => {
    const bw = server('bw', [], { 'scopes.categories': { pvp: ['bw'] } })
    const layers = { 'category:pvp': { [KEY]: 'pvp' }, 'category:arcade': { [KEY]: 'arcade' } }
    expect(resolve(payload(layers, [bw]), 'bw').value).toBe('pvp')
    const p = payload({ ...layers, global: { 'scopes.categories': { arcade: ['bw'] } } }, [bw])
    expect(resolve(p, 'bw').value).toBe('arcade')
  })

  it('moves other servers at once when the draft edits scopes.categories', () => {
    const a = server('a', [], { [KEY]: 'file-a' })
    const b = server('b', [], { [KEY]: 'file-b' })
    const p = payload({ 'category:arcade': { [KEY]: 'arcade' } }, [a, b])
    const draft = initialDraft(p)
    const before = effectiveOverrides(p, draft)
    expect(resolveSetting(p.settings!.fields[0]!, b, before).value).toBe('file-b')
    draft.settings.global = { 'scopes.categories': { kind: 'set', value: { arcade: ['b'] } } }
    const after = effectiveOverrides(p, draft)
    expect(resolveSetting(p.settings!.fields[0]!, b, after)).toMatchObject({ value: 'arcade', layer: { kind: 'category' } })
    expect(resolveSetting(p.settings!.fields[0]!, a, after).value).toBe('file-a')
  })

  it('applies pending sets, resets and publishing', () => {
    const lobby = server('lobby', [], { [KEY]: 'file' })
    const p = payload({ 'server:lobby': { [KEY]: 'server' } }, [lobby])
    const draft = initialDraft(p)
    draft.settings['server:lobby'] = { [KEY]: { kind: 'reset' } }
    expect(resolveSetting(p.settings!.fields[0]!, lobby, effectiveOverrides(p, draft)).value).toBe('file')
    draft.publish = { server: 'lobby', keys: [KEY] }
    expect(resolveSetting(p.settings!.fields[0]!, lobby, effectiveOverrides(p, draft))).toMatchObject({ value: 'file', layer: { kind: 'global' } })
  })

  it('gives an unnamed server no server scope; it joins categories only through its own', () => {
    const anon = server('', ['own'], { 'scopes.categories': { listed: [''] } })
    const p = payload({ 'category:own': { [KEY]: 'own' }, 'category:listed': { [KEY]: 'listed' } }, [anon])
    expect(categoriesOf(anon, effectiveOverrides(p, initialDraft(p)))).toEqual(['own'])
    expect(resolve(p, '').value).toBe('own')
  })

  it('reports a server\'s file layer without a value for a server it doesn\'t know', () => {
    const p = payload({}, [server('lobby')])
    expect(resolve(p, 'elsewhere')).toMatchObject({ value: undefined, layer: { kind: 'file', server: 'elsewhere' } })
  })

  it('shows a secret only as set or not', () => {
    const lobby = server('lobby', [], { [KEY]: true })
    const p = payload({}, [lobby], [field({ secret: true, default: '' })])
    const draft = initialDraft(p)
    draft.settings['server:lobby'] = { [KEY]: { kind: 'set', value: 'https://secret.example' } }
    expect(resolveSetting(p.settings!.fields[0]!, lobby, effectiveOverrides(p, draft)).value).toBe(true)
    draft.settings['server:lobby'] = { [KEY]: { kind: 'set', value: '' } }
    expect(resolveSetting(p.settings!.fields[0]!, lobby, effectiveOverrides(p, draft)).value).toBe(false)
  })
})

describe('what a scope inherits', () => {
  it('global: the uploading server\'s file, with the servers that differ', () => {
    const p = payload({}, [server('lobby', [], { [KEY]: 'x' }), server('a', [], { [KEY]: 'x' }), server('b', [], { [KEY]: 'y' })])
    const inherited = inheritedValue(p, p.settings!.fields[0]!, 'global', effectiveOverrides(p, initialDraft(p)))
    expect(inherited).toMatchObject({ value: 'x', layer: { kind: 'file', server: 'lobby' }, differing: ['b'] })
  })

  it('category: global, else the file; server: everything below its own scope', () => {
    const lobby = server('lobby', ['hub'], { [KEY]: 'file' })
    const p = payload({ 'global': { [KEY]: 'global' }, 'category:hub': { [KEY]: 'hub' }, 'server:lobby': { [KEY]: 'own' } }, [lobby])
    const overrides = effectiveOverrides(p, initialDraft(p))
    const f = p.settings!.fields[0]!
    expect(inheritedValue(p, f, 'category:hub', overrides)).toMatchObject({ value: 'global', layer: { kind: 'global' } })
    expect(inheritedValue(p, f, 'server:lobby', overrides)).toMatchObject({ value: 'hub', layer: { kind: 'category' } })
  })
})

describe('messages resolution', () => {
  function messagesPayload(): Payload {
    const p = payload({}, [server('lobby', ['hub']), server('other', ['hub'])])
    const key = p.messages!.fields[0]!.key
    p.messages!.locales = [
      { locale: 'en_US', fileTexts: { [key]: 'english file' } },
      { locale: 'de_DE', fileTexts: {} }
    ]
    p.messages!.scopes = [
      { scope: 'global', locale: 'en_US', version: 1, overrides: { [key]: 'global en' } },
      { scope: 'category:hub', locale: 'de_DE', version: 1, overrides: { [key]: 'hub de' } }
    ]
    return p
  }

  it('resolves per locale, through categories, to the uploading server\'s file, then en_US, then the default', () => {
    const p = messagesPayload()
    const key = p.messages!.fields[0]!.key
    const draft = initialDraft(p)
    const messages = effectiveMessageOverrides(p, draft)
    const settings = effectiveOverrides(p, draft)
    const lobby = p.settings!.servers[0]!
    expect(resolveMessage(p, messages, settings, lobby, 'de_DE', key)).toMatchObject({ text: 'hub de', layer: { kind: 'category' } })
    expect(resolveMessage(p, messages, settings, lobby, 'en_US', key)).toMatchObject({ text: 'global en', layer: { kind: 'global' } })
    expect(resolveMessage(p, messages, settings, lobby, 'fr_FR', key)).toMatchObject({ text: 'english file', layer: { kind: 'file' } })
  })

  it('knows file texts only for the uploading server', () => {
    const p = messagesPayload()
    const key = p.messages!.fields[0]!.key
    const draft = initialDraft(p)
    const messages = effectiveMessageOverrides(p, draft)
    const settings = effectiveOverrides(p, draft)
    const other = p.settings!.servers[1]!
    expect(resolveMessage(p, messages, settings, other, 'fr_FR', key)).toMatchObject({ text: undefined, layer: { kind: 'file', server: 'other' } })
  })

  it('applies pending edits and shows what each scope inherits', () => {
    const p = messagesPayload()
    const key = p.messages!.fields[0]!.key
    const draft = initialDraft(p)
    draft.messages['server:lobby'] = { en_US: { [key]: { kind: 'set', text: 'lobby only' } } }
    draft.messages.global = { en_US: { [key]: { kind: 'reset' } } }
    const messages = effectiveMessageOverrides(p, draft)
    const settings = effectiveOverrides(p, draft)
    expect(resolveMessage(p, messages, settings, p.settings!.servers[0]!, 'en_US', key).text).toBe('lobby only')
    expect(resolveMessage(p, messages, settings, p.settings!.servers[1]!, 'en_US', key).layer.kind).toBe('file')
    expect(inheritedMessage(p, messages, settings, 'server:lobby', 'de_DE', key)).toMatchObject({ text: 'hub de' })
    expect(inheritedMessage(p, messages, settings, 'global', 'en_US', key)).toMatchObject({ text: 'english file', layer: { kind: 'file' } })
  })
})
