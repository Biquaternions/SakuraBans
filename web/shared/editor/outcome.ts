// What a result (protocol section 9) does to the editor: the baseline after an applied change
// set, errors of a refused one mapped back to fields, and a local result and snapshot for demo
// mode.

import type { ApplyResult, ChangeSet, JsonValue, Payload, ResultChange, Section } from '../protocol/schema'
import { removeKey } from '../json'
import { GLOBAL } from '../protocol/schema'
import { listChanges, refId, refSection, settingField, templateKey, templatePath } from './draft'
import type { ChangeItem, ChangeRef, EditorDraft, Issue } from './draft'
import { formToDefinition, sameDefinition } from './templates'

export function refForResultChange(change: ResultChange): ChangeRef | null {
  switch (change.section) {
    case 'settings':
      return change.action === 'published'
        ? { section: 'publish', key: change.target }
        : { section: 'settings', scope: change.scope || GLOBAL, key: change.target }
    case 'messages': {
      const colon = change.target.indexOf(':')
      return colon < 0
        ? null
        : { section: 'messages', scope: change.scope || GLOBAL, locale: change.target.slice(0, colon), key: change.target.slice(colon + 1) }
    }
    case 'templates':
      return { section: 'templates', scope: change.scope || GLOBAL, name: change.target }
  }
}

function sectionsOf(changes: ChangeSet): Section[] {
  const sections: Section[] = []
  if (changes.settings) {
    sections.push('settings')
  }
  if (changes.messages) {
    sections.push('messages')
  }
  if (changes.templates) {
    sections.push('templates')
  }
  return sections
}

/** The longest known key that `rest` starts with (keys contain dots and colons). */
function keyIn(rest: string, keys: Iterable<string>): string | undefined {
  return [...keys].filter(key => rest === key || rest.startsWith(`${key}.`) || rest.startsWith(`${key}[`))
    .sort((a, b) => b.length - a.length)[0]
}

/**
 * Maps a refusal path to the change it belongs to, and the path in the editor's terms:
 * `settings[0].set.<key>…`, `settings[0].reset[1]`, `settings[0].publish.keys[0]`,
 * `messages[0].set.<key>`, `templates[1].definition.steps[0].step`; a `settings.set.<key>` without
 * an index means the global block. Null when it names nothing the editor shows.
 */
export function mapErrorPath(payload: Payload, changes: ChangeSet, path: string):
{ ref: ChangeRef, path: string } | null {
  const settingKeys = (payload.settings?.fields ?? []).map(field => field.key)
  const settings = /^settings(?:\[(\d+)\])?\.(set|reset|publish)(.*)$/.exec(path)
  if (settings) {
    const block = settings[1] === undefined
      ? changes.settings?.find(item => item.scope === GLOBAL)
      : changes.settings?.[Number(settings[1])]
    const scope = block?.scope ?? GLOBAL
    const part = settings[2]!
    const rest = settings[3]!
    const index = /^\[(\d+)\]/.exec(rest) ?? /^\.keys\[(\d+)\]/.exec(rest)
    if (part === 'publish') {
      const key = index ? block?.publish?.keys[Number(index[1])] : keyIn(rest.replace(/^\.(keys\.)?/, ''), settingKeys)
      return key ? { ref: { section: 'publish', key }, path: `settings[global].publish.${key}` } : null
    }
    const key = index && part === 'reset'
      ? block?.reset[Number(index[1])]
      : keyIn(rest.replace(/^\./, ''), settingKeys)
    if (!key) {
      return null
    }
    const tail = rest.replace(/^\./, '')
    return { ref: { section: 'settings', scope, key }, path: `settings[${scope}].${part}.${part === 'set' ? tail : key}` }
  }
  const messageKeys = (payload.messages?.fields ?? []).map(field => field.key)
  const messages = /^messages\[(\d+)\]\.(set|reset)(.*)$/.exec(path)
  if (messages) {
    const block = changes.messages?.[Number(messages[1])]
    if (!block) {
      return null
    }
    const rest = messages[3]!
    const index = /^\[(\d+)\]/.exec(rest)
    const key = index && messages[2] === 'reset' ? block.reset[Number(index[1])] : keyIn(rest.replace(/^\./, ''), messageKeys)
    return key
      ? { ref: { section: 'messages', scope: block.scope, locale: block.locale, key }, path: `messages[${block.scope}][${block.locale}].set.${key}` }
      : null
  }
  const templates = /^templates\[(\d+)\](?:\.definition)?\.?(.*)$/.exec(path)
  if (templates) {
    const entry = changes.templates?.[Number(templates[1])]
    if (!entry) {
      return null
    }
    return { ref: { section: 'templates', scope: entry.scope, name: entry.name },
      path: `${templatePath(entry.scope, entry.name)}.${templates[2] || 'name'}` }
  }
  return null
}

