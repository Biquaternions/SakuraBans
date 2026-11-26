// Re-basing on a fresh snapshot (protocol section 10), per scope. The "server" snapshots are the
// payload fixture with changes made in the test; keys and names are read from the fixture.

import { describe, expect, it } from 'vitest'
import { buildChangeSet, initialDraft, listChanges, refId } from '../shared/editor/draft'
import type { EditorDraft } from '../shared/editor/draft'
import { simulateSnapshot } from '../shared/editor/outcome'
import { rebaseDraft } from '../shared/editor/rebase'
import type { RebaseNote } from '../shared/editor/rebase'
import { definitionToForm, emptyTemplate } from '../shared/editor/templates'
import type { JsonValue, Payload, TemplateDefinition } from '../shared/protocol/schema'
import { draftFromChanges, fixtureChanges, fixturePayload } from './fixtures'

function server(change: (payload: Payload) => void): Payload {
  const payload = structuredClone(fixturePayload())
  change(payload)
  for (const scope of payload.settings?.scopes ?? []) {
    scope.version += 1
  }
  for (const scope of payload.messages?.scopes ?? []) {
    scope.version += 1
  }
  return payload
}

function setOverride(p: Payload, scope: string, key: string, value: JsonValue | undefined) {
  let entry = p.settings!.scopes.find(item => item.scope === scope)
  if (!entry) {
    entry = { scope, version: 0, overrides: {} }
    p.settings!.scopes.push(entry)
  }
  if (value === undefined) {
    Reflect.deleteProperty(entry.overrides, key)
  } else {
    entry.overrides[key] = value
  }
}

function noteFor(notes: RebaseNote[], target: string, scope?: string): string | undefined {
  return notes.find(note => note.target === target && (scope === undefined || note.scope === scope))?.kind
}

const before = fixturePayload()
const key = before.settings!.fields.find(field => field.level === 'SCOPED' && !field.secret && field.type === 'STRING')!.key
const SCOPE = 'server:rebase-test'

describe('settings per scope', () => {
  it('drops a set the snapshot now has in that scope; keeps one it has only elsewhere', () => {
    const draft = initialDraft(before)
    draft.settings[SCOPE] = { [key]: { kind: 'set', value: 'mine' } }
    draft.settings.global = { [key]: { kind: 'set', value: 'mine' } }
    const after = server(p => setOverride(p, SCOPE, key, 'mine'))
    const { draft: next, notes } = rebaseDraft(before, after, draft, 2)
    expect(noteFor(notes, key, SCOPE)).toBe('applied')
    expect(noteFor(notes, key, 'global')).toBe('kept')
    expect(next.settings).toEqual({ global: { [key]: { kind: 'set', value: 'mine' } } })
    expect(buildChangeSet(after, next).settings).toEqual([
      { scope: 'global', expectedVersion: after.settings!.scopes[0]!.version, set: { [key]: 'mine' }, reset: [] }
    ])
    expect(next.revision).toBe(2)
  })

  it('keeps a set whose override in that scope moved meanwhile, as a conflict', () => {
    const draft = initialDraft(before)
    draft.settings[SCOPE] = { [key]: { kind: 'set', value: 'mine' } }
    const after = server(p => setOverride(p, SCOPE, key, 'theirs'))
    const { notes } = rebaseDraft(before, after, draft, 2)
    expect(noteFor(notes, key, SCOPE)).toBe('conflict')
    const quiet = rebaseDraft(before, server(() => undefined), draft, 2, { conflicts: new Set([refId({ section: 'settings', scope: SCOPE, key })]) })
    expect(noteFor(quiet.notes, key, SCOPE)).toBe('conflict')
  })

  it('drops a reset once the scope\'s override is gone, keeps it while it exists', () => {
    const scope = before.settings!.scopes.find(item => item.scope !== 'global' && Object.keys(item.overrides).length > 0)!
    const overridden = Object.keys(scope.overrides)[0]!
    const draft = initialDraft(before)
    draft.settings[scope.scope] = { [overridden]: { kind: 'reset' } }
    expect(rebaseDraft(before, server(p => setOverride(p, scope.scope, overridden, undefined)), draft, 2).draft.settings).toEqual({})
    const kept = rebaseDraft(before, server(() => undefined), draft, 2)
    expect(kept.draft.settings).toEqual({ [scope.scope]: { [overridden]: { kind: 'reset' } } })
    expect(noteFor(kept.notes, overridden)).toBe('kept')
  })

  it('decides secrets by the result, since their values can\'t be compared', () => {
    const secret = before.settings!.fields.find(field => field.secret)
    if (!secret) {
      return
    }
    const draft = initialDraft(before)
    draft.settings[SCOPE] = { [secret.key]: { kind: 'set', value: 'https://new.example/hook' } }
    const after = server(p => setOverride(p, SCOPE, secret.key, true))
    expect(rebaseDraft(before, after, draft, 2).draft.settings[SCOPE]).toBeDefined()
    const applied = new Set([refId({ section: 'settings', scope: SCOPE, key: secret.key })])
    expect(rebaseDraft(before, after, draft, 2, { applied }).draft.settings).toEqual({})
  })

  it('drops published keys once global holds the server\'s file value', () => {
    const publisher = before.settings!.servers[0]!
    const [pubKey, value] = Object.entries(publisher.fileValues).find(([k]) => !(k in before.settings!.scopes[0]!.overrides))!
    const draft = initialDraft(before)
    draft.publish = { server: publisher.name, keys: [pubKey] }
    expect(rebaseDraft(before, server(p => setOverride(p, 'global', pubKey, value)), draft, 2).draft.publish).toBeNull()
    expect(rebaseDraft(before, server(() => undefined), draft, 2).draft.publish).toEqual(draft.publish)
  })
})

