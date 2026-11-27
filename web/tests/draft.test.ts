// Change-set building against the payload fixture. Keys, names and versions are read from the
// fixture, never written here, so a fixture change needs no test change.

import { describe, expect, it } from 'vitest'
import {
  buildChangeSet, displayValue, initialDraft, listChanges, revertChange, validateDraft
} from '../shared/editor/draft'
import {
  changeKind, definitionToForm, emptyLadder, emptyPreset, emptyStep, emptyTemplate, formatStep, formToDefinition,
  parseStepText, readTemplate, sameDefinition, stepLabel, summarizeTemplate, usageHint, validateTemplate
} from '../shared/editor/templates'
import type { TemplateForm } from '../shared/editor/templates'
import { exportTemplatesYaml, importTemplatesYaml } from '../shared/editor/templatesYaml'
import type { Payload, SettingField, TemplateDefinition } from '../shared/protocol/schema'
import { fixtureJson, fixturePayload } from './fixtures'

const payload = (): Payload => fixturePayload()

const issuesOf = (form: TemplateForm) => validateTemplate(form, new Set()).map(issue => `${issue.path}: ${issue.message}`)

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

describe('template steps', () => {
  it('reads step texts as the plugin does, errors included', () => {
    expect(parseStepText('none')).toEqual({ type: null, duration: { mode: 'open', length: '' } })
    expect(parseStepText('kick')).toEqual({ type: 'kick', duration: { mode: 'open', length: '' } })
    expect(parseStepText('warn')).toEqual({ type: 'warn', duration: { mode: 'open', length: '' } })
    expect(parseStepText('warn 30d')).toEqual({ type: 'warn', duration: { mode: 'fixed', length: '30d' } })
    expect(parseStepText('MUTE 1h')).toEqual({ type: 'mute', duration: { mode: 'fixed', length: '1h' } })
    expect(parseStepText('ban perm')).toEqual({ type: 'ban', duration: { mode: 'permanent', length: '' } })
    const errors = ['7d', 'permanent', 'slap', 'ban', 'mute', 'kick 1d', 'none 1d', 'ban soon', 'ban 1d extra', '']
      .map(text => parseStepText(text).error)
    expect(errors).toEqual([
      '"7d" names no type: a step is none, kick, warn, or a type and a duration (ban 7d, mute 1h, ban permanent)',
      '"permanent" names no type: a step is none, kick, warn, or a type and a duration (ban 7d, mute 1h, ban permanent)',
      '"slap" is not a type (none, ban, mute, warn or kick)',
      '"ban" needs a duration or \'permanent\' after the type',
      '"mute" needs a duration or \'permanent\' after the type',
      'a kick has no duration',
      'a step without a punishment has no duration',
      '"soon" is not a duration or \'permanent\'',
      '"ban 1d extra" is not a step (none, kick, warn, or a type and a duration: ban 7d, mute 1h, ban permanent)',
      '"" is not a step (none, kick, warn, or a type and a duration: ban 7d, mute 1h, ban permanent)'
    ])
  })

  it('writes steps and durations in the plugin\'s canonical form', () => {
    expect(formatStep({ type: null, duration: { mode: 'open', length: '' } })).toBe('none')
    expect(formatStep({ type: 'kick', duration: { mode: 'open', length: '' } })).toBe('kick')
    expect(formatStep({ type: 'warn', duration: { mode: 'open', length: '30d' } })).toBe('warn')
    expect(formatStep({ type: 'mute', duration: { mode: 'fixed', length: '90m' } })).toBe('mute 1h30m')
    expect(formatStep({ type: 'ban', duration: { mode: 'fixed', length: '1mo' } })).toBe('ban 30d')
    expect(formatStep({ type: 'ban', duration: { mode: 'permanent', length: '7d' } })).toBe('ban permanent')
  })

  it('round-trips every fixture template byte for byte', () => {
    // The raw fixture: parsing with the schema may order keys differently.
    const raw = fixtureJson('payload-v1.json') as { templates: { items: Array<{ name: string, definition: TemplateDefinition }> } }
    for (const item of raw.templates.items) {
      expect(JSON.stringify(formToDefinition(definitionToForm(item.name, item.definition)))).toBe(JSON.stringify(item.definition))
    }
  })

  it('round-trips a preset of each type and their duration states', () => {
    const presets: TemplateDefinition[] = [
      { 'type': 'ban', 'ip': true, 'scope': 'category:lifesteal', 'reason': 'Out of hearts', 'duration': '3d', 'permission-required': true, 'groups': { a: 1, b: 2 } },
      { type: 'ban', reason: 'Command decides' },
      { type: 'mute', shadow: true, reason: 'Spam', duration: 'permanent', actions: ['say {victim} muted'], message: '<red>Muted' },
      { type: 'warn', reason: 'Language', duration: '30d' },
      { type: 'warn', reason: 'Language' },
      { type: 'kick', reason: 'Change your name', message: '<red>Change it' }
    ]
    for (const definition of presets) {
      const form = definitionToForm('x', definition)
      expect(form.kind).toBe('preset')
      expect(JSON.stringify(formToDefinition(form))).toBe(JSON.stringify(definition))
    }
    const open = definitionToForm('x', presets[1]!)
    expect(open.kind === 'preset' && open.duration.mode).toBe('open')
  })

  it('round-trips a ladder with a none step and a step map', () => {
    const definition: TemplateDefinition = {
      'type': 'ladder',
      'shadow': true,
      'reason': 'Toxic',
      'steps': ['warn', 'warn 30d', 'kick', 'mute 1h',
        { step: 'none', actions: ['say {victim} is on a final warning'] },
        { step: 'ban 7d', reason: 'Toxic (again)', message: '<red>Last chance', actions: ['say bye'] },
        'ban permanent'],
      'expiry': '30d',
      'address-shared': true,
      'permission-required': true,
      'actions': ['say {victim} was punished'],
      'groups': { chat: 1 },
      'message': '<red>Be nice'
    }
    const form = definitionToForm('toxicity', definition)
    expect(form.kind).toBe('ladder')
    if (form.kind !== 'ladder') {
      return
    }
    expect(form.steps.map(step => step.type)).toEqual(['warn', 'warn', 'kick', 'mute', null, 'ban', 'ban'])
    expect(form.steps[5]).toMatchObject({ reason: 'Toxic (again)', message: '<red>Last chance', actions: ['say bye'] })
    expect(JSON.stringify(formToDefinition(form))).toBe(JSON.stringify(definition))
    expect(form.steps.map(stepLabel)).toEqual(['Warn', 'Warn 30 days', 'Kick', 'Mute 1 hour', 'No punishment', 'Ban 7 days', 'Ban permanent'])
  })

  it('compares definitions by meaning, as the plugin writes them back', () => {
    const a: TemplateDefinition = { type: 'ladder', reason: 'x', steps: ['mute 90m', 'BAN forever'], expiry: '1mo', actions: ['/say hi '] }
    const b: TemplateDefinition = { type: 'ladder', reason: 'x ', steps: ['mute 1h30m', 'ban permanent'], expiry: '30d', actions: ['say hi'] }
    expect(sameDefinition(a, b)).toBe(true)
    expect(sameDefinition(a, { ...b, steps: ['mute 1h30m', 'ban 7d'] })).toBe(false)
    expect(sameDefinition({ type: 'ban', reason: 'x', groups: { b: 1, a: 2 } }, { type: 'ban', reason: 'x', groups: { a: 2, b: 1 } })).toBe(true)
    expect(sameDefinition({ type: 'ban', reason: 'x' }, { type: 'ban', reason: 'x', duration: 'permanent' })).toBe(false)
    expect(sameDefinition({ type: 'ban', reason: 'x' }, { type: 'mute', reason: 'x' })).toBe(false)
  })

  it('keeps a step\'s reason, message and actions with it when reordering', () => {
    const p = payload()
    const draft = initialDraft(p)
    const form = draft.templates.find(item => item.kind === 'ladder' && item.steps.length > 2)
    if (!form || form.kind !== 'ladder') {
      throw new Error('the payload fixture has no ladder with three steps')
    }
    form.steps[0]!.message = '<red>first'
    form.steps[0]!.reason = 'First time'
    form.steps[0]!.actions = ['say first']
    form.steps.reverse()
    const steps = formToDefinition(form).steps!
    expect(steps.at(-1)).toEqual({ step: formatStep(form.steps.at(-1)!), reason: 'First time', message: '<red>first', actions: ['say first'] })
    const [change] = buildChangeSet(p, draft).templates!
    expect(change).toMatchObject({ op: 'update', name: form.name })
  })

  it('validates both shapes with the plugin\'s rules and paths', () => {
    const ladder = emptyLadder('mixed')
    ladder.reason = 'x'
    ladder.steps = [
      { ...emptyStep('kick'), duration: { mode: 'fixed', length: '1d' } },
      { ...emptyStep('mute'), duration: { mode: 'fixed', length: 'soon' } },
      { ...emptyStep('ban'), duration: { mode: 'open', length: '' } },
      { ...emptyStep(null), message: '<red>no' },
      { ...emptyStep('warn'), reason: 'r'.repeat(1025) },
      { ...emptyStep('ban'), duration: { mode: 'fixed', length: '500ms' } }
    ]
    ladder.shadow = true
    ladder.expiry = 'later'
    expect(issuesOf(ladder)).toEqual(expect.arrayContaining([
      'steps[0].step: a kick has no duration',
      expect.stringMatching(/^steps\[1\]\.step: Not a duration/),
      'steps[2].step: a ban or mute step needs a duration or permanent',
      'steps[3].message: a step without a punishment has no message',
      'steps[3].actions: a step without a punishment needs an action',
      'steps[4].reason: must be at most 1024 characters',
      'steps[5].step: Must be at least one second.',
      expect.stringMatching(/^expiry: /)
    ]))
    expect(issuesOf(ladder).some(issue => issue.startsWith('shadow'))).toBe(false)
    ladder.steps[1]!.type = 'ban'
    expect(issuesOf(ladder)).toContain('shadow: needs a mute step')
    ladder.steps = []
    expect(issuesOf(ladder)).toContain('steps: must have 1-64 steps')

    const kick = emptyPreset('k', 'kick')
    kick.reason = 'x'
    kick.duration = { mode: 'permanent', length: '' }
    kick.shadow = true
    expect(issuesOf(kick)).toEqual(['duration: a kick has no duration', 'shadow: only a mute is a shadow mute'])
    const misplaced = definitionToForm('m', { type: 'ban', reason: 'x', steps: ['ban 1d'], expiry: '1d' })
    expect(issuesOf(misplaced)).toEqual(['steps: only a ladder has steps (type: ladder)', 'expiry: only a ladder has an expiry'])
    expect(formToDefinition(misplaced)).toEqual({ type: 'ban', reason: 'x', steps: ['ban 1d'], expiry: '1d' })
    expect(issuesOf(definitionToForm('d', { type: 'ladder', reason: 'x', steps: ['kick'], duration: '1d' })))
      .toEqual(['duration: a ladder\'s steps have the durations'])
  })

  it('reads, writes and checks display like the plugin', () => {
    const mute = definitionToForm('spam', { type: 'mute', reason: 'Spam', message: '<red>Quiet', display: 'Dialog' })
    expect(mute.display).toBe('dialog')
    expect(issuesOf(mute)).toEqual([])
    // Written after the message, where TemplateFiles.tree puts it; chat is left out.
    expect(Object.keys(formToDefinition(mute))).toEqual(['type', 'reason', 'message', 'display'])
    expect(formToDefinition(definitionToForm('x', { type: 'mute', reason: 'x', display: 'chat' }))).toEqual({ type: 'mute', reason: 'x' })
    expect(readTemplate('x', { type: 'mute', reason: 'x', display: 'popup' }).errors)
      .toEqual([{ path: 'display', message: '"popup" is not a display (chat or dialog)' }])

    expect(issuesOf(definitionToForm('w', { type: 'warn', reason: 'x', display: 'dialog' }))).toEqual([])
    expect(issuesOf(definitionToForm('b', { type: 'ban', reason: 'x', display: 'dialog' })))
      .toEqual(['display: only a mute or a warning is shown as a dialog'])
    expect(issuesOf(definitionToForm('k', { type: 'kick', reason: 'x', display: 'dialog' })))
      .toEqual(['display: only a mute or a warning is shown as a dialog'])
    expect(issuesOf(definitionToForm('h', { type: 'mute', shadow: true, reason: 'x', display: 'dialog' })))
      .toEqual(['display: a shadow mute shows the player nothing'])
    const ladder = definitionToForm('l', { type: 'ladder', reason: 'x', steps: ['kick', 'warn', 'ban 1d'], display: 'dialog' })
    expect(issuesOf(ladder)).toEqual([])
    ladder.steps.splice(1, 1)
    expect(issuesOf(ladder)).toEqual(['display: needs a mute or warn step'])

    // A kind that can't show one falls back to chat.
    expect(changeKind(mute, 'ban').display).toBe('chat')
    expect(changeKind(mute, 'warn').display).toBe('dialog')
    expect(changeKind(mute, 'ladder').display).toBe('dialog')
  })

  it('reads and writes silent like the plugin, left out when false', () => {
    const ban = definitionToForm('raid', { type: 'ban', reason: 'Raid', display: 'chat', silent: true })
    expect(ban.silent).toBe(true)
    expect(Object.keys(formToDefinition(ban))).toEqual(['type', 'reason', 'silent'])
    expect(formToDefinition(definitionToForm('x', { type: 'kick', reason: 'x', silent: false }))).toEqual({ type: 'kick', reason: 'x' })
    expect(readTemplate('x', { type: 'mute', reason: 'x', silent: 'yes' } as never).errors)
      .toEqual([{ path: 'silent', message: 'must be true or false' }])
    const ladder = definitionToForm('l', { type: 'ladder', reason: 'x', steps: ['warn', 'ban 1d'], silent: true })
    expect(formToDefinition(ladder).silent).toBe(true)
    expect(changeKind(ban, 'ladder').silent).toBe(true)
    expect(issuesOf(ladder)).toEqual([])
  })

  it('keeps template durations to whole seconds, as the plugin does', () => {
    const preset = definitionToForm('m', { type: 'mute', reason: 'x', duration: '1500ms' })
    expect(issuesOf(preset)).toEqual(['duration: Must be whole seconds (a template is written as 90s, not 1500ms).'])
    expect(issuesOf(definitionToForm('m', { type: 'mute', reason: 'x', duration: '500ms' }))).toEqual(['duration: Must be at least one second.'])
    expect(issuesOf(definitionToForm('m', { type: 'mute', reason: 'x', duration: '90s' }))).toEqual([])
    expect(issuesOf(definitionToForm('l', { type: 'ladder', reason: 'x', steps: ['mute 90500ms'] })))
      .toEqual(['steps[0].step: Must be whole seconds (a template is written as 90s, not 1500ms).'])
    expect(issuesOf(definitionToForm('l', { type: 'ladder', reason: 'x', steps: ['kick'], expiry: '1500ms' })))
      .toEqual(['expiry: Must be whole seconds (a template is written as 90s, not 1500ms).'])
  })

  it('changes the kind, keeping what the new kind has', () => {
    const mute = definitionToForm('spam', { type: 'mute', shadow: true, reason: 'Spam', duration: '1h', groups: { chat: 1 } })
    const ban = changeKind(mute, 'ban')
    expect(formToDefinition(ban)).toEqual({ type: 'ban', reason: 'Spam', duration: '1h', groups: { chat: 1 } })
    expect(formToDefinition(changeKind(ban, 'kick'))).toEqual({ type: 'kick', reason: 'Spam', groups: { chat: 1 } })
    const ladder = changeKind(mute, 'ladder')
    expect(formToDefinition(ladder)).toEqual({ type: 'ladder', shadow: true, reason: 'Spam', steps: ['mute 1h'], groups: { chat: 1 } })
    expect(formToDefinition(changeKind(emptyPreset('b', 'ban'), 'ladder')).steps).toEqual(['ban permanent'])
    const back = changeKind(definitionToForm('t', { type: 'ladder', reason: 'x', steps: ['warn', 'mute 1d', 'ban 7d'] }), 'mute')
    expect(formToDefinition(back)).toEqual({ type: 'mute', reason: 'x', duration: '1d' })
  })

  it('summarises presets and ladders, and says how to use them', () => {
    const toxicity = definitionToForm('toxicity', { type: 'ladder', reason: 'x', steps: ['kick', 'mute 1h', { step: 'none', actions: ['say hi'] }, 'ban permanent'], expiry: '30d', groups: { chat: 1 } })
    const spam = definitionToForm('spam', { type: 'mute', shadow: true, reason: 'x', duration: '1h', groups: { chat: 2 } })
    expect(summarizeTemplate(toxicity, [toxicity, spam])).toBe(
      '1st: kick, 2nd: mute 1 hour, 3rd: no punishment, then ban permanent; uses older than 30 days don\'t count; counts with: spam (weight 2)')
    expect(summarizeTemplate(spam, [toxicity, spam])).toBe('Shadow mute for 1 hour; counts with: toxicity (weight 1)')
    expect(summarizeTemplate(emptyPreset('a', 'ban'), [])).toBe('Ban; the command decides how long')
    expect(summarizeTemplate(definitionToForm('a', { type: 'ban', ip: true, reason: 'x', duration: 'permanent' }), [])).toBe('Permanent ban, on the address')
    expect(summarizeTemplate(emptyPreset('k', 'kick'), [])).toBe('Kick')
    expect(summarizeTemplate(definitionToForm('w', { type: 'warn', reason: 'x', duration: '30d' }), [])).toBe('Warning for 30 days')
    expect(usageHint(spam)).toBe('/mute <player> -t spam')
    expect(usageHint(toxicity)).toBe('/punish <player> toxicity')
  })
})

