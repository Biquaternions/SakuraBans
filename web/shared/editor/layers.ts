// Layered settings and messages (protocol 5.1 and 5.3). For a server the value of a key is the
// first of: its server scope, its categories in order, global, its file, the default. The
// editor resolves exactly as the plugin does, over the payload with the draft's edits applied,
// so editing scopes.categories moves other servers' effective values at once.

import type { EditorDraft } from './draft'
import { removeKey } from '../json'
import type { JsonValue, Payload, ServerInfo, SettingField } from '../protocol/schema'
import { GLOBAL } from '../protocol/schema'

export const CATEGORIES_KEY = 'scopes.categories'

export type Layer
  = | { kind: 'server', scope: string }
    | { kind: 'category', scope: string }
    | { kind: 'global' }
    | { kind: 'file', server: string }
    | { kind: 'default' }

export type Overrides = Map<string, Record<string, JsonValue>>

export function scopeOf(kind: 'server' | 'category', name: string): string {
  return `${kind}:${name}`
}

export function scopeName(scope: string): string {
  const colon = scope.indexOf(':')
  return colon < 0 ? scope : scope.slice(colon + 1)
}

export function scopeKind(scope: string): 'global' | 'server' | 'category' {
  return scope === GLOBAL ? 'global' : scope.startsWith('server:') ? 'server' : 'category'
}

export function layerText(layer: Layer): string {
  switch (layer.kind) {
    case 'server':
      return `server ${scopeName(layer.scope)}`
    case 'category':
      return `category ${scopeName(layer.scope)}`
    case 'global':
      return 'global'
    case 'file':
      return layer.server ? `${layer.server}'s file` : 'the server\'s file'
    case 'default':
      return 'default'
  }
}

/** The settings overrides per scope, as they would be after the draft is applied. */
export function effectiveOverrides(payload: Payload, draft: EditorDraft): Overrides {
  const result: Overrides = new Map()
  const fields = new Map((payload.settings?.fields ?? []).map(field => [field.key, field]))
  for (const scope of payload.settings?.scopes ?? []) {
    result.set(scope.scope, { ...scope.overrides })
  }
  if (!result.has(GLOBAL)) {
    result.set(GLOBAL, {})
  }
  for (const [scope, edits] of Object.entries(draft.settings)) {
    const overrides = { ...(result.get(scope) ?? {}) }
    for (const [key, edit] of Object.entries(edits)) {
      if (edit.kind === 'reset') {
        removeKey(overrides, key)
      } else {
        // A secret's value never shows up in a view: only whether it is set.
        overrides[key] = fields.get(key)?.secret ? edit.value !== '' : edit.value
      }
    }
    result.set(scope, overrides)
  }
  if (draft.publish) {
    const server = (payload.settings?.servers ?? []).find(item => item.name === draft.publish!.server)
    const global = result.get(GLOBAL)!
    for (const key of draft.publish.keys) {
      if (server && key in server.fileValues) {
        global[key] = server.fileValues[key]!
      }
    }
  }
  return result
}

function asCategories(value: JsonValue | undefined): Record<string, string[]> {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    return {}
  }
  const out: Record<string, string[]> = {}
  for (const [category, servers] of Object.entries(value)) {
    out[category] = Array.isArray(servers) ? servers.filter((item): item is string => typeof item === 'string') : []
  }
  return out
}

/** The effective scopes.categories for a server: the global override, else its own file value. */
export function effectiveCategories(overrides: Overrides, server: Pick<ServerInfo, 'fileValues'> | null): Record<string, string[]> {
  const global = overrides.get(GLOBAL) ?? {}
  if (CATEGORIES_KEY in global) {
    return asCategories(global[CATEGORIES_KEY])
  }
  return asCategories(server?.fileValues[CATEGORIES_KEY])
}

/**
 * A server's categories in order: those of the effective scopes.categories that list it, in
 * declaration order, then its own `categories` that aren't among them. An unnamed server only
 * has its own.
 */