describe('messages per scope', () => {
  const key = before.messages!.fields[0]!.key
  const locale = before.messages!.locales[0]!.locale

  function setText(p: Payload, scope: string, text: string | undefined) {
    let entry = p.messages!.scopes.find(item => item.scope === scope && item.locale === locale)
    if (!entry) {
      entry = { scope, locale, version: 0, overrides: {} }
      p.messages!.scopes.push(entry)
    }
    if (text === undefined) {
      Reflect.deleteProperty(entry.overrides, key)
    } else {
      entry.overrides[key] = text
    }
  }

  it('drops applied sets and resets, keeps the rest with the new version', () => {
    const draft = initialDraft(before)
    draft.messages[SCOPE] = { [locale]: { [key]: { kind: 'set', text: '<red>applied' } } }
    draft.messages.global = { [locale]: { [key]: { kind: 'set', text: '<red>pending' } } }
    const after = server(p => setText(p, SCOPE, '<red>applied'))
    const { draft: next, notes } = rebaseDraft(before, after, draft, 2)
    expect(noteFor(notes, `${locale}:${key}`, SCOPE)).toBe('applied')
    const global = after.messages!.scopes.find(item => item.scope === 'global' && item.locale === locale)
    expect(buildChangeSet(after, next).messages).toEqual([
      { scope: 'global', locale, expectedVersion: global?.version ?? 0, set: { [key]: '<red>pending' }, reset: [] }
    ])
  })

  it('folds a locale created here into the one that now exists on the server', () => {
    const draft = initialDraft(before)
    draft.addedLocales = ['xx_XX']
    draft.messages.global = { xx_XX: { [key]: { kind: 'set', text: '<red>translated' } } }
    const after = server((p) => {
      p.messages!.scopes.push({ scope: 'global', locale: 'xx_XX', version: 4, overrides: { other: 'x' } })
    })
    const { draft: next, notes } = rebaseDraft(before, after, draft, 2)
    expect(next.addedLocales).toEqual([])
    expect(notes).toContainEqual({ kind: 'locale-exists', section: 'messages', scope: '', target: 'xx_XX' })
    expect(buildChangeSet(after, next).messages).toEqual([
      { scope: 'global', locale: 'xx_XX', expectedVersion: 5, set: { [key]: '<red>translated' }, reset: [] }
    ])
  })
})

