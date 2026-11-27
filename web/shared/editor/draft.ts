// The editor's working state and the change set built from it (protocol section 6).
//
// The draft holds only what the user did; everything else is read from the payload. Settings
// and messages are edited per scope: in a scope a key is either set (an override there) or
// reset (the override removed, so the next layer applies). Global may also publish a server's
// file values. Templates are held as the whole edited list and compared with the payload by
// scope and name: `global` for a template's global definition, a server or category for one of
// its variants.

import { canonicalJson, jsonEqual, removeKey } from '../json'
import { FORMAT } from '../protocol/constants'
import type {
  ChangeSet, JsonValue, MessagesBlock, Payload, Section, SettingField, SettingsBlock,
  TemplateChange, TemplateDefinition, TemplateItem
} from '../protocol/schema'
import { GLOBAL, LOCALE_PATTERN, SCOPE_PATTERN } from '../protocol/schema'
import { MAX_MESSAGE } from './limits'
import { scopeName } from './layers'
import { validateSetting } from './settings'
import { definitionToForm, formToDefinition, kindOf, normalizeDefinition, sameDefinition, validateTemplate } from './templates'
import type { TemplateForm } from './templates'

export type SettingEdit = { kind: 'set', value: JsonValue } | { kind: 'reset' }
export type MessageEdit = { kind: 'set', text: string } | { kind: 'reset' }

export type TemplateDraft = TemplateForm & {
  /** The name the template had in the payload; null for one created in the editor. */
  origin: string | null
  /**
   * Where this definition is in force: `global`, or a variant's server or category. Not
   * `scope` of the form, which is where its punishments apply.
   */
  variant: string
}

/** A definition's identity: its variant scope and name. */
export function templateKey(variant: string, name: string): string {
  return `${variant} ${name}`
}

/** The path prefix of a definition's issues: `templates.<name>` or `templates[<scope>].<name>`. */
export function templatePath(variant: string, name: string): string {
  return variant === GLOBAL ? `templates.${name || '(unnamed)'}` : `templates[${variant}].${name || '(unnamed)'}`
}

/**
 * The order the plugin applies template changes in (EditorService): variant deletions,
 * global creations and updates, variant creations and updates, global deletions. The change set
 * is written in this order, so its result lines come back in it too.
 */
export function templatePhase(variant: string, op: 'create' | 'update' | 'delete'): number {
  if (variant !== GLOBAL) {
    return op === 'delete' ? 0 : 2
  }
  return op === 'delete' ? 3 : 1
}

/** Payload items by scope and name. */
function templateItems(payload: Payload): Map<string, TemplateItem> {
  return new Map((payload.templates?.items ?? []).map(item => [templateKey(item.scope, item.name), item]))
}

function byNameThenScope(a: { variant: string, name: string }, b: { variant: string, name: string }): number {
  return a.name.localeCompare(b.name) || (a.variant === GLOBAL ? -1 : b.variant === GLOBAL ? 1 : a.variant.localeCompare(b.variant))
}

export interface PublishDraft {
  /** The server's name (protocol 6: `publish.server`). */
  server: string
  keys: string[]
}

export interface EditorDraft {
  /** scope → key → edit */
  settings: Record<string, Record<string, SettingEdit>>
  /** Global only: make a server's file values the global overrides for these keys. */
  publish: PublishDraft | null
  /** scope → locale → key → edit */
  messages: Record<string, Record<string, Record<string, MessageEdit>>>
  /** Locales created in the editor (not in the payload). */
  addedLocales: string[]
  templates: TemplateDraft[]
  /** The sequence of the last save (protocol section 6); 0 before the first. */
  lastSequence: number
  /** The payload revision (protocol section 3) this draft is based on. */
  revision: number
  /**
   * Sections a change set was applied to without a fresh snapshot (`payloadRevision` null):
   * their versions are out of date, so a further save there will conflict. Warned, not locked.
   */
  stale: Section[]
  /** Changes reported as applied while the snapshot couldn't be refreshed (refIds). */
  applied: string[]
}

export function initialDraft(payload: Payload, revision = 1): EditorDraft {
  return {
    settings: {},
    publish: null,
    messages: {},
    addedLocales: [],
    templates: (payload.templates?.items ?? []).map(item => ({
      ...definitionToForm(item.name, item.definition),
      origin: item.name,
      variant: item.scope
    })),
    lastSequence: 0,
    revision,
    stale: [],
    applied: []
  }
}