describe('template changes', () => {
  it('turns a rename into delete + create and lists it as a rename', () => {
    const p = payload()
    const item = p.templates!.items[0]!
    const draft = initialDraft(p)
    draft.templates.find(form => form.name === item.name)!.name = `${item.name}-renamed`
    expect(buildChangeSet(p, draft).templates).toEqual([
      { op: 'create', scope: 'global', name: `${item.name}-renamed`, definition: item.definition },
      { op: 'delete', scope: 'global', name: item.name, expectedVersion: item.version }
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
    expect(buildChangeSet(p, draft).templates).toEqual([{ op: 'delete', scope: 'global', name: item.name, expectedVersion: item.version }])
    revertChange(p, draft, { section: 'templates', scope: 'global', name: item.name })
    for (const form of draft.templates) {
      if (form.kind === 'ladder') {
        form.expiry = form.expiry === '' ? 'none' : form.expiry
      } else if (form.duration.mode !== 'fixed') {
        form.duration.length = '12h'
      }
      form.actions = form.actions.map(action => `/${action}`)
    }
    expect(buildChangeSet(p, draft).templates).toBeUndefined()
  })

  it('names the changed keys as the plugin\'s result does', () => {
    const p = payload()
    const draft = initialDraft(p)
    const form = draft.templates.find(item => item.kind === 'ladder')!
    form.reason = 'Changed'
    if (form.kind === 'ladder') {
      form.steps.pop()
      form.expiry = '2y'
    }
    expect(listChanges(p, draft).find(item => item.kind === 'update')?.note).toBe('changed: reason, steps, expiry')
  })

  it('keeps unknown definition keys', () => {
    const p = payload()
    p.templates!.items[0]!.definition['future-key'] = 'kept'
    const draft = initialDraft(p)
    draft.templates[0]!.reason = 'Changed'
    const [change] = buildChangeSet(p, draft).templates!
    expect(change).toMatchObject({ op: 'update', definition: { 'future-key': 'kept', 'reason': 'Changed' } })
  })

  it('creates presets and ladders with their own shape', () => {
    const p = payload()
    const draft = initialDraft(p)
    const preset = { ...emptyTemplate('new-mute', 'mute'), reason: 'Spam', origin: null, variant: 'global' }
    const ladder = { ...emptyTemplate('new-ladder', 'ladder'), reason: 'Escalation', origin: null, variant: 'global' }
    draft.templates.push(preset, ladder)
    expect(validateDraft(p, draft)).toEqual([])
    expect(buildChangeSet(p, draft).templates).toEqual([
      { op: 'create', scope: 'global', name: 'new-ladder', definition: { type: 'ladder', reason: 'Escalation', steps: ['warn', 'mute 1d', 'ban 1d'] } },
      { op: 'create', scope: 'global', name: 'new-mute', definition: { type: 'mute', reason: 'Spam' } }
    ])
    const taken = { ...emptyTemplate(p.templates!.items[0]!.name, 'ladder'), reason: 'x', origin: null, variant: 'global' }
    draft.templates.push(taken)
    expect(validateDraft(p, draft).some(issue => issue.path.endsWith('.name') && issue.message.includes('unique'))).toBe(true)
  })
})

describe('templates.yml', () => {
  it('exports and imports the same definitions, messages included', () => {
    const p = payload()
    const draft = initialDraft(p)
    const imported = importTemplatesYaml(exportTemplatesYaml(draft.templates))
    expect(imported.errors).toEqual([])
    const keyed = (forms: Array<TemplateForm & { variant: string }>) => Object.fromEntries(
      forms.map(form => [`${form.variant} ${form.name}`, formToDefinition(form)]))
    expect(keyed(imported.templates)).toEqual(keyed(draft.templates))
    expect(imported.templates.some(form => form.variant !== 'global')).toBe(true)
  })

  it('reads the plugin\'s file format and refuses the old keys and bad templates', () => {
    const imported = importTemplatesYaml(`templates:
  hacking:
    type: ban
    reason: Unfair advantage (hacked client)
    duration: 30d
    groups:
      cheating: 2
  advertising:
    type: Mute
    reason: Advertising
  misconduct:
    type: ladder
    reason: Repeated misconduct
    steps:
      - warn
      - kick
      - mute 1d
      - {step: none, actions: ["say {victim} is on probation"]}
      - {step: ban permanent, message: "<red>Permanently banned.</red>"}
    expiry: 90d
  old:
    type: ban
    reason: Cheating
    ladder: [7d, 30d, permanent]
    ladder-expiry: none
  broken:
    type: slap
    reason: x
  bad-step:
    type: ladder
    reason: x
    steps: [7d, {step: none}]
  kick-with-time:
    type: kick
    reason: x
    duration: 1d
`)
    expect(imported.templates.map(form => [form.name, formToDefinition(form)])).toEqual([
      ['hacking', { type: 'ban', reason: 'Unfair advantage (hacked client)', duration: '30d', groups: { cheating: 2 } }],
      ['advertising', { type: 'mute', reason: 'Advertising' }],
      ['misconduct', { type: 'ladder', reason: 'Repeated misconduct', steps: ['warn', 'kick', 'mute 1d',
        { step: 'none', actions: ['say {victim} is on probation'] },
        { step: 'ban permanent', message: '<red>Permanently banned.</red>' }], expiry: '90d' }]
    ])
    expect(imported.errors).toEqual([
      'templates.old.ladder: is no longer read: use \'type: ladder\' and \'steps\' (each step names its type: warn, kick, mute 1h, ban 7d, none)',
      'templates.old.ladder-expiry: is no longer read: use \'expiry\'',
      'templates.broken.type: "slap" is not a type (ban, mute, warn, kick or ladder)',
      'templates.bad-step.steps[0]: "7d" names no type: a step is none, kick, warn, or a type and a duration (ban 7d, mute 1h, ban permanent)',
      'templates.bad-step.steps[1].actions: a step without a punishment needs an action',
      'templates.kick-with-time.duration: a kick has no duration'
    ])
    expect(importTemplatesYaml('nope: [').errors[0]).toContain('Not valid YAML')
  })
})
