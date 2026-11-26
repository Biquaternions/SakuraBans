// Change-set building against the payload fixture. Keys, names and versions are read from the
// fixture, never written here, so a fixture change needs no test change.

import { describe, expect, it } from 'vitest'
import {
  buildChangeSet, displayValue, initialDraft, listChanges, revertChange, validateDraft
} from '../shared/editor/draft'
import {
  definitionToForm, emptyStep, emptyTemplate, formatStep, formToDefinition, parseStep, sameDefinition,
  stepLabel, summarizeTemplate
} from '../shared/editor/templates'
import { exportTemplatesYaml, importTemplatesYaml } from '../shared/editor/templatesYaml'
import type { Payload, SettingField } from '../shared/protocol/schema'
import { fixturePayload } from './fixtures'

const payload = (): Payload => fixturePayload()

function field(p: Payload, test: (item: SettingField) => boolean): SettingField {
  const found = p.settings!.fields.find(test)
  if (!found) {
    throw new Error('the payload fixture has no such setting')
  }
  return found
}

function scoped(p: Payload) {
  return p.settings!.fields.find(item => item.level === 'SCOPED' && !item.secret && item.type === 'STRING')!
}

const NEW_SCOPE = 'server:test-new-scope'

describe('settings changes per scope', () => {
  it('puts each scope in its own block with its version, 0 for a scope without overrides', () => {
    const p = payload()
    const draft = initialDraft(p)
    const key = scoped(p).key
    const listed = p.settings!.scopes.find(item => item.scope !== 'global')!
    draft.settings[listed.scope] = { [key]: { kind: 'set', value: 'listed' } }
    draft.settings[NEW_SCOPE] = { [key]: { kind: 'set', value: 'new' } }
    expect(buildChangeSet(p, draft).settings).toEqual([
      { scope: listed.scope, expectedVersion: listed.version, set: { [key]: 'listed' }, reset: [] },
      { scope: NEW_SCOPE, expectedVersion: 0, set: { [key]: 'new' }, reset: [] }
    ])
  })

  it('leaves out a set equal to the scope\'s override and a reset where there is none', () => {
    const p = payload()
    const draft = initialDraft(p)
    const scope = p.settings!.scopes.find(item => item.scope !== 'global' && Object.keys(item.overrides).length > 0)!
    const [key, value] = Object.entries(scope.overrides)[0]!
    draft.settings[scope.scope] = { [key]: { kind: 'set', value } }
    draft.settings[NEW_SCOPE] = { [key]: { kind: 'reset' } }
    expect(buildChangeSet(p, draft).settings).toBeUndefined()
    expect(listChanges(p, draft)).toEqual([])
  })

  it('refuses a GLOBAL-level key outside global, and bad scope names', () => {
    const p = payload()
    const draft = initialDraft(p)
    const global = field(p, item => item.level === 'GLOBAL' && item.type === 'ENUM')
    draft.settings[NEW_SCOPE] = { [global.key]: { kind: 'set', value: global.options[0]! } }
    draft.settings['server:Not Valid'] = { [scoped(p).key]: { kind: 'set', value: 'x' } }
    const messages = validateDraft(p, draft).map(issue => issue.message)
    expect(messages).toContain('only the global scope can override this key')
    expect(messages.some(message => message.startsWith('is not a scope'))).toBe(true)
  })

  it('validates types and bounds', () => {
    const p = payload()
    const draft = initialDraft(p)
    const enumField = field(p, item => item.type === 'ENUM')
    const bounded = field(p, item => item.type === 'INTEGER' && item.min !== null)
    const duration = field(p, item => item.type === 'DURATION')
    draft.settings.global = {
      [enumField.key]: { kind: 'set', value: 'NOT_AN_OPTION' },
      [bounded.key]: { kind: 'set', value: bounded.min! - 1 },
      [duration.key]: { kind: 'set', value: '5 minutes' }
    }
    const paths = validateDraft(p, draft).map(issue => issue.path)
    expect(paths.sort()).toEqual([
      `settings[global].set.${bounded.key}`, `settings[global].set.${duration.key}`, `settings[global].set.${enumField.key}`
    ].sort())
    draft.settings.global[duration.key] = { kind: 'set', value: '250ms' }
    expect(validateDraft(p, draft).some(issue => issue.path.endsWith(duration.key))).toBe(false)
  })
})