// ---- Payload lookups -------------------------------------------------------------------------

export function settingField(payload: Payload, key: string): SettingField | undefined {
  return payload.settings?.fields.find(field => field.key === key)
}

export function settingOverride(payload: Payload, scope: string, key: string): JsonValue | undefined {
  return payload.settings?.scopes.find(item => item.scope === scope)?.overrides[key]
}

export function settingsVersion(payload: Payload, scope: string): number {
  return payload.settings?.scopes.find(item => item.scope === scope)?.version ?? 0
}

export function messageOverride(payload: Payload, scope: string, locale: string, key: string): string | undefined {
  return payload.messages?.scopes.find(item => item.scope === scope && item.locale === locale)?.overrides[key]
}

export function messagesVersion(payload: Payload, scope: string, locale: string): number {
  return payload.messages?.scopes.find(item => item.scope === scope && item.locale === locale)?.version ?? 0
}

export function knownLocales(payload: Payload, draft: EditorDraft): string[] {
  const locales = new Set((payload.messages?.locales ?? []).map(item => item.locale))
  for (const scope of payload.messages?.scopes ?? []) {
    locales.add(scope.locale)
  }
  for (const locale of draft.addedLocales) {
    locales.add(locale)
  }
  return [...locales].sort()
}

/** Scopes in a stable order: global, the payload's, then the draft's own. */
function orderedScopes(listed: string[], drafted: string[]): string[] {
  return [...new Set([GLOBAL, ...listed, ...drafted])]
}

// ---- Edits that matter -----------------------------------------------------------------------

/** Whether an edit changes anything against the payload's override in that scope. */
export function settingEditMatters(payload: Payload, scope: string, key: string, edit: SettingEdit): boolean {
  const current = settingOverride(payload, scope, key)
  if (edit.kind === 'reset') {
    return current !== undefined
  }
  if (current === undefined) {
    return true
  }
  if (settingField(payload, key)?.secret) {
    // Only an empty override can be recognised; a new value always counts.
    return !(edit.value === '' && current === false)
  }
  return !jsonEqual(edit.value, current)
}

export function messageEditMatters(payload: Payload, scope: string, locale: string, key: string, edit: MessageEdit): boolean {
  const current = messageOverride(payload, scope, locale, key)
  if (edit.kind === 'reset') {
    return current !== undefined
  }
  return edit.text !== current
}

// ---- Change set ------------------------------------------------------------------------------

/** The next save's sequence: the clock, but always above the previous one. */
export function nextSequence(draft: EditorDraft, now: number): number {
  return Math.max(Math.floor(now), draft.lastSequence + 1)
}

export type ChangeRef
  = | { section: 'settings', scope: string, key: string }
    | { section: 'publish', key: string }
    | { section: 'messages', scope: string, locale: string, key: string }
    | { section: 'templates', scope: string, name: string }

export function refId(ref: ChangeRef): string {
  switch (ref.section) {
    case 'settings':
      return `settings:${ref.scope}:${ref.key}`
    case 'publish':
      return `publish:${ref.key}`
    case 'messages':
      return `messages:${ref.scope}:${ref.locale}:${ref.key}`
    case 'templates':
      return `templates:${ref.scope}:${ref.name}`
  }
}

/** The section of the payload a change belongs to (publishing is part of settings). */
export function refSection(ref: ChangeRef): Section {
  return ref.section === 'publish' ? 'settings' : ref.section
}

