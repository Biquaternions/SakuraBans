// Scoped template variants (S62): a definition is keyed by its variant scope and name in the
// change set, the review, undo, validation, re-basing and the demo's result and snapshot.

import { describe, expect, it } from 'vitest'
import { buildChangeSet, initialDraft, listChanges, refId, revertChange, validateDraft } from '../shared/editor/draft'
import { applyOutcome, refForResultChange, simulateResult, simulateSnapshot } from '../shared/editor/outcome'
import { rebaseDraft } from '../shared/editor/rebase'
import { changeKind, definitionToForm } from '../shared/editor/templates'
import { fixturePayload } from './fixtures'

const SKYWARS = 'server:skywars'

describe('template variants', () => {
  it('reads the payload\'s variant and sends nothing for it untouched', () => {
    const p = fixturePayload()
    const draft = initialDraft(p)
    expect(draft.templates.filter(form => form.variant === SKYWARS).map(form => form.name)).toEqual(['spam'])
    expect(buildChangeSet(p, draft).templates).toBeUndefined()
  })

  it('creates a variant from the global definition, without its groups, after the global changes', () => {
    const p = fixturePayload()
    const draft = initialDraft(p)
    const global = draft.templates.find(form => form.variant === 'global' && form.name === 'hacking')!
    global.reason = 'Changed globally'
    draft.templates.push({ ...structuredClone(global), groups: [], origin: null, variant: 'category:minigames', reason: 'Minigames' })
    draft.templates.find(form => form.variant === SKYWARS)!.reason = 'Changed in Skywars'
    expect(validateDraft(p, draft)).toEqual([])

    const ops = buildChangeSet(p, draft).templates!.map(change => `${change.op} ${change.scope} ${change.name}`)
    expect(ops).toEqual(['update global hacking', 'create category:minigames hacking', 'update server:skywars spam'])
    const items = listChanges(p, draft)
    expect(items.map(item => [item.kind, item.scope, item.title])).toEqual([
      ['update', 'global', 'hacking'], ['create', 'category:minigames', 'hacking'], ['update', SKYWARS, 'spam']])
    expect(refId(items[1]!.ref)).toBe('templates:category:minigames:hacking')

    revertChange(p, draft, items[1]!.ref)
    expect(draft.templates.some(form => form.variant === 'category:minigames')).toBe(false)
    revertChange(p, draft, items[2]!.ref)
    expect(buildChangeSet(p, draft).templates!.map(change => change.name)).toEqual(['hacking'])
  })

  it('refuses a variant of another kind, with groups, or without its global template', () => {
    const p = fixturePayload()
    const draft = initialDraft(p)
    const index = draft.templates.findIndex(form => form.variant === SKYWARS)
    draft.templates.splice(index, 1, { ...changeKind(draft.templates[index]!, 'ban'), origin: 'spam', variant: SKYWARS })
    expect(validateDraft(p, draft).map(issue => [issue.path, issue.message])).toContainEqual(
      ['templates[server:skywars].spam.type', 'a variant has its global template\'s type: mute'])

    const grouped = initialDraft(p)
    grouped.templates.find(form => form.variant === SKYWARS)!.groups.push({ name: 'chat', weight: 1 })
    expect(validateDraft(p, grouped).map(issue => issue.path)).toContain('templates[server:skywars].spam.groups')

    const orphan = initialDraft(p)
    orphan.templates = orphan.templates.filter(form => !(form.variant === 'global' && form.name === 'spam'))
    expect(validateDraft(p, orphan).map(issue => issue.path)).toContain('templates[server:skywars].spam.name')
  })

  it('deletes a global template and its variants, the variant first', () => {
    const p = fixturePayload()
    const draft = initialDraft(p)
    draft.templates = draft.templates.filter(form => form.name !== 'spam')
    expect(validateDraft(p, draft)).toEqual([])
    expect(buildChangeSet(p, draft).templates).toEqual([
      { op: 'delete', scope: SKYWARS, name: 'spam', expectedVersion: 1 },
      { op: 'delete', scope: 'global', name: 'spam', expectedVersion: 1 }])
    expect(listChanges(p, draft).map(item => item.note)).toEqual(['variant removed', 'deleted'])

    const result = simulateResult(p, draft, buildChangeSet(p, draft, 9))
    expect(result.changes.map(change => [change.scope, change.target, change.action])).toEqual([
      [SKYWARS, 'spam', 'deleted'], ['global', 'spam', 'deleted']])
    expect(simulateSnapshot(p, draft).templates!.items.some(item => item.name === 'spam')).toBe(false)
  })

  it('maps result lines and refused paths of a variant to it', () => {
    const p = fixturePayload()
    const draft = initialDraft(p)
    draft.templates.find(form => form.variant === SKYWARS)!.reason = 'Mine'
    const changes = buildChangeSet(p, draft, 7)
    expect(refForResultChange({ section: 'templates', scope: SKYWARS, target: 'spam', action: 'conflict', detail: '' }))
      .toEqual({ section: 'templates', scope: SKYWARS, name: 'spam' })
    const refused = applyOutcome(p, draft, changes, {
      format: 1, sequence: 7, outcome: 'refused', by: 'live', changes: [],
      errors: [{ path: 'templates[0].definition.reason', message: 'too long' }],
      summary: { applied: 0, unchanged: 0, conflicts: 0 }, payloadRevision: null
    })
    expect(refused.issues.map(issue => [issue.ref, issue.path])).toEqual([
      [{ section: 'templates', scope: SKYWARS, name: 'spam' }, 'templates[server:skywars].spam.reason']])
  })

  it('re-bases a variant on its own: applied, conflict and one created elsewhere', () => {
    const before = fixturePayload()
    const draft = initialDraft(before)
    draft.templates.find(form => form.variant === SKYWARS)!.reason = 'Mine'
    draft.templates.push({ ...definitionToForm('hacking', { type: 'ladder', reason: 'Here', steps: ['ban 1d'] }),
      origin: null, variant: 'category:minigames' })

    const applied = simulateSnapshot(before, draft)
    const done = rebaseDraft(before, applied, draft, 2)
    expect(done.notes.map(note => [note.kind, note.scope, note.target])).toEqual([
      ['applied', 'category:minigames', 'hacking'], ['applied', SKYWARS, 'spam']])
    expect(buildChangeSet(applied, done.draft).templates).toBeUndefined()

    const theirs = structuredClone(before)
    const variant = theirs.templates!.items.find(item => item.scope === SKYWARS)!
    variant.definition = { ...variant.definition, reason: 'Theirs' }
    variant.version += 1
    theirs.templates!.items.push({ scope: 'category:minigames', name: 'hacking', version: 3,
      definition: { type: 'ladder', reason: 'Theirs', steps: ['ban 2d'] } })
    const kept = rebaseDraft(before, theirs, draft, 2)
    expect(kept.notes.map(note => [note.kind, note.scope, note.target])).toEqual([
      ['template-exists', 'category:minigames', 'hacking'], ['conflict', SKYWARS, 'spam']])
    expect(buildChangeSet(theirs, kept.draft).templates!.map(change =>
      [change.op, change.scope, change.name, change.op === 'delete' ? null : change.definition.reason])).toEqual([
      ['update', 'category:minigames', 'hacking', 'Here'], ['update', SKYWARS, 'spam', 'Mine']])
  })
})