describe('publish', () => {
  it('goes only into the global block, and never with a set of the same key', () => {
    const p = payload()
    const draft = initialDraft(p)
    const server = p.settings!.servers[0]!
    const key = Object.keys(server.fileValues).find(k => !(k in p.settings!.scopes[0]!.overrides))!
    draft.publish = { server: server.name, keys: [key] }
    const [block] = buildChangeSet(p, draft).settings!
    expect(block).toMatchObject({ scope: 'global', publish: { server: server.name, keys: [key] } })
    draft.settings.global = { [key]: { kind: 'set', value: server.fileValues[key]! } }
    expect(validateDraft(p, draft).map(issue => issue.path)).toContain(`settings[global].publish.${key}`)
  })

  it('lists published keys in the review, and undoes them one by one', () => {
    const p = payload()
    const draft = initialDraft(p)
    const server = p.settings!.servers[0]!
    const keys = Object.keys(server.fileValues).slice(0, 2)
    draft.publish = { server: server.name, keys }
    const items = listChanges(p, draft)
    expect(items.map(item => item.kind)).toEqual(['publish', 'publish'])
    revertChange(p, draft, items[0]!.ref)
    revertChange(p, draft, items[1]!.ref)
    expect(draft.publish).toBeNull()
  })
})

describe('secrets', () => {
  it('sends a new value or an empty override, and never shows either', () => {
    const p = payload()
    const secret = p.settings!.fields.find(item => item.secret)
    if (!secret) {
      return
    }
    const draft = initialDraft(p)
    draft.settings[NEW_SCOPE] = { [secret.key]: { kind: 'set', value: 'https://hooks.example/abc' } }
    expect(buildChangeSet(p, draft).settings).toEqual([
      { scope: NEW_SCOPE, expectedVersion: 0, set: { [secret.key]: 'https://hooks.example/abc' }, reset: [] }
    ])
    const [item] = listChanges(p, draft)
    expect(JSON.stringify(item)).not.toContain('hooks.example')
    expect(item!.after).toBe(displayValue('https://hooks.example/abc', true))
    draft.settings[NEW_SCOPE] = { [secret.key]: { kind: 'set', value: 'not a url' } }
    expect(validateDraft(p, draft)[0]?.message).toContain('http(s)')
  })
})

describe('message changes per scope', () => {
  it('builds one block per scope and locale, with version 0 for a new pair', () => {
    const p = payload()
    const draft = initialDraft(p)
    const key = p.messages!.fields[0]!.key
    const listed = p.messages!.scopes[0]!
    draft.messages[listed.scope] = { [listed.locale]: { [key]: { kind: 'set', text: '<red>changed' } } }
    draft.messages[NEW_SCOPE] = { [listed.locale]: { [key]: { kind: 'set', text: '<red>new' } } }
    expect(buildChangeSet(p, draft).messages).toEqual([
      { scope: listed.scope, locale: listed.locale, expectedVersion: listed.version, set: { [key]: '<red>changed' }, reset: [] },
      { scope: NEW_SCOPE, locale: listed.locale, expectedVersion: 0, set: { [key]: '<red>new' }, reset: [] }
    ])
  })

  it('creates a new locale with expectedVersion 0, and rejects bad names', () => {
    const p = payload()
    const draft = initialDraft(p)
    const key = p.messages!.fields[0]!.key
    draft.addedLocales.push('xx_XX', 'german')
    draft.messages.global = { xx_XX: { [key]: { kind: 'set', text: '<red>x' } } }
    expect(buildChangeSet(p, draft).messages).toEqual([{ scope: 'global', locale: 'xx_XX', expectedVersion: 0, set: { [key]: '<red>x' }, reset: [] }])
    expect(validateDraft(p, draft).some(issue => issue.message.includes('en_US'))).toBe(true)
  })

  it('resets only existing overrides and refuses empty texts', () => {
    const p = payload()
    const draft = initialDraft(p)
    const listed = p.messages!.scopes[0]!
    const key = Object.keys(listed.overrides)[0]!
    const other = p.messages!.fields.find(item => !(item.key in listed.overrides))!.key
    draft.messages[listed.scope] = { [listed.locale]: { [key]: { kind: 'reset' }, [other]: { kind: 'reset' } } }
    expect(buildChangeSet(p, draft).messages).toEqual([{ scope: listed.scope, locale: listed.locale, expectedVersion: listed.version, set: {}, reset: [key] }])
    draft.messages[listed.scope] = { [listed.locale]: { [other]: { kind: 'set', text: ' ' } } }
    expect(validateDraft(p, draft)).toHaveLength(1)
  })
})