export function buildChangeSet(payload: Payload, draft: EditorDraft, sequence = 1): ChangeSet {
  const changes: ChangeSet = { format: FORMAT as 1, sequence }
  const applied = new Set(draft.applied)
  const done = (ref: ChangeRef) => applied.has(refId(ref))

  if (payload.settings) {
    const fields = new Map(payload.settings.fields.map(field => [field.key, field]))
    const blocks: SettingsBlock[] = []
    for (const scope of orderedScopes(payload.settings.scopes.map(item => item.scope), Object.keys(draft.settings))) {
      const set: Record<string, JsonValue> = {}
      const reset: string[] = []
      for (const [key, edit] of Object.entries(draft.settings[scope] ?? {})) {
        if (!fields.has(key) || !settingEditMatters(payload, scope, key, edit) || done({ section: 'settings', scope, key })) {
          continue
        }
        if (edit.kind === 'set') {
          set[key] = edit.value
        } else {
          reset.push(key)
        }
      }
      const publishKeys = scope === GLOBAL && draft.publish
        ? draft.publish.keys.filter(key => fields.has(key) && !done({ section: 'publish', key }))
        : []
      if (Object.keys(set).length === 0 && reset.length === 0 && publishKeys.length === 0) {
        continue
      }
      const block: SettingsBlock = { scope, expectedVersion: settingsVersion(payload, scope), set, reset }
      if (publishKeys.length > 0) {
        block.publish = { server: draft.publish!.server, keys: publishKeys }
      }
      blocks.push(block)
    }
    if (blocks.length > 0) {
      changes.settings = blocks
    }
  }

  if (payload.messages) {
    const keys = new Set(payload.messages.fields.map(field => field.key))
    const blocks: MessagesBlock[] = []
    for (const scope of orderedScopes(payload.messages.scopes.map(item => item.scope), Object.keys(draft.messages))) {
      for (const locale of Object.keys(draft.messages[scope] ?? {}).sort()) {
        const set: Record<string, string> = {}
        const reset: string[] = []
        for (const [key, edit] of Object.entries(draft.messages[scope]![locale]!)) {
          if (!keys.has(key) || !messageEditMatters(payload, scope, locale, key, edit)
            || done({ section: 'messages', scope, locale, key })) {
            continue
          }
          if (edit.kind === 'set') {
            set[key] = edit.text
          } else {
            reset.push(key)
          }
        }
        if (Object.keys(set).length > 0 || reset.length > 0) {
          blocks.push({ scope, locale, expectedVersion: messagesVersion(payload, scope, locale), set, reset })
        }
      }
    }
    if (blocks.length > 0) {
      changes.messages = blocks
    }
  }

  if (payload.templates) {
    const original = templateItems(payload)
    const current = new Set(draft.templates.map(form => templateKey(form.variant, form.name)))
    const templates: Array<{ phase: number, change: TemplateChange }> = []
    for (const form of [...draft.templates].sort(byNameThenScope)) {
      const before = original.get(templateKey(form.variant, form.name))
      const definition = formToDefinition(form)
      if (done({ section: 'templates', scope: form.variant, name: form.name })) {
        continue
      }
      if (!before) {
        templates.push({ phase: templatePhase(form.variant, 'create'),
          change: { op: 'create', scope: form.variant, name: form.name, definition } })
      } else if (!sameDefinition(before.definition, definition)) {
        // Creations before updates within a phase.
        templates.push({ phase: templatePhase(form.variant, 'update') + 0.5,
          change: { op: 'update', scope: form.variant, name: form.name, expectedVersion: before.version, definition } })
      }
    }
    for (const item of [...original.values()].sort((a, b) => byNameThenScope({ variant: a.scope, name: a.name }, { variant: b.scope, name: b.name }))) {
      if (!current.has(templateKey(item.scope, item.name)) && !done({ section: 'templates', scope: item.scope, name: item.name })) {
        templates.push({ phase: templatePhase(item.scope, 'delete'),
          change: { op: 'delete', scope: item.scope, name: item.name, expectedVersion: item.version } })
      }
    }
    if (templates.length > 0) {
      // A stable sort keeps name order within a phase.
      changes.templates = templates.sort((a, b) => a.phase - b.phase).map(entry => entry.change)
    }
  }

  return changes
}

export function isEmptyChangeSet(changes: Pick<ChangeSet, 'settings' | 'messages' | 'templates'>): boolean {
  return !changes.settings && !changes.messages && !changes.templates
}

// ---- Review ----------------------------------------------------------------------------------

export interface ChangeItem {
  ref: ChangeRef
  section: Section
  kind: 'set' | 'reset' | 'publish' | 'create' | 'update' | 'delete' | 'rename'
  /** The scope: of a setting or message, or the variant of a template (`global` for its global definition). */
  scope: string
  title: string
  before?: string
  after?: string
  note?: string
  /** In a section whose snapshot is out of date: saving it again will likely conflict. */
  stale?: boolean
}

export const HIDDEN = '(hidden)'

export function displayValue(value: JsonValue | undefined, secret = false): string {
  if (value === undefined) {
    return '(not set here)'
  }
  if (secret) {
    return value === true ? 'set' + ' ' + HIDDEN : value === false ? 'empty' : value === '' ? 'empty' : `new value ${HIDDEN}`
  }
  if (typeof value === 'string') {
    return value === '' ? '(empty)' : value
  }
  if (Array.isArray(value) && value.every(item => typeof item === 'string')) {
    return value.length === 0 ? '(none)' : value.join(', ')
  }
  return canonicalJson(value)
}