export function categoriesOf(server: Pick<ServerInfo, 'name' | 'categories' | 'fileValues'>, overrides: Overrides): string[] {
  const out: string[] = []
  if (server.name !== '') {
    for (const [category, servers] of Object.entries(effectiveCategories(overrides, server))) {
      if (servers.includes(server.name)) {
        out.push(category)
      }
    }
  }
  for (const category of server.categories) {
    if (!out.includes(category)) {
      out.push(category)
    }
  }
  return out
}

export interface Resolved {
  /** Undefined when the layer's value isn't known (another server's file). */
  value: JsonValue | undefined
  layer: Layer
  /** Other categories of the server that also set the key, which lose to the first. */
  shadowed: string[]
}

/** A server the editor knows only by name (typed in, or from lookups). */
export function unknownServer(name: string): ServerInfo {
  return { id: '', name, categories: [], platform: '', pluginVersion: '', lastSeenAt: 0, fileValues: {} }
}

/**
 * The effective value of `key` on `server`. `skipServerScope` resolves what the server scope
 * would inherit (everything below it).
 */
export function resolveSetting(field: SettingField, server: ServerInfo, overrides: Overrides,
  skipServerScope = false): Resolved {
  const key = field.key
  if (server.name !== '' && !skipServerScope) {
    const own = overrides.get(scopeOf('server', server.name))
    if (own && key in own) {
      return { value: own[key], layer: { kind: 'server', scope: scopeOf('server', server.name) }, shadowed: [] }
    }
  }
  const categories = categoriesOf(server, overrides)
  const setting = categories.filter(category => key in (overrides.get(scopeOf('category', category)) ?? {}))
  if (setting.length > 0) {
    const first = scopeOf('category', setting[0]!)
    return { value: overrides.get(first)![key], layer: { kind: 'category', scope: first }, shadowed: setting.slice(1) }
  }
  const global = overrides.get(GLOBAL) ?? {}
  if (key in global) {
    return { value: global[key], layer: { kind: 'global' }, shadowed: [] }
  }
  if (key in server.fileValues) {
    return { value: server.fileValues[key], layer: { kind: 'file', server: server.name }, shadowed: [] }
  }
  if (server.id === '') {
    return { value: undefined, layer: { kind: 'file', server: server.name }, shadowed: [] }
  }
  return { value: field.secret ? false : field.default, layer: { kind: 'default' }, shadowed: [] }
}

export interface Inherited extends Resolved {
  /** For global: the servers whose file value differs from the shown one. */
  differing: string[]
}

/** The servers of the payload; the uploading server first (protocol 5.2). */
export function servers(payload: Payload): ServerInfo[] {
  return payload.settings?.servers ?? []
}

export function uploadingServer(payload: Payload): ServerInfo | null {
  const list = servers(payload)
  return list.find(server => server.name === payload.meta.server) ?? list[0] ?? null
}

function sameValue(a: JsonValue | undefined, b: JsonValue | undefined): boolean {
  return JSON.stringify(a) === JSON.stringify(b)
}

/** What a scope would inherit for `key` if it had no override itself. */
export function inheritedValue(payload: Payload, field: SettingField, scope: string, overrides: Overrides): Inherited {
  const key = field.key
  const uploader = uploadingServer(payload)
  const fileLayer = (): Inherited => {
    const value = uploader && key in uploader.fileValues ? uploader.fileValues[key] : (field.secret ? false : field.default)
    const differing = servers(payload)
      .filter(server => key in server.fileValues && !sameValue(server.fileValues[key], value))
      .map(server => server.name || server.id)
    return { value, layer: uploader ? { kind: 'file', server: uploader.name } : { kind: 'default' }, shadowed: [], differing }
  }
  switch (scopeKind(scope)) {
    case 'global':
      return fileLayer()
    case 'category': {
      const global = overrides.get(GLOBAL) ?? {}
      return key in global
        ? { value: global[key], layer: { kind: 'global' }, shadowed: [], differing: [] }
        : fileLayer()
    }
    case 'server': {
      const name = scopeName(scope)
      const server = servers(payload).find(item => item.name === name) ?? unknownServer(name)
      return { ...resolveSetting(field, server, overrides, true), differing: [] }
    }
  }
}

// ---- Messages ----

