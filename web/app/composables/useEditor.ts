import type { InjectionKey } from 'vue'
import {
  buildChangeSet, initialDraft, isEmptyChangeSet, listChanges, nextSequence, revertChange, validateDraft, refId
} from '#shared/editor/draft'
import type { ChangeRef, EditorDraft, Issue, TemplateDraft } from '#shared/editor/draft'
import { effectiveMessageOverrides, effectiveOverrides } from '#shared/editor/layers'
import { applyOutcome, refForResultChange, simulateResult, simulateSnapshot } from '#shared/editor/outcome'
import type { OutcomeEffect } from '#shared/editor/outcome'
import { rebaseDraft } from '#shared/editor/rebase'
import type { RebaseNote } from '#shared/editor/rebase'
import { PING_INTERVAL_MS, reconnectDelay } from '#shared/editor/saveFlow'
import { canonicalJson, jsonEqual, removeKey } from '#shared/json'
import { readPayload } from '#shared/protocol/format'
import { changeSetSchema, GLOBAL, payloadSchema, resultSchema } from '#shared/protocol/schema'
import type { ApplyResult, ChangeSet, JsonValue, Payload, Section, SettingField } from '#shared/protocol/schema'
import { openJson, sealJson } from '#shared/protocol/seal'

export interface EditorSession {
  /** Null in demo mode. */
  id: string | null
  /** Never stored anywhere; lives in memory for this page only. */
  key: Uint8Array | null
  payload: Payload
  /** The payload's revision (X-Revision); 1 for the first upload. */
  revision: number
  /** When the server drops the payload (epoch ms), if it said so. */
  expiresAt: number | null
}

export type SaveResult
  = | { kind: 'saved', code: string, expiresAt: number | null, changeSet: ChangeSet, live: boolean }
    | { kind: 'demo', code: string, changeSet: ChangeSet, sealedBytes: number, live: boolean }

export class SaveError extends Error {}

/** What the last refresh from the server did, for the notice under the header. */
export interface RefreshReport {
  revision: number
  at: number
  notes: RebaseNote[]
  /** Whether the user had something to look at (pending edits or flags). */
  notable: boolean
}

const DRAFT_PREFIX = 'sakurabans-editor-draft:'

interface StoredDraft {
  revision: number
  draft: EditorDraft
  /** The snapshot the draft was made against, so a newer one can be re-based onto. */
  base: Payload
}

function storageKey(session: EditorSession): string {
  return DRAFT_PREFIX + (session.id ?? 'demo')
}

function normalizeDraft(raw: Partial<EditorDraft>, revision: number): EditorDraft | null {
  if (typeof raw !== 'object' || raw === null || !Array.isArray(raw.templates)) {
    return null
  }
  // A draft kept by a version of this site before typed templates can't be read.
  if (!raw.templates.every(form => typeof form === 'object' && form !== null
    && (form.kind === 'preset' || form.kind === 'ladder'))) {
    return null
  }
  return {
    settings: raw.settings ?? {},
    publish: raw.publish ?? null,
    messages: raw.messages ?? {},
    addedLocales: raw.addedLocales ?? [],
    // A draft kept before scoped variants holds global definitions only.
    templates: raw.templates.map(form => ({ ...form, variant: typeof form.variant === 'string' ? form.variant : GLOBAL })),
    lastSequence: Number(raw.lastSequence) || 0,
    revision,
    stale: Array.isArray(raw.stale) ? raw.stale : [],
    applied: Array.isArray(raw.applied) ? raw.applied : []
  }
}

/** The stored draft for this session, re-based when the server has a newer snapshot. */
function restoreDraft(session: EditorSession): { draft: EditorDraft, notes: RebaseNote[] } | null {
  try {
    const raw = sessionStorage.getItem(storageKey(session))
    if (!raw) {
      return null
    }
    const stored = JSON.parse(raw) as Partial<StoredDraft>
    const revision = Number(stored.revision) || 1
    const draft = normalizeDraft(stored.draft ?? {}, revision)
    if (!draft) {
      return null
    }
    if (revision === session.revision) {
      return { draft, notes: [] }
    }
    const base = payloadSchema.safeParse(stored.base)
    if (revision > session.revision || !base.success) {
      return null
    }
    const rebased = rebaseDraft(base.data, session.payload, draft, session.revision)
    return { draft: rebased.draft, notes: rebased.notes }
  } catch {
    return null
  }
}