export function scopeLabel(scope: string): string {
  if (scope === GLOBAL || scope === '') {
    return scope === '' ? '' : 'Global'
  }
  return `${scope.startsWith('server:') ? 'Server' : 'Category'} ${scopeName(scope)}`
}

/** Pending changes for the review: applied ones left out, ones in stale sections marked. */
export function listChanges(payload: Payload, draft: EditorDraft): ChangeItem[] {
  const applied = new Set(draft.applied)
  return listAllChanges(payload, draft)
    .filter(item => !applied.has(refId(item.ref)))
    .map(item => (draft.stale.includes(item.section) ? { ...item, stale: true } : item))
}

/** Every difference between the draft and the payload, applied or not. */
export function listAllChanges(payload: Payload, draft: EditorDraft): ChangeItem[] {
  const items: ChangeItem[] = []
  if (payload.settings) {
    for (const scope of orderedScopes(payload.settings.scopes.map(item => item.scope), Object.keys(draft.settings))) {
      for (const [key, edit] of Object.entries(draft.settings[scope] ?? {})) {
        const field = settingField(payload, key)
        if (!field || !settingEditMatters(payload, scope, key, edit)) {
          continue
        }
        const before = displayValue(settingOverride(payload, scope, key), field.secret)
        const ref: ChangeRef = { section: 'settings', scope, key }
        items.push(edit.kind === 'set'
          ? { ref, section: 'settings', kind: 'set', scope, title: key, before, after: displayValue(edit.value, field.secret) }
          : { ref, section: 'settings', kind: 'reset', scope, title: key, before, note: 'override removed: the next layer applies' })
      }
    }
    if (draft.publish) {
      const server = payload.settings.servers.find(item => item.name === draft.publish!.server)
      for (const key of draft.publish.keys) {
        const field = settingField(payload, key)
        if (!field) {
          continue
        }
        items.push({ ref: { section: 'publish', key }, section: 'settings', kind: 'publish', scope: GLOBAL, title: key,
          before: displayValue(settingOverride(payload, GLOBAL, key), field.secret),
          after: displayValue(server?.fileValues[key], field.secret),
          note: `published from ${draft.publish.server}'s file` })
      }
    }
  }
  if (payload.messages) {
    for (const scope of orderedScopes(payload.messages.scopes.map(item => item.scope), Object.keys(draft.messages))) {
      for (const [locale, edits] of Object.entries(draft.messages[scope] ?? {})) {
        for (const [key, edit] of Object.entries(edits)) {
          if (!messageEditMatters(payload, scope, locale, key, edit)) {
            continue
          }
          const ref: ChangeRef = { section: 'messages', scope, locale, key }
          const before = messageOverride(payload, scope, locale, key) ?? '(not set here)'
          items.push(edit.kind === 'set'
            ? { ref, section: 'messages', kind: 'set', scope, title: `${locale} · ${key}`, before, after: edit.text,
                note: draft.addedLocales.includes(locale) ? 'new locale' : undefined }
            : { ref, section: 'messages', kind: 'reset', scope, title: `${locale} · ${key}`, before,
                note: 'override removed: the next layer applies' })
        }
      }
    }
  }
  if (payload.templates) {
    const original = templateItems(payload)
    const current = new Set(draft.templates.map(form => templateKey(form.variant, form.name)))
    const renamedFrom = new Set<string>()
    const templates: Array<{ phase: number, item: ChangeItem }> = []
    for (const form of draft.templates) {
      const key = templateKey(form.variant, form.name)
      const before = original.get(key)
      const ref: ChangeRef = { section: 'templates', scope: form.variant, name: form.name }
      if (!before) {
        // Only a global definition can be renamed: a variant's name is its template's.
        const renamed = form.variant === GLOBAL && form.origin !== null && form.origin !== form.name
          && original.has(templateKey(GLOBAL, form.origin)) && !current.has(templateKey(GLOBAL, form.origin))
        if (renamed) {
          renamedFrom.add(form.origin!)
        }
        templates.push({ phase: templatePhase(form.variant, 'create'), item: { ref, section: 'templates', scope: form.variant,
          kind: renamed ? 'rename' : 'create',
          title: renamed ? `${form.origin} → ${form.name}` : form.name,
          note: renamed
            ? 'sent as delete + create: the new name starts with fresh history'
            : form.variant === GLOBAL ? 'created' : 'variant created' } })
      } else if (!sameDefinition(before.definition, formToDefinition(form))) {
        templates.push({ phase: templatePhase(form.variant, 'update') + 0.5, item: { ref, section: 'templates',
          scope: form.variant, kind: 'update', title: form.name, note: describeTemplateDiff(before.definition, form) } })
      }
    }
    for (const item of payload.templates.items) {
      if (!current.has(templateKey(item.scope, item.name)) && !(item.scope === GLOBAL && renamedFrom.has(item.name))) {
        templates.push({ phase: templatePhase(item.scope, 'delete'), item: { ref: { section: 'templates', scope: item.scope, name: item.name },
          section: 'templates', scope: item.scope, kind: 'delete', title: item.name,
          note: item.scope === GLOBAL ? 'deleted' : 'variant removed' } })
      }
    }
    items.push(...templates.sort((a, b) => a.phase - b.phase).map(entry => entry.item))
  }
  return items
}