/** locale → key → text, per scope, after the draft. */
export type MessageOverrides = Map<string, Map<string, Record<string, string>>>

export function effectiveMessageOverrides(payload: Payload, draft: EditorDraft): MessageOverrides {
  const result: MessageOverrides = new Map()
  const get = (scope: string, locale: string) => {
    let byLocale = result.get(scope)
    if (!byLocale) {
      byLocale = new Map()
      result.set(scope, byLocale)
    }
    let texts = byLocale.get(locale)
    if (!texts) {
      texts = {}
      byLocale.set(locale, texts)
    }
    return texts
  }
  for (const entry of payload.messages?.scopes ?? []) {
    Object.assign(get(entry.scope, entry.locale), entry.overrides)
  }
  for (const [scope, locales] of Object.entries(draft.messages)) {
    for (const [locale, edits] of Object.entries(locales)) {
      const texts = get(scope, locale)
      for (const [key, edit] of Object.entries(edits)) {
        if (edit.kind === 'reset') {
          removeKey(texts, key)
        } else {
          texts[key] = edit.text
        }
      }
    }
  }
  return result
}

export interface ResolvedMessage {
  /** Undefined when only that server's language file knows it. */
  text: string | undefined
  layer: Layer
  shadowed: string[]
}

/** The uploading server's file text for a locale, then en_US's, then the shipped default. */
export function fileText(payload: Payload, locale: string, key: string): string {
  const locales = payload.messages?.locales ?? []
  const field = payload.messages?.fields.find(item => item.key === key)
  return locales.find(item => item.locale === locale)?.fileTexts[key]
    ?? locales.find(item => item.locale === 'en_US')?.fileTexts[key]
    ?? field?.default
    ?? ''
}

/**
 * The text of `key` in `locale` on `server` (null: the uploading server). Categories come from
 * the settings layers when the payload has them.
 */
export function resolveMessage(payload: Payload, messages: MessageOverrides, settings: Overrides,
  server: ServerInfo | null, locale: string, key: string, skipServerScope = false): ResolvedMessage {
  const at = (scope: string) => messages.get(scope)?.get(locale)
  const target = server ?? uploadingServer(payload)
  if (target && target.name !== '' && !skipServerScope) {
    const own = at(scopeOf('server', target.name))
    if (own && key in own) {
      return { text: own[key], layer: { kind: 'server', scope: scopeOf('server', target.name) }, shadowed: [] }
    }
  }
  const categories = target ? categoriesOf(target, settings) : []
  const setting = categories.filter(category => key in (at(scopeOf('category', category)) ?? {}))
  if (setting.length > 0) {
    const first = scopeOf('category', setting[0]!)
    return { text: at(first)![key], layer: { kind: 'category', scope: first }, shadowed: setting.slice(1) }
  }
  const global = at(GLOBAL) ?? {}
  if (key in global) {
    return { text: global[key], layer: { kind: 'global' }, shadowed: [] }
  }
  const uploader = uploadingServer(payload)
  const isUploader = !target || !uploader || target === uploader || target.name === payload.meta.server
  return {
    text: isUploader ? fileText(payload, locale, key) : undefined,
    layer: { kind: 'file', server: target?.name ?? payload.meta.server },
    shadowed: []
  }
}

/** What a messages scope would inherit for `key` in `locale`. */
export function inheritedMessage(payload: Payload, messages: MessageOverrides, settings: Overrides,
  scope: string, locale: string, key: string): ResolvedMessage {
  switch (scopeKind(scope)) {
    case 'global':
      return { text: fileText(payload, locale, key), layer: { kind: 'file', server: payload.meta.server }, shadowed: [] }
    case 'category': {
      const global = messages.get(GLOBAL)?.get(locale) ?? {}
      return key in global
        ? { text: global[key], layer: { kind: 'global' }, shadowed: [] }
        : { text: fileText(payload, locale, key), layer: { kind: 'file', server: payload.meta.server }, shadowed: [] }
    }
    case 'server': {
      const name = scopeName(scope)
      const server = servers(payload).find(item => item.name === name) ?? unknownServer(name)
      return resolveMessage(payload, messages, settings, server, locale, key, true)
    }
  }
}