export interface OutcomeEffect {
  /** Refusal errors placed next to their fields. */
  issues: Issue[]
  /** Refusal errors that name nothing the editor shows. */
  unmapped: Array<{ path: string, message: string }>
  /** Changes the plugin reported as conflicts (still pending). */
  conflicts: ChangeRef[]
  /** Changes the plugin reported as applied (set, reset, published, created, …). */
  applied: ChangeRef[]
  /** A fresh snapshot to load (protocol section 10), when newer than the draft's. */
  refreshTo: number | null
  /** Applied without a fresh snapshot: these sections are now out of date. */
  stale: Section[]
}

/**
 * What a result means for the editor. Applied with a fresh snapshot: the caller loads it and
 * re-bases (`rebaseDraft`). Applied without one (`payloadRevision` null): the applied changes
 * stop being pending and their sections are marked stale (mutates the draft). Refused: errors
 * are mapped to fields and every edit stays pending.
 */
export function applyOutcome(payload: Payload, draft: EditorDraft, changes: ChangeSet,
  result: ApplyResult): OutcomeEffect {
  const effect: OutcomeEffect = { issues: [], unmapped: [], conflicts: [], applied: [], refreshTo: null, stale: [] }
  if (result.outcome === 'refused') {
    for (const error of result.errors) {
      const mapped = mapErrorPath(payload, changes, error.path)
      if (mapped) {
        effect.issues.push({ ref: mapped.ref, path: mapped.path, message: `${error.message} (refused by the server)` })
      } else {
        effect.unmapped.push(error)
      }
    }
    return effect
  }
  for (const change of result.changes) {
    const ref = refForResultChange(change)
    if (!ref) {
      continue
    }
    if (change.action === 'conflict') {
      effect.conflicts.push(ref)
    } else if (change.action !== 'unchanged') {
      effect.applied.push(ref)
    }
  }
  if (result.payloadRevision !== null) {
    effect.refreshTo = result.payloadRevision > draft.revision ? result.payloadRevision : null
    return effect
  }
  for (const section of sectionsOf(changes)) {
    if (!draft.stale.includes(section)) {
      draft.stale.push(section)
    }
    effect.stale.push(section)
  }
  for (const ref of effect.applied) {
    if (!draft.applied.includes(refId(ref))) {
      draft.applied.push(refId(ref))
    }
  }
  return effect
}

const ACTION: Record<ChangeItem['kind'], ResultChange['action']> = {
  set: 'set', reset: 'reset', publish: 'published', create: 'created', update: 'updated', delete: 'deleted', rename: 'created'
}

/** Demo mode: the result an applying plugin would post if nothing conflicted. */
export function simulateResult(payload: Payload, draft: EditorDraft, changes: ChangeSet,
  payloadRevision: number | null = null): ApplyResult {
  const lines: ResultChange[] = []
  for (const item of listChanges(payload, draft)) {
    const ref = item.ref
    const target = ref.section === 'settings' || ref.section === 'publish'
      ? ref.key
      : ref.section === 'messages' ? `${ref.locale}:${ref.key}` : ref.name
    if (item.kind === 'rename' && ref.section === 'templates') {
      const form = draft.templates.find(other => other.variant === GLOBAL && other.name === target)
      lines.push({ section: 'templates', scope: GLOBAL, target, action: 'created', detail: '' })
      if (form?.origin) {
        lines.push({ section: 'templates', scope: GLOBAL, target: form.origin, action: 'deleted', detail: '' })
      }
      continue
    }
    const secret = (ref.section === 'settings' || ref.section === 'publish') && settingField(payload, ref.key)?.secret
    const detail = secret
      ? ''
      : item.section === 'settings' && (item.kind === 'set' || item.kind === 'publish')
        ? `${item.before} → ${item.after}`
        : item.section === 'messages' && item.kind === 'set'
          ? item.after ?? ''
          : item.kind === 'update' ? (item.note ?? '').replace(/^changed: /, '') : ''
    lines.push({ section: refSection(ref), scope: item.scope, target, action: ACTION[item.kind], detail })
  }
  return {
    format: 1,
    sequence: changes.sequence,
    outcome: 'applied',
    by: 'live',
    changes: lines,
    errors: [],
    summary: { applied: lines.length, unchanged: 0, conflicts: 0 },
    payloadRevision
  }
}