/**
 * The definition keys that differ, in the plugin's words: its result names the changed keys of
 * templates.yml in file order (EditorService.fields), and the demo's simulated result does too.
 */
function describeTemplateDiff(before: TemplateDefinition, form: TemplateForm): string {
  const a = normalizeDefinition(before) as Record<string, JsonValue>
  const b = formToDefinition(form) as Record<string, JsonValue>
  const keys = new Set([...Object.keys(a), ...Object.keys(b)])
  const changed = [...keys].filter(key => canonicalJson(a[key]) !== canonicalJson(b[key]))
  return `changed: ${changed.join(', ')}`
}

/** Undoes one change of the review list. */
export function revertChange(payload: Payload, draft: EditorDraft, ref: ChangeRef): void {
  switch (ref.section) {
    case 'settings':
      removeKey(draft.settings[ref.scope] ?? {}, ref.key)
      break
    case 'publish':
      if (draft.publish) {
        draft.publish.keys = draft.publish.keys.filter(key => key !== ref.key)
        if (draft.publish.keys.length === 0) {
          draft.publish = null
        }
      }
      break
    case 'messages':
      removeKey(draft.messages[ref.scope]?.[ref.locale] ?? {}, ref.key)
      break
    case 'templates': {
      const original = templateItems(payload).get(templateKey(ref.scope, ref.name))
      const index = draft.templates.findIndex(form => form.variant === ref.scope && form.name === ref.name)
      const restore = (item: TemplateItem): TemplateDraft => ({
        ...definitionToForm(item.name, item.definition), origin: item.name, variant: item.scope
      })
      if (index >= 0) {
        const form = draft.templates[index]!
        const source = form.origin === null || ref.scope !== GLOBAL
          ? undefined
          : templateItems(payload).get(templateKey(GLOBAL, form.origin))
        if (original) {
          draft.templates.splice(index, 1, restore(original))
        } else if (source) {
          // A rename: put the original back under its old name.
          draft.templates.splice(index, 1, restore(source))
        } else {
          draft.templates.splice(index, 1)
        }
      } else if (original) {
        draft.templates.push(restore(original))
        draft.templates.sort(byNameThenScope)
      }
      break
    }
  }
}

// ---- Validation ------------------------------------------------------------------------------

export interface Issue {
  ref: ChangeRef
  /** The change set path (`settings[server:lobby].set.reasons.default`, `templates.hacking.steps[0].step`). */
  path: string
  message: string
}

export function validateDraft(payload: Payload, draft: EditorDraft): Issue[] {
  const applied = new Set(draft.applied)
  return validateAll(payload, draft).filter(issue => !applied.has(refId(issue.ref)))
}