describe('typed ladder steps', () => {
  it('parses the three forms and writes the template\'s type as just the duration', () => {
    expect(parseStep('7d')).toEqual({ type: null, duration: '7d' })
    expect(parseStep('kick')).toEqual({ type: 'kick', duration: '' })
    expect(parseStep('mute 1h')).toEqual({ type: 'mute', duration: '1h' })
    expect(parseStep('BAN permanent')).toEqual({ type: 'ban', duration: 'permanent' })
    expect(formatStep({ type: null, duration: '7d' }, 'ban')).toBe('7d')
    expect(formatStep({ type: 'kick', duration: '' }, 'mute')).toBe('kick')
    expect(formatStep({ type: 'ban', duration: 'permanent' }, 'mute')).toBe('ban permanent')
    expect(formatStep({ type: null, duration: '' }, 'kick')).toBe('permanent')
  })

  it('round-trips every fixture template and compares steps by meaning', () => {
    for (const item of payload().templates!.items) {
      expect(formToDefinition(definitionToForm(item.name, item.definition))).toEqual(item.definition)
    }
    const a = { type: 'mute' as const, reason: 'x', ladder: ['mute 1h', 'kick'] }
    expect(sameDefinition(a, { ...a, ladder: ['1h', 'kick'] })).toBe(true)
    expect(sameDefinition(a, { ...a, ladder: ['1h', 'ban 1d'] })).toBe(false)
    expect(sameDefinition({ type: 'kick', reason: 'x', ladder: ['permanent'] }, { type: 'kick', reason: 'x', ladder: ['kick'] })).toBe(true)
  })

  it('keeps step actions and messages with their step when reordering', () => {
    const p = payload()
    const draft = initialDraft(p)
    const form = draft.templates.find(item => item.steps.length > 1)!
    form.steps[0]!.message = '<red>first'
    form.steps[0]!.actions = ['say first']
    form.steps.reverse()
    const definition = formToDefinition(form)
    const last = String(form.steps.length)
    expect(definition['step-messages']?.[last]).toBe('<red>first')
    expect(definition['step-actions']?.[last]).toEqual(['say first'])
  })

  it('validates kicks without duration, durations, shadow without a mute step', () => {
    const form = emptyTemplate('mixed', 'ban')
    form.reason = 'x'
    form.steps = [
      { ...emptyStep('kick', 'ban'), duration: '1d' },
      { ...emptyStep('mute', 'ban'), duration: 'soon' },
      emptyStep(null, 'ban')
    ]
    form.shadow = true
    const p = payload()
    const draft = initialDraft(p)
    draft.templates.push({ ...form, origin: null })
    const messages = validateDraft(p, draft).map(issue => `${issue.path}: ${issue.message}`)
    expect(messages).toEqual(expect.arrayContaining([
      expect.stringContaining('ladder[0]: a kick has no duration'),
      expect.stringContaining('ladder[1]')
    ]))
    expect(messages.some(message => message.includes('shadow'))).toBe(false)
    form.steps[1]!.type = 'ban'
    expect(validateDraft(p, draft).some(issue => issue.path.endsWith('shadow'))).toBe(true)
  })

  it('labels steps and summarises a mixed ladder', () => {
    const form = emptyTemplate('toxicity', 'mute')
    form.steps = [emptyStep('kick', 'mute'), { ...emptyStep(null, 'mute'), duration: '1h' }, { ...emptyStep('ban', 'mute'), duration: 'permanent' }]
    expect(form.steps.map(step => stepLabel(step, 'mute'))).toEqual(['Kick', 'Mute 1 hour', 'Ban permanent'])
    expect(summarizeTemplate(form, [form])).toBe('1st: kick, 2nd: mute 1 hour, then ban permanent')
  })
})