/**
 * The draft as kept in sessionStorage: a secret's new value is never written there (it lives in
 * this page's memory only and has to be entered again after a reload).
 */
export function withoutSecrets(draft: EditorDraft, base: Payload): EditorDraft {
  const secrets = new Set((base.settings?.fields ?? []).filter(field => field.secret).map(field => field.key))
  const settings: EditorDraft['settings'] = {}
  for (const [scope, edits] of Object.entries(draft.settings)) {
    settings[scope] = Object.fromEntries(Object.entries(edits)
      .filter(([key, edit]) => !(secrets.has(key) && edit.kind === 'set')))
  }
  return { ...draft, settings }
}

function writeStoredDraft(session: EditorSession, base: Payload, draft: EditorDraft) {
  try {
    const stored: StoredDraft = { revision: draft.revision, draft: withoutSecrets(draft, base), base }
    sessionStorage.setItem(storageKey(session), JSON.stringify(stored))
  } catch {
    // Storage full or disabled: the draft simply isn't kept.
  }
}

export function createEditorStore(session: EditorSession) {
  /** The snapshot being edited; replaced when the server sends a newer revision. */
  const payload = shallowRef<Payload>(session.payload)
  const restored = import.meta.client ? restoreDraft(session) : null
  const draft = reactive<EditorDraft>(restored?.draft ?? initialDraft(session.payload, session.revision))
  const changes = computed(() => listChanges(payload.value, draft))
  const restoredDraft = ref(restored !== null && changes.value.length > 0)
  // Sequence 1 here: the real one is taken at save time and isn't part of the comparison.
  const changeSet = computed(() => buildChangeSet(payload.value, draft, 1))
  /** Client-side problems: they block saving. */
  const issues = computed<Issue[]>(() => validateDraft(payload.value, draft))
  /** Problems the plugin reported in a refused result, shown next to fields until the next save. */
  const serverIssues = ref<Issue[]>([])
  const fieldIssues = computed<Issue[]>(() => [...issues.value, ...serverIssues.value])
  const fingerprint = computed(() => canonicalJson(changeSet.value))
  const lastSaved = ref<string>(canonicalJson(buildChangeSet(payload.value, initialDraft(payload.value), 1)))
  const lastResult = ref<SaveResult | null>(null)
  const unsaved = computed(() => fingerprint.value !== lastSaved.value)
  const lastEffect = ref<OutcomeEffect | null>(null)
  const refreshReport = ref<RefreshReport | null>(restored && restored.notes.length > 0
    ? { revision: session.revision, at: Date.now(), notes: restored.notes, notable: true }
    : null)
  const refreshFailed = ref(false)
  /** Settings and message overrides per scope as they would be after saving (for layer views). */
  const overrides = computed(() => effectiveOverrides(payload.value, draft))
  const messageOverrides = computed(() => effectiveMessageOverrides(payload.value, draft))
  /** Scopes typed in by the user that have no override yet. */
  const addedScopes = ref<string[]>([])
  /** Whether a secret was typed in this page (its value is never stored, so a reload loses it). */
  const secretTyped = ref(false)

  /** Sections in the draft that the current snapshot doesn't have (a permission changed). */
  const orphanSections = computed<Section[]>(() => {
    const present = payload.value.meta.sections
    const sections: Section[] = []
    if (!present.includes('settings') && (Object.values(draft.settings).some(edits => Object.keys(edits).length > 0) || draft.publish)) {
      sections.push('settings')
    }
    if (!present.includes('messages') && Object.values(draft.messages).some(locales => Object.values(locales).some(edits => Object.keys(edits).length > 0))) {
      sections.push('messages')
    }
    if (!present.includes('templates') && draft.templates.length > 0) {
      sections.push('templates')
    }
    return sections
  })

  function isStale(section: Section): boolean {
    return draft.stale.includes(section)
  }

  // ---- Live socket (protocol section 9) ----
  /** 'connecting' while the browser's own socket isn't up (first connect or reconnect). */
  const socketState = ref<'connecting' | 'up' | 'down'>(session.id === null ? 'up' : 'connecting')
  const pluginConnected = ref(session.id === null)
  const live = computed(() => socketState.value === 'up' && pluginConnected.value)
  const resultListeners = new Set<(code: string) => void>()
  const socketListeners = new Set<(up: boolean) => void>()
  let socket: WebSocket | null = null
  let attempt = 0
  let reconnectTimer: ReturnType<typeof setTimeout> | undefined
  let pingTimer: ReturnType<typeof setInterval> | undefined
  let closedForGood = false

  function setSocketUp(up: boolean) {
    const was = socketState.value === 'up'
    socketState.value = up ? 'up' : attempt === 0 ? 'connecting' : 'down'
    if (was !== up) {
      socketListeners.forEach(listener => listener(up))
    }
  }

  function connect() {
    if (session.id === null || closedForGood) {
      return
    }
    const scheme = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const ws = new WebSocket(`${scheme}//${window.location.host}/api/sessions/${encodeURIComponent(session.id)}/socket?role=browser`)
    socket = ws
    ws.addEventListener('open', () => {
      attempt = 0
      clearInterval(pingTimer)
      pingTimer = setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) {
          ws.send('{"type":"ping"}')
        }
      }, PING_INTERVAL_MS)
    })
    ws.addEventListener('message', (event) => {
      let frame: { type?: unknown, plugin?: unknown, connected?: unknown, code?: unknown }
      try {
        frame = JSON.parse(String(event.data)) as typeof frame
      } catch {
        return
      }
      if (frame.type === 'hello') {
        pluginConnected.value = frame.plugin === true
        setSocketUp(true)
      } else if (frame.type === 'plugin') {
        pluginConnected.value = frame.connected === true
      } else if (frame.type === 'result' && typeof frame.code === 'string') {
        const code = frame.code
        resultListeners.forEach(listener => listener(code))
        void otherResult(code)
      }
    })
    ws.addEventListener('close', (event) => {
      clearInterval(pingTimer)
      if (socket !== ws) {
        return
      }
      socket = null
      pluginConnected.value = false
      if (event.code === 4004) {
        // The session expired or is unknown: no point in retrying.
        closedForGood = true
      }
      attempt++
      setSocketUp(false)
      if (!closedForGood) {
        reconnectTimer = setTimeout(connect, reconnectDelay(attempt - 1))
      }
    })
  }

  function disconnect() {
    closedForGood = true
    clearTimeout(reconnectTimer)
    clearInterval(pingTimer)
    socket?.close(1000, 'editor closed')
    socket = null
  }

  function onResultNotice(listener: (code: string) => void): () => void {
    resultListeners.add(listener)
    return () => resultListeners.delete(listener)
  }

  function onSocketChange(listener: (up: boolean) => void): () => void {
    socketListeners.add(listener)
    return () => socketListeners.delete(listener)
  }

  async function fetchResult(code: string): Promise<ApplyResult | null> {
    if (session.id === null || session.key === null) {
      return null
    }
    const response = await fetch(`/api/changes/${encodeURIComponent(code)}/result`, {
      credentials: 'omit', cache: 'no-store', referrerPolicy: 'no-referrer'
    })
    if (response.status === 404) {
      return null
    }
    if (!response.ok) {
      throw new Error(`result ${response.status}`)
    }
    const value = await openJson(session.key, 'result', new Uint8Array(await response.arrayBuffer()))
    const parsed = resultSchema.safeParse(value)
    return parsed.success ? parsed.data : null
  }

  // ---- Fresh snapshots (protocol section 10) ----
  /** Codes whose result a save dialog is following, or that were already handled. */
  const followed = new Set<string>()
  const handled = new Set<string>()
  /** A demo "server": the snapshot after the simulated apply. */
  let demoSnapshot: Payload | null = null

  async function fetchPayload(): Promise<{ payload: Payload, revision: number } | null> {
    if (session.id === null || session.key === null) {
      return demoSnapshot ? { payload: demoSnapshot, revision: draft.revision + 1 } : null
    }
    const response = await fetch(`/api/sessions/${encodeURIComponent(session.id)}`, {
      credentials: 'omit', cache: 'no-store', referrerPolicy: 'no-referrer'
    })
    if (!response.ok) {
      return null
    }
    const value = await openJson(session.key, 'payload', new Uint8Array(await response.arrayBuffer()))
    const read = readPayload(value)
    if (!read.ok) {
      return null
    }
    return { payload: read.payload, revision: Number(response.headers.get('x-revision')) || 1 }
  }

  /** Loads the newer snapshot and re-bases the draft on it. False when it couldn't be loaded. */
  async function refresh(target: number, hints: { conflicts?: ReadonlySet<string>, applied?: ReadonlySet<string> } = {}): Promise<boolean> {
    if (target <= draft.revision) {
      return true
    }
    const fresh = await fetchPayload().catch(() => null)
    if (!fresh || fresh.revision <= draft.revision) {
      refreshFailed.value = fresh === null
      return fresh !== null
    }
    const result = rebaseDraft(payload.value, fresh.payload, toRaw(draft) as EditorDraft, fresh.revision, hints)
    // Replace the model in place: components stay mounted, so focus and caret stay where they are.
    payload.value = fresh.payload
    for (const key of Object.keys(result.draft) as Array<keyof EditorDraft>) {
      (draft as Record<keyof EditorDraft, unknown>)[key] = result.draft[key]
    }
    serverIssues.value = []
    refreshFailed.value = false
    lastSaved.value = fingerprint.value
    const pending = listChanges(payload.value, draft).length > 0
    refreshReport.value = {
      revision: fresh.revision,
      at: Date.now(),
      notes: result.notes,
      notable: pending || result.notes.some(note => note.kind !== 'applied')
    }
    return true
  }

  function resultHints(result: ApplyResult): { conflicts: Set<string>, applied: Set<string> } {
    const refs = (test: (action: string) => boolean) => new Set(result.changes.filter(change => test(change.action))
      .map(refForResultChange).filter(ref => ref !== null).map(ref => refId(ref!)))
    return { conflicts: refs(action => action === 'conflict'), applied: refs(action => action !== 'conflict' && action !== 'unchanged') }
  }

  /** A result of one of this tab's saves (from the save dialog). */
  async function handleResult(code: string, changes: ChangeSet, result: ApplyResult): Promise<OutcomeEffect> {
    handled.add(code)
    const effect = applyOutcome(payload.value, draft, changes, result)
    serverIssues.value = effect.issues
    lastEffect.value = effect
    lastSaved.value = fingerprint.value
    refreshFailed.value = result.outcome === 'applied' && result.payloadRevision === null
    if (effect.refreshTo !== null) {
      await refresh(effect.refreshTo, resultHints(result))
    }
    return effect
  }

  function follow(code: string): () => void {
    followed.add(code)
    return () => followed.delete(code)
  }

  /** A `result` notice for a code this tab isn't following (another tab, a colleague, a command). */
  async function otherResult(code: string) {
    const upper = code.toUpperCase()
    if (followed.has(upper) || handled.has(upper)) {
      return
    }
    handled.add(upper)
    try {
      const result = await fetchResult(upper)
      if (result?.outcome === 'applied') {
        if (result.payloadRevision === null) {
          refreshFailed.value = true
        } else {
          await refresh(result.payloadRevision, resultHints(result))
        }
      }
    } catch {
      handled.delete(upper)
    }
  }

  /** Demo mode: the result is made up locally, and so is the snapshot that follows it. */
  function demoResult(changes: ChangeSet): ApplyResult {
    demoSnapshot = simulateSnapshot(payload.value, draft)
    return simulateResult(payload.value, draft, changes, draft.revision + 1)
  }

  let timer: ReturnType<typeof setTimeout> | undefined
  watch(draft, () => {
    clearTimeout(timer)
    timer = setTimeout(() => writeStoredDraft(session, payload.value, toRaw(draft) as EditorDraft), 300)
  }, { deep: true })

  // ---- Settings (per scope) ----
  function payloadOverride(scope: string, key: string): JsonValue | undefined {
    return payload.value.settings?.scopes.find(item => item.scope === scope)?.overrides[key]
  }

  function scopeEdits(scope: string) {
    return (draft.settings[scope] ??= {})
  }

  /** Sets the override of `field` in `scope` (no edit when it equals the scope's current one). */
  function setSetting(scope: string, field: SettingField, value: JsonValue) {
    const current = payloadOverride(scope, field.key)
    if (field.secret) {
      secretTyped.value = true
    }
    if (!field.secret && current !== undefined && jsonEqual(value, current)) {
      removeKey(scopeEdits(scope), field.key)
    } else {
      scopeEdits(scope)[field.key] = { kind: 'set', value }
    }
  }

  /** Starts overriding in `scope`, from the value the scope inherits (secrets start empty). */
  function overrideHere(scope: string, field: SettingField, inherited: JsonValue | undefined) {
    const start = field.secret ? '' : inherited !== undefined ? JSON.parse(JSON.stringify(inherited)) as JsonValue : field.default
    const current = payloadOverride(scope, field.key)
    if (current !== undefined && !field.secret) {
      removeKey(scopeEdits(scope), field.key)
    } else {
      scopeEdits(scope)[field.key] = { kind: 'set', value: start }
    }
  }

  /** Stops overriding in `scope`: a reset when the payload has an override there, else drop the edit. */
  function removeOverride(scope: string, field: SettingField) {
    if (payloadOverride(scope, field.key) !== undefined) {
      scopeEdits(scope)[field.key] = { kind: 'reset' }
    } else {
      removeKey(scopeEdits(scope), field.key)
    }
  }

  function undoSetting(scope: string, key: string) {
    removeKey(scopeEdits(scope), key)
  }

  function setPublish(server: string, keys: string[]) {
    draft.publish = keys.length > 0 ? { server, keys: [...keys] } : null
  }

  function addScope(scope: string) {
    if (scope !== GLOBAL && !addedScopes.value.includes(scope)) {
      addedScopes.value = [...addedScopes.value, scope]
    }
  }

  // ---- Messages (per scope and locale) ----
  function payloadMessage(scope: string, locale: string, key: string): string | undefined {
    return payload.value.messages?.scopes.find(item => item.scope === scope && item.locale === locale)?.overrides[key]
  }

  function messageEdits(scope: string, locale: string) {
    return ((draft.messages[scope] ??= {})[locale] ??= {})
  }

  function setMessage(scope: string, locale: string, key: string, text: string) {
    if (payloadMessage(scope, locale, key) === text) {
      removeKey(messageEdits(scope, locale), key)
    } else {
      messageEdits(scope, locale)[key] = { kind: 'set', text }
    }
  }

  function removeMessageOverride(scope: string, locale: string, key: string) {
    if (payloadMessage(scope, locale, key) !== undefined) {
      messageEdits(scope, locale)[key] = { kind: 'reset' }
    } else {
      removeKey(messageEdits(scope, locale), key)
    }
  }

  function undoMessage(scope: string, locale: string, key: string) {
    removeKey(messageEdits(scope, locale), key)
  }

  function addLocale(locale: string) {
    if (!draft.addedLocales.includes(locale)) {
      draft.addedLocales.push(locale)
    }
  }

  function removeAddedLocale(locale: string) {
    draft.addedLocales = draft.addedLocales.filter(item => item !== locale)
    for (const locales of Object.values(draft.messages)) {
      removeKey(locales, locale)
    }
  }

  // ---- Templates ----
  function addTemplate(form: TemplateDraft) {
    draft.templates.push(form)
  }

  function removeTemplate(form: TemplateDraft) {
    const index = draft.templates.indexOf(form)
    if (index >= 0) {
      draft.templates.splice(index, 1)
    }
  }

  function revert(ref: ChangeRef) {
    revertChange(payload.value, draft, ref)
  }

  function discardAll() {
    Object.assign(draft, {
      ...initialDraft(payload.value, draft.revision),
      lastSequence: draft.lastSequence,
      stale: [...draft.stale],
      applied: [...draft.applied]
    })
  }

  async function save(): Promise<SaveResult> {
    if (issues.value.length > 0) {
      throw new SaveError('Fix the errors first.')
    }
    const sequence = nextSequence(draft, Date.now())
    const parsed = changeSetSchema.parse(buildChangeSet(payload.value, draft, sequence))
    if (isEmptyChangeSet(parsed)) {
      throw new SaveError('There is nothing to save.')
    }
    const key = session.key ?? crypto.getRandomValues(new Uint8Array(32))
    const blob = await sealJson(key, 'changes', parsed)
    const liveNow = live.value
    let result: SaveResult
    if (session.id === null) {
      result = { kind: 'demo', code: `DEMO${String(sequence % 1_000_000).padStart(6, '2')}`.slice(0, 10), changeSet: parsed, sealedBytes: blob.length, live: true }
    } else {
      let response: Response
      try {
        response = await fetch(`/api/sessions/${encodeURIComponent(session.id)}/changes`, {
          method: 'POST',
          body: new Blob([new Uint8Array(blob)], { type: 'application/octet-stream' }),
          headers: { 'content-type': 'application/octet-stream' },
          credentials: 'omit',
          cache: 'no-store',
          referrerPolicy: 'no-referrer'
        })
      } catch {
        throw new SaveError('The editor site could not be reached. Check your connection and try again.')
      }
      if (response.status !== 201) {
        const body = await response.json().catch(() => null) as { message?: string } | null
        const reason = response.status === 404
          ? 'This editor session has expired. Run /sb editor again to start a new one.'
          : response.status === 413
            ? 'The changes are too large for this editor site.'
            : response.status === 429
              ? 'Too many saves from your address. Wait a few minutes and try again.'
              : body?.message ?? `The editor site answered ${response.status}.`
        throw new SaveError(reason)
      }
      const { code } = await response.json() as { code: string }
      const expires = Number(response.headers.get('x-expires-at'))
      result = { kind: 'saved', code, expiresAt: Number.isFinite(expires) && expires > 0 ? expires : null, changeSet: parsed, live: liveNow }
    }
    draft.lastSequence = sequence
    serverIssues.value = []
    lastSaved.value = canonicalJson(buildChangeSet(payload.value, draft, 1))
    lastResult.value = result
    return result
  }

  return {
    session,
    payload,
    draft,
    restoredDraft,
    changes,
    changeSet,
    issues,
    serverIssues,
    fieldIssues,
    unsaved,
    lastResult,
    lastEffect,
    refreshReport,
    refreshFailed,
    overrides,
    messageOverrides,
    addedScopes,
    secretTyped,
    addScope,
    overrideHere,
    removeOverride,
    setPublish,
    removeMessageOverride,
    orphanSections,
    isStale,
    live,
    socketState,
    pluginConnected,
    connect,
    disconnect,
    onResultNotice,
    onSocketChange,
    fetchResult,
    follow,
    demoResult,
    handleResult,
    refresh,
    setSetting,
    undoSetting,
    setMessage,
    undoMessage,
    addLocale,
    removeAddedLocale,
    addTemplate,
    removeTemplate,
    revert,
    discardAll,
    save
  }
}

export type EditorStore = ReturnType<typeof createEditorStore>

export const EDITOR_STORE: InjectionKey<EditorStore> = Symbol('sakurabans-editor')

export function useEditor(): EditorStore {
  const store = inject(EDITOR_STORE)
  if (!store) {
    throw new Error('useEditor() outside the editor')
  }
  return store
}

/** Issues of one change target, for showing next to its field. */
export function issuesFor(issues: Issue[], match: (ref: ChangeRef) => boolean): Issue[] {
  return issues.filter(issue => match(issue.ref))
}