/** The lines of a result grouped by section, for the outcome view. */
export function groupResult(result: ApplyResult): Array<{ section: Section, changes: ResultChange[] }> {
  const groups: Array<{ section: Section, changes: ResultChange[] }> = []
  for (const section of ['settings', 'messages', 'templates'] as const) {
    const changes = result.changes.filter(change => change.section === section)
    if (changes.length > 0) {
      groups.push({ section, changes })
    }
  }
  return groups
}

/**
 * Demo mode: the snapshot the plugin would upload after applying `draft` without conflicts
 * (protocol section 10), so re-basing and "keep editing" can be seen without a server.
 */
export function simulateSnapshot(payload: Payload, draft: EditorDraft): Payload {
  const next = structuredClone(payload)
  const pending = new Set(listChanges(payload, draft).map(item => refId(item.ref)))
  if (next.settings) {
    const touched = new Set<string>()
    const scopeEntry = (scope: string) => {
      let entry = next.settings!.scopes.find(item => item.scope === scope)
      if (!entry) {
        entry = { scope, version: 0, overrides: {} }
        next.settings!.scopes.push(entry)
      }
      return entry
    }
    for (const [scope, edits] of Object.entries(draft.settings)) {
      for (const [key, edit] of Object.entries(edits)) {
        if (!pending.has(`settings:${scope}:${key}`)) {
          continue
        }
        const entry = scopeEntry(scope)
        touched.add(scope)
        if (edit.kind === 'reset') {
          removeKey(entry.overrides, key)
        } else {
          // The snapshot reports secrets as set or not, as the plugin would.
          entry.overrides[key] = settingField(payload, key)?.secret ? edit.value !== '' : edit.value as JsonValue
        }
      }
    }
    if (draft.publish) {
      const server = next.settings.servers.find(item => item.name === draft.publish!.server)
      for (const key of draft.publish.keys) {
        if (server && key in server.fileValues && pending.has(`publish:${key}`)) {
          scopeEntry(GLOBAL).overrides[key] = server.fileValues[key]!
          touched.add(GLOBAL)
        }
      }
    }
    for (const scope of touched) {
      scopeEntry(scope).version += 1
    }
    next.settings.scopes = next.settings.scopes.filter(entry => entry.scope === GLOBAL || Object.keys(entry.overrides).length > 0)
  }
  if (next.messages) {
    for (const [scope, locales] of Object.entries(draft.messages)) {
      for (const [locale, edits] of Object.entries(locales)) {
        const applied = Object.entries(edits).filter(([key]) => pending.has(`messages:${scope}:${locale}:${key}`))
        if (applied.length === 0) {
          continue
        }
        let entry = next.messages.scopes.find(item => item.scope === scope && item.locale === locale)
        if (!entry) {
          entry = { scope, locale, version: 0, overrides: {} }
          next.messages.scopes.push(entry)
        }
        for (const [key, edit] of applied) {
          if (edit.kind === 'reset') {
            removeKey(entry.overrides, key)
          } else {
            entry.overrides[key] = edit.text
          }
        }
        entry.version += 1
      }
    }
    next.messages.scopes = next.messages.scopes.filter(entry => Object.keys(entry.overrides).length > 0)
    for (const locale of draft.addedLocales) {
      if (!next.messages.locales.some(item => item.locale === locale)
        && next.messages.scopes.some(item => item.locale === locale)) {
        next.lookups.locales = [...new Set([...next.lookups.locales, locale])]
      }
    }
  }
  if (next.templates) {
    const before = new Map(next.templates.items.map(item => [templateKey(item.scope, item.name), item]))
    // As the plugin lists them: by name, the global definition first, then its variants.
    next.templates.items = [...draft.templates].sort((a, b) => a.name.localeCompare(b.name)
      || (a.variant === GLOBAL ? -1 : b.variant === GLOBAL ? 1 : a.variant.localeCompare(b.variant))).map((form) => {
      const old = before.get(templateKey(form.variant, form.name))
      const definition = formToDefinition(form)
      if (old && sameDefinition(old.definition, definition)) {
        return old
      }
      return { scope: form.variant, name: form.name, version: (old?.version ?? 0) + 1, definition }
    })
  }
  next.meta.createdAt = Math.max(Date.now(), payload.meta.createdAt + 1)
  return next
}