describe('template changes', () => {
  it('turns a rename into delete + create and lists it as a rename', () => {
    const p = payload()
    const item = p.templates!.items[0]!
    const draft = initialDraft(p)
    draft.templates.find(form => form.name === item.name)!.name = `${item.name}-renamed`
    expect(buildChangeSet(p, draft).templates).toEqual([
      { op: 'create', name: `${item.name}-renamed`, definition: formToDefinition(definitionToForm(item.name, item.definition)) },
      { op: 'delete', name: item.name, expectedVersion: item.version }
    ])
    const changes = listChanges(p, draft)
    expect(changes[0]).toMatchObject({ kind: 'rename', title: `${item.name} → ${item.name}-renamed` })
    revertChange(p, draft, changes[0]!.ref)
    expect(buildChangeSet(p, draft).templates).toBeUndefined()
  })

  it('deletes and reverts a delete; explicit defaults change nothing', () => {
    const p = payload()
    const item = p.templates!.items[0]!
    const draft = initialDraft(p)
    draft.templates = draft.templates.filter(form => form.name !== item.name)
    expect(buildChangeSet(p, draft).templates).toEqual([{ op: 'delete', name: item.name, expectedVersion: item.version }])
    revertChange(p, draft, { section: 'templates', name: item.name })
    for (const form of draft.templates) {
      form.ladderExpiry = form.ladderExpiry === '' ? 'none' : form.ladderExpiry
    }
    expect(buildChangeSet(p, draft).templates).toBeUndefined()
  })

  it('keeps unknown definition keys', () => {
    const p = payload()
    p.templates!.items[0]!.definition['future-key'] = 'kept'
    const draft = initialDraft(p)
    draft.templates[0]!.reason = 'Changed'
    const [change] = buildChangeSet(p, draft).templates!
    expect(change).toMatchObject({ op: 'update', definition: { 'future-key': 'kept', 'reason': 'Changed' } })
  })

  it('summarizes ladders and group mates', () => {
    const hacking = emptyTemplate('hacking')
    hacking.steps = ['7d', '30d', 'permanent'].map(duration => ({ ...emptyStep(null, 'ban'), duration }))
    hacking.ladderExpiry = '1y'
    hacking.groups = [{ name: 'cheating', weight: 2 }]
    const xray = emptyTemplate('xray')
    xray.groups = [{ name: 'cheating', weight: 1 }]
    expect(summarizeTemplate(hacking, [hacking, xray])).toBe(
      '1st: 7 days, 2nd: 30 days, then permanent; earlier punishments older than 1 year don\'t count; counts with: xray (weight 1)')
    const kick = emptyTemplate('name', 'kick')
    expect(summarizeTemplate(kick, [kick])).toBe('kick every time')
  })
})

describe('templates.yml', () => {
  it('exports and imports the same definitions, messages included', () => {
    const p = payload()
    const draft = initialDraft(p)
    const imported = importTemplatesYaml(exportTemplatesYaml(draft.templates))
    expect(imported.errors).toEqual([])
    expect(imported.templates.map(formToDefinition)).toEqual(
      [...draft.templates].sort((a, b) => a.name.localeCompare(b.name)).map(formToDefinition))
  })

  it('reads old single-type files and the plugin export format, and reports errors', () => {
    const imported = importTemplatesYaml(`templates:
  hacking:
    type: ban
    reason: Cheating
    ladder: [7d, 30d, permanent]
    ladder-expiry: none
    address-shared: false
    actions: []
    groups:
      cheating: 2
  name:
    type: kick
    reason: Change your name
    ladder: [permanent]
  toxic:
    type: mute
    reason: Toxic
    ladder: [kick, mute 1h, ban 7d]
    message: "<red>Be nice"
    step-messages:
      3: "<red>Banned"
  broken:
    type: slap
    reason: x
    ladder: [1d]
`)
    expect(imported.templates.map(form => formToDefinition(form))).toEqual([
      { type: 'ban', reason: 'Cheating', ladder: ['7d', '30d', 'permanent'], groups: { cheating: 2 } },
      { type: 'kick', reason: 'Change your name', ladder: ['permanent'] },
      { 'type': 'mute', 'reason': 'Toxic', 'ladder': ['kick', 'mute 1h', 'ban 7d'], 'message': '<red>Be nice', 'step-messages': { 3: '<red>Banned' } }
    ])
    expect(imported.errors[0]).toContain('templates.broken.type')
    expect(importTemplatesYaml('nope: [').errors[0]).toContain('Not valid YAML')
  })
})