function validateAll(payload: Payload, draft: EditorDraft): Issue[] {
  const issues: Issue[] = []
  for (const [scope, edits] of Object.entries(draft.settings)) {
    for (const [key, edit] of Object.entries(edits)) {
      const field = settingField(payload, key)
      if (!field || !settingEditMatters(payload, scope, key, edit)) {
        continue
      }
      const ref: ChangeRef = { section: 'settings', scope, key }
      const base = `settings[${scope}].${edit.kind === 'set' ? 'set' : 'reset'}.${key}`
      if (!SCOPE_PATTERN.test(scope)) {
        issues.push({ ref, path: base, message: 'is not a scope (names are 1-64 characters of a-z, 0-9, _ and -)' })
      }
      if (field.level === 'GLOBAL' && scope !== GLOBAL) {
        issues.push({ ref, path: base, message: 'only the global scope can override this key' })
      }
      if (edit.kind !== 'set') {
        continue
      }
      if (field.secret) {
        if (typeof edit.value !== 'string') {
          issues.push({ ref, path: base, message: 'must be text' })
        } else if (edit.value !== '' && field.key.endsWith('url') && !/^https?:\/\/\S+$/.test(edit.value)) {
          issues.push({ ref, path: base, message: 'must be an http(s) URL, or empty' })
        }
        continue
      }
      for (const issue of validateSetting(field, edit.value)) {
        issues.push({ ref, path: issue.path ? `${base}.${issue.path}` : base, message: issue.message })
      }
    }
  }
  if (draft.publish) {
    const server = payload.settings?.servers.find(item => item.name === draft.publish!.server)
    const global = draft.settings[GLOBAL] ?? {}
    for (const key of draft.publish.keys) {
      const ref: ChangeRef = { section: 'publish', key }
      const path = `settings[global].publish.${key}`
      if (!server) {
        issues.push({ ref, path, message: `the server ${draft.publish.server} is unknown` })
      } else if (!(key in server.fileValues)) {
        issues.push({ ref, path, message: `${draft.publish.server} reported no file value for this key` })
      }
      if (key in global) {
        issues.push({ ref, path, message: 'is also set or reset in global: a key is only in one of set, reset and publish' })
      }
    }
  }
  for (const locale of draft.addedLocales) {
    if (!LOCALE_PATTERN.test(locale)) {
      issues.push({ ref: { section: 'messages', scope: GLOBAL, locale, key: '' }, path: `messages.${locale}`,
        message: 'a locale must look like en_US' })
    }
  }
  for (const [scope, locales] of Object.entries(draft.messages)) {
    for (const [locale, edits] of Object.entries(locales)) {
      for (const [key, edit] of Object.entries(edits)) {
        if (edit.kind !== 'set') {
          continue
        }
        const ref: ChangeRef = { section: 'messages', scope, locale, key }
        const path = `messages[${scope}][${locale}].set.${key}`
        if (!SCOPE_PATTERN.test(scope)) {
          issues.push({ ref, path, message: 'is not a scope' })
        }
        if (edit.text.trim() === '' || edit.text.length > MAX_MESSAGE) {
          issues.push({ ref, path, message: `must be 1-${MAX_MESSAGE} characters` })
        }
      }
    }
  }
  // Only templates the user touched: the payload's own are valid by construction. A variant is
  // also checked against its global definition, which may have changed since.
  const original = templateItems(payload)
  const globals = new Map(draft.templates.filter(form => form.variant === GLOBAL).map(form => [form.name, form]))
  draft.templates.forEach((form, index) => {
    const ref: ChangeRef = { section: 'templates', scope: form.variant, name: form.name }
    const prefix = templatePath(form.variant, form.name)
    if (form.variant !== GLOBAL) {
      const global = globals.get(form.name)
      if (!SCOPE_PATTERN.test(form.variant)) {
        issues.push({ ref, path: `${prefix}.name`, message: 'is not in a server or category scope' })
      }
      if (!global) {
        issues.push({ ref, path: `${prefix}.name`,
          message: 'its global template is deleted or renamed: restore it, or remove this variant' })
      } else if (kindOf(global) !== kindOf(form)) {
        issues.push({ ref, path: `${prefix}.type`,
          message: `a variant has its global template's type: ${kindOf(global)}` })
      }
      if (form.groups.length > 0) {
        issues.push({ ref, path: `${prefix}.groups`, message: 'a variant counts in its global template\'s groups; set them there' })
      }
    }
    const before = original.get(templateKey(form.variant, form.name))
    if (before && sameDefinition(before.definition, formToDefinition(form))) {
      return
    }
    const others = new Set(draft.templates.filter((other, at) => at !== index && other.variant === form.variant)
      .map(other => other.name))
    for (const issue of validateTemplate(form, others)) {
      issues.push({ ref, path: `${prefix}.${issue.path}`, message: issue.message })
    }
  })
  return issues
}

/** How many pending overrides changes each scope has (for the scope switcher). */
export function changesPerScope(items: ChangeItem[], section: Section): Map<string, number> {
  const counts = new Map<string, number>()
  for (const item of items) {
    if (item.section === section && item.scope) {
      counts.set(item.scope, (counts.get(item.scope) ?? 0) + 1)
    }
  }
  return counts
}
