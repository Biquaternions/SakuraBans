// Re-basing a draft on a fresh snapshot (protocol section 10, "Browser"). Pure: the old
// payload, the new payload and the draft go in; a new draft and notes for the user come out.
//
// - A pending edit whose value equals the new snapshot's override in that scope is dropped: it
//   was applied (a reset: the override is gone). A secret's new value can't be compared (the
//   snapshot only says set or not), so the result's applied entries decide.
// - Every other pending edit stays, against the new snapshot and its versions. If the scope's
//   override moved between the two snapshots (someone else changed it, or the plugin reported
//   a conflict), the note says so: the user now sees the edit next to the current value.
// - Publishing: a key is dropped once global holds the publishing server's file value.
// - A pending update of a template that no longer exists, and a pending create of one that now
//   exists, are kept and flagged ("template-gone", "template-exists"). Templates are keyed by
//   variant scope and name, so a variant is re-based on its own.
// - Edits of a section the new snapshot doesn't have stay in the draft but can't be sent.

import { jsonEqual } from '../json'
import type { JsonValue, Payload, Section } from '../protocol/schema'
import { GLOBAL } from '../protocol/schema'
import { initialDraft, messageOverride, refId, settingField, settingOverride, templateKey } from './draft'
import type { EditorDraft, MessageEdit, SettingEdit, TemplateDraft } from './draft'
import { definitionToForm, formToDefinition, sameDefinition } from './templates'

export type RebaseNoteKind
  = | 'applied' // dropped: the new snapshot already has this value
    | 'kept' // still pending; the server's value didn't move
    | 'conflict' // still pending; the server's value changed meanwhile
    | 'template-gone' // an update of a template that no longer exists (kept as a create)
    | 'template-exists' // a create of a template that now exists (kept as an update)
    | 'locale-exists' // a locale created here now exists on the server
    | 'section-gone' // the new snapshot has no such section: edits kept, can't be sent

export interface RebaseNote {
  kind: RebaseNoteKind
  section: Section
  /** The scope for settings and messages, the variant (`global` included) for templates, "" otherwise. */
  scope: string
  /** The key, "<locale>:<key>", the template name, or the section name. */
  target: string
}

export interface RebaseResult {
  draft: EditorDraft
  notes: RebaseNote[]
}

export interface RebaseHints {
  /** refIds the plugin reported as conflicts: their notes say "conflict". */
  conflicts?: ReadonlySet<string>
  /** refIds the plugin reported as applied: decides for secrets, whose values can't be compared. */
  applied?: ReadonlySet<string>
}

export const NOTE_TEXT: Record<RebaseNoteKind, string> = {
  'applied': 'applied: now part of the snapshot',
  'kept': 'still pending',
  'conflict': 'still pending, but the server\'s value changed meanwhile: check it before saving again',
  'template-gone': 'this template was deleted on the server: saving recreates it; undo to discard your edit',
  'template-exists': 'a template with this name now exists: saving replaces it with yours; undo to keep the server\'s',
  'locale-exists': 'this locale now exists on the server',
  'section-gone': 'this section is no longer in the snapshot (a permission may have changed): its edits are kept but can\'t be saved'
}