describe('templates', () => {
  const [first, second] = before.templates!.items
  const changed = (definition: TemplateDefinition, reason: string): TemplateDefinition => ({ ...definition, reason })

  function draftWith(change: (draft: EditorDraft) => void): EditorDraft {
    const draft = initialDraft(before)
    change(draft)
    return draft
  }

  it('drops an applied create, update and delete', () => {
    const created = emptyTemplate('brand-new')
    created.reason = 'New'
    const draft = draftWith((d) => {
      d.templates.push({ ...created, origin: null })
      d.templates.find(form => form.name === first!.name)!.reason = 'Updated'
      d.templates = d.templates.filter(form => form.name !== second!.name)
    })
    const after = server((p) => {
      const items = p.templates!.items
      items.find(item => item.name === first!.name)!.definition.reason = 'Updated'
      p.templates!.items = items.filter(item => item.name !== second!.name)
      p.templates!.items.push({ name: 'brand-new', version: 1, definition: { type: 'ban', reason: 'New', ladder: ['1d'] } })
    })
    const { draft: next, notes } = rebaseDraft(before, after, draft, 2)
    expect(notes.map(note => note.kind)).toEqual(['applied', 'applied', 'applied'])
    expect(buildChangeSet(after, next).templates).toBeUndefined()
  })

  it('keeps an update against the new version; flags a gone template and a create that now exists', () => {
    const draft = draftWith((d) => {
      d.templates.find(form => form.name === first!.name)!.reason = 'Mine'
      d.templates.push({ ...emptyTemplate('clash'), reason: 'Mine', origin: null })
    })
    const theirs = server((p) => {
      const item = p.templates!.items.find(candidate => candidate.name === first!.name)!
      item.definition = changed(item.definition, 'Theirs')
      item.version += 1
      p.templates!.items.push({ name: 'clash', version: 7, definition: { type: 'mute', reason: 'Theirs', ladder: ['1h'] } })
    })
    const rebased = rebaseDraft(before, theirs, draft, 2)
    expect(noteFor(rebased.notes, first!.name)).toBe('conflict')
    expect(noteFor(rebased.notes, 'clash')).toBe('template-exists')
    expect(buildChangeSet(theirs, rebased.draft).templates).toEqual([
      { op: 'update', name: 'clash', expectedVersion: 7, definition: { type: 'ban', reason: 'Mine', ladder: ['1d'] } },
      { op: 'update', name: first!.name, expectedVersion: first!.version + 1, definition: changed(first!.definition, 'Mine') }
    ])
    const gone = server((p) => {
      p.templates!.items = p.templates!.items.filter(item => item.name !== first!.name)
    })
    expect(noteFor(rebaseDraft(before, gone, draft, 2).notes, first!.name)).toBe('template-gone')
  })

  it('follows the server for templates the user didn\'t touch', () => {
    const draft = initialDraft(before)
    const after = server((p) => {
      p.templates!.items[0]!.definition = changed(p.templates!.items[0]!.definition, 'Changed elsewhere')
      p.templates!.items.splice(1, 1)
    })
    const { draft: next, notes } = rebaseDraft(before, after, draft, 2)
    expect(notes).toEqual([])
    expect(next.templates).toEqual(after.templates!.items.map(item => ({ ...definitionToForm(item.name, item.definition), origin: item.name })))
  })
})

describe('sections and idempotence', () => {
  it('keeps edits of a section that disappeared, without sending them', () => {
    const draft = initialDraft(before)
    draft.templates[0]!.reason = 'Mine'
    const after = server((p) => {
      delete p.templates
      p.meta.sections = p.meta.sections.filter(section => section !== 'templates')
    })
    const { draft: next, notes } = rebaseDraft(before, after, draft, 2)
    expect(notes).toEqual([{ kind: 'section-gone', section: 'templates', scope: '', target: 'templates' }])
    expect(next.templates[0]!.reason).toBe('Mine')
    expect(buildChangeSet(after, next).templates).toBeUndefined()
  })

  it('changes nothing when re-based twice', () => {
    const draft = draftFromChanges(before, fixtureChanges())
    draft.lastSequence = 42
    const after = server(p => setOverride(p, SCOPE, key, 'theirs'))
    const once = rebaseDraft(before, after, draft, 2)
    const twice = rebaseDraft(after, after, once.draft, 2)
    expect(twice.draft).toEqual(once.draft)
    expect(twice.notes.every(note => note.kind === 'kept')).toBe(true)
    expect(once.draft.lastSequence).toBe(42)
  })

  it('demo snapshot: applying every pending change leaves nothing pending after re-basing', () => {
    const draft = draftFromChanges(before, fixtureChanges())
    const after = simulateSnapshot(before, draft)
    const secrets = new Set(before.settings!.fields.filter(field => field.secret).map(field => field.key))
    const applied = new Set(listChanges(before, draft).filter(item => item.ref.section === 'settings' && secrets.has(item.ref.key)).map(item => refId(item.ref)))
    const { draft: next, notes } = rebaseDraft(before, after, draft, 2, { applied })
    expect(notes.every(note => note.kind === 'applied' || note.kind === 'locale-exists')).toBe(true)
    expect(listChanges(after, next)).toEqual([])
    const secretValues = fixtureChanges().settings!.flatMap(block => Object.entries(block.set)
      .filter(([k]) => secrets.has(k)).map(([, value]) => String(value)))
    for (const value of secretValues) {
      expect(JSON.stringify(after)).not.toContain(value)
    }
  })
})