function rebaseSettings(before: Payload, after: Payload, draft: EditorDraft, next: EditorDraft,
  notes: RebaseNote[], hints: Required<RebaseHints>) {
  const scoped = Object.entries(draft.settings).filter(([, edits]) => Object.keys(edits).length > 0)
  if (scoped.length === 0 && !draft.publish) {
    return
  }
  if (!after.settings) {
    next.settings = structuredClone(draft.settings)
    next.publish = draft.publish ? { ...draft.publish, keys: [...draft.publish.keys] } : null
    notes.push({ kind: 'section-gone', section: 'settings', scope: '', target: 'settings' })
    return
  }
  for (const [scope, edits] of scoped) {
    for (const [key, edit] of Object.entries(edits)) {
      const note = (kind: RebaseNoteKind) => notes.push({ kind, section: 'settings', scope, target: key })
      const field = settingField(after, key)
      if (!field) {
        (next.settings[scope] ??= {})[key] = edit
        note('section-gone')
        continue
      }
      const now = settingOverride(after, scope, key)
      const was = settingOverride(before, scope, key)
      const id = refId({ section: 'settings', scope, key })
      let applied: boolean
      if (edit.kind === 'reset') {
        applied = now === undefined
      } else if (field.secret) {
        applied = hints.applied.has(id) || (now === false && edit.value === '' && was !== false)
      } else {
        applied = now !== undefined && jsonEqual(edit.value, now)
      }
      if (applied) {
        note('applied')
        continue
      }
      (next.settings[scope] ??= {})[key] = edit as SettingEdit
      const moved = !jsonEqual(was ?? null, now ?? null) || (was === undefined) !== (now === undefined)
      note(moved || hints.conflicts.has(id) ? 'conflict' : 'kept')
    }
  }
  if (draft.publish) {
    const server = after.settings.servers.find(item => item.name === draft.publish!.server)
    const keys: string[] = []
    for (const key of draft.publish.keys) {
      const note = (kind: RebaseNoteKind) => notes.push({ kind, section: 'settings', scope: GLOBAL, target: key })
      const now = settingOverride(after, GLOBAL, key)
      const value: JsonValue | undefined = server?.fileValues[key]
      if (hints.applied.has(refId({ section: 'publish', key })) || (now !== undefined && value !== undefined && jsonEqual(now, value))) {
        note('applied')
      } else {
        keys.push(key)
        note(hints.conflicts.has(refId({ section: 'publish', key })) ? 'conflict' : 'kept')
      }
    }
    next.publish = keys.length > 0 ? { server: draft.publish.server, keys } : null
  }
}

function rebaseMessages(before: Payload, after: Payload, draft: EditorDraft, next: EditorDraft,
  notes: RebaseNote[], hints: Required<RebaseHints>) {
  const entries: Array<[string, string, Record<string, MessageEdit>]> = []
  for (const [scope, locales] of Object.entries(draft.messages)) {
    for (const [locale, edits] of Object.entries(locales)) {
      if (Object.keys(edits).length > 0) {
        entries.push([scope, locale, edits])
      }
    }
  }
  if (!after.messages) {
    if (entries.length > 0 || draft.addedLocales.length > 0) {
      next.messages = structuredClone(draft.messages)
      next.addedLocales = [...draft.addedLocales]
      notes.push({ kind: 'section-gone', section: 'messages', scope: '', target: 'messages' })
    }
    return
  }
  const known = new Set([...after.messages.locales.map(item => item.locale), ...after.messages.scopes.map(item => item.locale)])
  for (const locale of draft.addedLocales) {
    if (known.has(locale)) {
      notes.push({ kind: 'locale-exists', section: 'messages', scope: '', target: locale })
    } else {
      next.addedLocales.push(locale)
    }
  }
  const keys = new Set(after.messages.fields.map(field => field.key))
  for (const [scope, locale, edits] of entries) {
    for (const [key, edit] of Object.entries(edits)) {
      const target = `${locale}:${key}`
      const note = (kind: RebaseNoteKind) => notes.push({ kind, section: 'messages', scope, target })
      const keep = () => {
        ((next.messages[scope] ??= {})[locale] ??= {})[key] = edit
      }
      if (!keys.has(key)) {
        keep()
        note('section-gone')
        continue
      }
      const now = messageOverride(after, scope, locale, key)
      const was = messageOverride(before, scope, locale, key)
      const applied = edit.kind === 'reset' ? now === undefined : edit.text === now
      if (applied) {
        note('applied')
        continue
      }
      keep()
      const id = refId({ section: 'messages', scope, locale, key })
      note(was !== now || hints.conflicts.has(id) ? 'conflict' : 'kept')
    }
  }
}

function rebaseTemplates(before: Payload, after: Payload, draft: EditorDraft, next: EditorDraft,
  notes: RebaseNote[], hints: Required<RebaseHints>) {
  // Keyed by variant scope and name: a variant re-bases like any other definition.
  const keyed = (items: Payload['templates']) => new Map((items?.items ?? []).map(item => [templateKey(item.scope, item.name), item]))
  const oldItems = keyed(before.templates)
  const drafted = new Map(draft.templates.map(form => [templateKey(form.variant, form.name), form]))
  const pendingKeys = [...new Set([...oldItems.keys(), ...drafted.keys()])].filter((key) => {
    const old = oldItems.get(key)
    const form = drafted.get(key)
    return !old || !form || !sameDefinition(old.definition, formToDefinition(form))
  })
  if (!after.templates) {
    next.templates = draft.templates.map(form => structuredClone(form))
    if (pendingKeys.length > 0) {
      notes.push({ kind: 'section-gone', section: 'templates', scope: '', target: 'templates' })
    }
    return
  }
  const newItems = keyed(after.templates)
  const split = (key: string) => {
    const space = key.indexOf(' ')
    return { scope: key.slice(0, space), name: key.slice(space + 1) }
  }
  const moved = (key: string): boolean => {
    const old = oldItems.get(key)
    const now = newItems.get(key)
    const { scope, name } = split(key)
    return hints.conflicts.has(refId({ section: 'templates', scope, name })) || !old || !now || old.version !== now.version
      || !sameDefinition(old.definition, now.definition)
  }
  const note = (kind: RebaseNoteKind, key: string) => {
    const { scope, name } = split(key)
    notes.push({ kind, section: 'templates', scope, target: name })
  }
  const result = new Map<string, TemplateDraft>()
  for (const [key, item] of newItems) {
    result.set(key, { ...definitionToForm(item.name, item.definition), origin: item.name, variant: item.scope })
  }

  for (const key of [...new Set([...oldItems.keys(), ...drafted.keys(), ...newItems.keys()])].sort()) {
    const old = oldItems.get(key)
    const form = drafted.get(key)
    const now = newItems.get(key)
    if (!form && !old) {
      continue // only on the server: already in `result`
    }
    if (!form) {
      // A pending delete (or the old half of a rename).
      if (!now) {
        note('applied', key)
      } else {
        result.delete(key)
        note(moved(key) ? 'conflict' : 'kept', key)
      }
      continue
    }
    const mine = formToDefinition(form)
    if (old && sameDefinition(old.definition, mine)) {
      continue // untouched: follow the server (kept, changed or gone)
    }
    if (now && sameDefinition(now.definition, mine)) {
      note('applied', key)
      continue
    }
    if (now) {
      result.set(key, { ...structuredClone(form), origin: old ? form.origin : form.name })
      note(old ? (moved(key) ? 'conflict' : 'kept') : 'template-exists', key)
    } else {
      result.set(key, structuredClone(form))
      note(old ? 'template-gone' : 'kept', key)
    }
  }
  // The server's order first, then definitions only the draft has.
  const order = [...after.templates.items.map(item => templateKey(item.scope, item.name)),
    ...draft.templates.map(form => templateKey(form.variant, form.name))]
  next.templates = [...new Set(order)].filter(key => result.has(key)).map(key => result.get(key)!)
}

/** Re-bases `draft` (made against `before`) on `after`. */
export function rebaseDraft(before: Payload, after: Payload, draft: EditorDraft, revision: number,
  hints: RebaseHints = {}): RebaseResult {
  const notes: RebaseNote[] = []
  const all: Required<RebaseHints> = { conflicts: hints.conflicts ?? new Set(), applied: hints.applied ?? new Set() }
  const next: EditorDraft = {
    ...initialDraft(after, revision),
    settings: {},
    publish: null,
    messages: {},
    addedLocales: [],
    lastSequence: draft.lastSequence,
    stale: [],
    applied: []
  }
  rebaseSettings(before, after, draft, next, notes, all)
  rebaseMessages(before, after, draft, next, notes, all)
  rebaseTemplates(before, after, draft, next, notes, all)
  return { draft: next, notes }
}
