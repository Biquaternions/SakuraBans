// Templates in the editor: an editable form model, its conversion to and from the protocol's
// definition (protocol section 5.4), validation and the summary line.
//
// A template has a kind. A preset (`type: ban|mute|warn|kick`) fills in one
// punishment of its type and may fix its duration. A ladder (`type: ladder`) holds the
// escalation: steps that each name their own type, or `none` (no punishment, only actions).
// The definition is the templates.yml tree as the plugin writes it (TemplateFiles.tree):
// formToDefinition writes the same keys, in the same order, with the same canonical texts, so an
// untouched template comes out byte for byte as it came in.

import { checkDuration, describeDuration, formatCompact, isPermanentToken, parseDuration } from '../duration'
import { canonicalJson } from '../json'
import { PLAYER_COUNT_TAGS } from './countTags'
import type { JsonValue, PunishmentType, TemplateDefinition, TemplateDisplay, TemplateKind } from '../protocol/schema'
import { SCOPE_PATTERN } from '../protocol/schema'
import {
  actionProblem, IDENTIFIER_HINT, isIdentifier, MAX_ACTIONS, MAX_GROUPS, MAX_MESSAGE, MAX_REASON,
  MAX_STEPS, MAX_WEIGHT
} from './limits'

export type DurationMode = 'open' | 'permanent' | 'fixed'

/**
 * A preset's or a step's duration. On a preset, open means the command decides; on a step, the
 * type's default (a bare `warn` gets `warnings.expire-after`; kicks and `none` are always open).
 * `length` is kept while another mode is picked, so switching back restores it.
 */
export interface DurationForm {
  mode: DurationMode
  length: string
}

export interface TemplateStepForm {
  /** Null: no punishment, only actions (`none`). */
  type: PunishmentType | null
  duration: DurationForm
  /** Empty: the template's reason. */
  reason: string
  /** Empty: the template's message (or the network's). */
  message: string
  actions: string[]
  /** Keys of a step map this site doesn't know, kept as they came. */
  extra: Record<string, JsonValue>
}

export interface TemplateGroupForm {
  name: string
  weight: number
}

interface CommonForm {
  name: string
  reason: string
  ip: boolean
  shadow: boolean
  /** Empty for "scopes.default of the server". */
  scope: string
  permissionRequired: boolean
  actions: string[]
  groups: TemplateGroupForm[]
  /** Empty: the network's message for the punishment's type. */
  message: string
  /** How the mute notice and the warning reach an online player; chat by default. */
  display: TemplateDisplay
  /** Its punishments are silent unless the command hides them further. */
  silent: boolean
  /** Keys this site doesn't know (or that don't belong to this kind), kept as they came. */
  extra: Record<string, JsonValue>
}

export interface PresetForm extends CommonForm {
  kind: 'preset'
  type: PunishmentType
  duration: DurationForm
}

export interface LadderForm extends CommonForm {
  kind: 'ladder'
  steps: TemplateStepForm[]
  /** Empty for none. */
  expiry: string
  addressShared: boolean
}

export type TemplateForm = PresetForm | LadderForm

export const PUNISHMENT_TYPES: readonly PunishmentType[] = ['ban', 'mute', 'warn', 'kick']
export const TEMPLATE_KINDS: readonly TemplateKind[] = ['ban', 'mute', 'warn', 'kick', 'ladder']
export const TEMPLATE_DISPLAYS: readonly TemplateDisplay[] = ['chat', 'dialog']

export const NONE = 'none'
const PERMANENT = 'permanent'

/** Keys of a definition, in the order templates.yml writes them (TemplateFiles.KEYS). */
const PRESET_KEYS = new Set(['type', 'ip', 'shadow', 'scope', 'reason', 'duration',
  'permission-required', 'actions', 'groups', 'message', 'display', 'silent'])
const LADDER_KEYS = new Set(['type', 'ip', 'shadow', 'scope', 'reason', 'steps', 'expiry',
  'address-shared', 'permission-required', 'actions', 'groups', 'message', 'display', 'silent'])
const STEP_KEYS = new Set(['step', 'reason', 'message', 'actions'])

/** Keys of the format before typed templates, with what replaced them (TemplateFiles.REPLACED). */
export const REPLACED_KEYS: Readonly<Record<string, string>> = {
  'ladder': 'use \'type: ladder\' and \'steps\' (each step names its type: warn, kick, mute 1h, ban 7d, none)',
  'ladder-expiry': 'use \'expiry\'',
  'step-actions': 'use \'actions\' inside the step ({step: ..., actions: [...]})',
  'step-messages': 'use \'message\' inside the step ({step: ..., message: ...})'
}

/** Keys that belong to the other kind, with the plugin's error for them. */
const OTHER_KIND: Readonly<Record<string, { preset: string | null, ladder: string | null }>> = {
  'duration': { preset: null, ladder: 'a ladder\'s steps have the durations' },
  'steps': { preset: 'only a ladder has steps (type: ladder)', ladder: null },
  'expiry': { preset: 'only a ladder has an expiry', ladder: null },
  'address-shared': { preset: 'only a ladder shares progress', ladder: null }
}

/** Placeholders of template actions (templates.yml comment). */
export const TEMPLATE_ACTION_PLACEHOLDERS = ['victim', 'uuid', 'address', 'id', 'operator',
  'reason', 'template', 'step', 'duration'] as const

/** The network message a template's or step's message replaces, by type (lang/en_US.yml). */
export const NETWORK_MESSAGE_KEYS: Record<PunishmentType, string> = {
  ban: 'screen.ban',
  kick: 'screen.kick',
  mute: 'victim.muted',
  warn: 'victim.warned'
}

/** Placeholders of those messages when the payload has no messages section (lang/en_US.yml). */
export const FALLBACK_MESSAGE_PLACEHOLDERS: Record<PunishmentType, readonly string[]> = {
  ban: ['victim', 'created', 'reason', 'operator', 'duration', 'remaining', 'expires', 'id', 'display_id', 'scope', 'type', 'server', ...PLAYER_COUNT_TAGS],
  kick: ['victim', 'created', 'reason', 'operator', 'duration', 'remaining', 'expires', 'id', 'display_id', 'scope', 'type', 'server', ...PLAYER_COUNT_TAGS],
  mute: ['operator', 'remaining', 'reason', 'duration', ...PLAYER_COUNT_TAGS],
  warn: ['operator', 'remaining', 'reason', ...PLAYER_COUNT_TAGS]
}

export const TYPE_LABEL: Record<PunishmentType, string> = { ban: 'Ban', mute: 'Mute', warn: 'Warn', kick: 'Kick' }
export const KIND_LABEL: Record<TemplateKind, string> = {
  ban: 'Ban preset', mute: 'Mute preset', warn: 'Warn preset', kick: 'Kick preset', ladder: 'Ladder'
}

/** The definition's `type` of a form. */
export function kindOf(form: TemplateForm): TemplateKind {
  return form.kind === 'ladder' ? 'ladder' : form.type
}

// ---- Durations ------------------------------------------------------------------------------

export function openDuration(length = ''): DurationForm {
  return { mode: 'open', length }
}

/** The modes a type allows: a preset's, or a step's (`step`), which are fewer. */
export function durationModes(type: PunishmentType | null, step: boolean): DurationMode[] {
  if (type === null || type === 'kick') {
    return ['open']
  }
  if (step && type !== 'warn') {
    return ['permanent', 'fixed']
  }
  return ['open', 'permanent', 'fixed']
}

/** A fixed length as the plugin writes it (`1h30m`); the text as typed when it isn't one. */
function canonicalLength(text: string): string {
  const millis = parseDuration(text)
  return millis !== null && millis >= 1000 ? formatCompact(millis) : text.trim()
}

/** The duration's text, or null when open. */
export function durationText(duration: DurationForm): string | null {
  switch (duration.mode) {
    case 'open':
      return null
    case 'permanent':
      return PERMANENT
    case 'fixed':
      return canonicalLength(duration.length)
  }
}

/** `permanent` (or `perm`, `forever`) or a positive duration; null when it is neither. */
export function readDuration(text: string): DurationForm | null {
  if (isPermanentToken(text)) {
    return { mode: 'permanent', length: '' }
  }
  return parseDuration(text) !== null ? { mode: 'fixed', length: text.trim() } : null
}

function checkLength(length: string): string | null {
  const check = checkDuration(length)
  if (!check.ok) {
    return check.message
  }
  return check.kind === 'duration' ? secondsProblem(check.millis) : null
}

/** A template's lengths are whole seconds, at least one (TemplateDuration.validateLength). */
function secondsProblem(millis: number): string | null {
  if (millis < 1000) {
    return 'Must be at least one second.'
  }
  if (millis % 1000 !== 0) {
    return 'Must be whole seconds (a template is written as 90s, not 1500ms).'
  }
  return null
}

// ---- Steps ----------------------------------------------------------------------------------

export interface ParsedStep {
  type: PunishmentType | null
  duration: DurationForm
  /** Why the text isn't a step (the plugin's words); absent when it is one. */
  error?: string
}

/** Reads a step's text as TemplateSteps.parse does: `none`, `kick`, `warn`, `warn 30d`, `ban permanent`. */
export function parseStepText(text: string): ParsedStep {
  const words = text.trim().split(/\s+/)
  const fail = (error: string): ParsedStep => ({ type: null, duration: openDuration(), error })
  const first = words[0] ?? ''
  if (words.length > 2 || first === '') {
    return fail(`"${text}" is not a step (none, kick, warn, or a type and a duration: ban 7d, mute 1h, ban permanent)`)
  }
  const lower = first.toLowerCase()
  const none = lower === NONE
  const type = (PUNISHMENT_TYPES as readonly string[]).includes(lower) ? lower as PunishmentType : null
  if (!none && type === null) {
    return fail(isPermanentToken(first) || parseDuration(first) !== null
      ? `"${text}" names no type: a step is none, kick, warn, or a type and a duration (ban 7d, mute 1h, ban permanent)`
      : `"${first}" is not a type (none, ban, mute, warn or kick)`)
  }
  if (words.length === 1) {
    if (type === null || type === 'kick' || type === 'warn') {
      return { type, duration: openDuration() }
    }
    return fail(`"${text}" needs a duration or 'permanent' after the type`)
  }
  if (type === null || type === 'kick') {
    return fail(type === null ? 'a step without a punishment has no duration' : 'a kick has no duration')
  }
  const duration = readDuration(words[1]!)
  return duration ? { type, duration } : fail(`"${words[1]}" is not a duration or 'permanent'`)
}

/** A step's text as the plugin writes it (TemplateSteps.format). */
export function formatStep(step: Pick<TemplateStepForm, 'type' | 'duration'>): string {
  if (step.type === null) {
    return NONE
  }
  const duration = step.type === 'kick' ? null : durationText(step.duration)
  return duration === null ? step.type : `${step.type} ${duration}`
}

export function emptyStep(type: PunishmentType | null = 'ban'): TemplateStepForm {
  const step: TemplateStepForm = { type: null, duration: openDuration('1d'), reason: '', message: '', actions: [], extra: {} }
  setStepType(step, type)
  return step
}

/** Changes a step's type, keeping its duration where the new type allows it. */
export function setStepType(step: TemplateStepForm, type: PunishmentType | null): void {
  step.type = type
  const modes = durationModes(type, true)
  if (!modes.includes(step.duration.mode)) {
    step.duration = modes.includes('fixed') && step.duration.length.trim() !== ''
      ? { mode: 'fixed', length: step.duration.length }
      : modes.includes('fixed') ? { mode: 'fixed', length: '1d' } : openDuration(step.duration.length)
  }
  if (type === null) {
    step.message = ''
  }
}

// ---- Forms ----------------------------------------------------------------------------------

function common(name: string): CommonForm {
  return {
    name,
    reason: '',
    ip: false,
    shadow: false,
    scope: '',
    permissionRequired: false,
    actions: [],
    groups: [],
    message: '',
    display: 'chat',
    silent: false,
    extra: {}
  }
}

export function emptyPreset(name: string, type: PunishmentType = 'ban'): PresetForm {
  return { ...common(name), kind: 'preset', type, duration: openDuration() }
}

export function emptyLadder(name: string): LadderForm {
  return { ...common(name), kind: 'ladder', steps: [emptyStep('warn'), emptyStep('mute'), emptyStep('ban')], expiry: '', addressShared: false }
}

export function emptyTemplate(name: string, kind: TemplateKind = 'ban'): TemplateForm {
  return kind === 'ladder' ? emptyLadder(name) : emptyPreset(name, kind)
}

export function hasMuteStep(form: TemplateForm): boolean {
  return form.kind === 'ladder' ? form.steps.some(step => step.type === 'mute') : form.type === 'mute'
}

/**
 * Whether the template tells an online player anything a dialog could show: a mute or warn
 * preset that isn't a shadow mute, or a ladder with a mute or warn step
 * (TemplateDefinition.validate).
 */
export function canShowDialog(form: TemplateForm): boolean {
  if (form.kind === 'ladder') {
    return form.steps.some(step => step.type === 'mute' || step.type === 'warn')
  }
  return form.type === 'warn' || (form.type === 'mute' && !form.shadow)
}

/**
 * The form as another kind. Between presets the duration stays where the new type allows one;
 * a preset becomes a one-step ladder; a ladder becomes a preset with the duration of its first
 * step of that type (its steps, expiry and address-shared are dropped).
 */
export function changeKind(form: TemplateForm, kind: TemplateKind): TemplateForm {
  const base: CommonForm = {
    name: form.name,
    reason: form.reason,
    ip: form.ip,
    shadow: form.shadow,
    scope: form.scope,
    permissionRequired: form.permissionRequired,
    actions: [...form.actions],
    groups: form.groups.map(group => ({ ...group })),
    message: form.message,
    display: form.display,
    silent: form.silent,
    extra: { ...form.extra }
  }
  let next: TemplateForm
  if (kind === 'ladder') {
    if (form.kind === 'ladder') {
      return form
    }
    const step = emptyStep(form.type)
    if (durationModes(form.type, true).includes(form.duration.mode)) {
      step.duration = { ...form.duration }
    } else if (form.duration.mode === 'open' && form.type !== 'kick') {
      step.duration = { mode: 'permanent', length: form.duration.length }
    }
    next = { ...base, kind: 'ladder', steps: [step], expiry: '', addressShared: false }
  } else {
    let duration: DurationForm
    if (form.kind === 'preset') {
      duration = { ...form.duration }
    } else {
      const match = form.steps.find(step => step.type === kind)
      duration = match ? { ...match.duration } : openDuration()
    }
    if (!durationModes(kind, false).includes(duration.mode)) {
      duration = openDuration(duration.length)
    }
    next = { ...base, kind: 'preset', type: kind, duration }
  }
  if (!hasMuteStep(next)) {
    next.shadow = false
  }
  if (!canShowDialog(next)) {
    next.display = 'chat'
  }
  return next
}

// ---- Reading a definition -------------------------------------------------------------------

export interface FieldIssue {
  /** Path inside the template, in the plugin's terms (`steps[0].step`, `groups.cheating`). */
  path: string
  message: string
}

export interface ReadTemplate {
  /** Null when the tree can't be a template at all (no section, no valid type). */
  form: TemplateForm | null
  /** What doesn't read as the plugin reads it (TemplateFiles.template), before validation. */
  errors: FieldIssue[]
}

function isMap(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

/** A scalar as text, as Configurate's getString gives it; null for sections, lists and null. */
function asText(value: unknown): string | null {
  if (typeof value === 'string') {
    return value
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value)
  }
  return null
}

/**
 * Reads one template from a tree of maps, lists and scalars (a payload definition, or a template
 * of a templates.yml), with the plugin's rules for each key. A key this site doesn't know is
 * kept in `extra`; a key of the format before typed templates is an error naming its replacement.
 */
export function readTemplate(name: string, tree: unknown): ReadTemplate {
  const errors: FieldIssue[] = []
  const add = (path: string, message: string) => errors.push({ path, message })
  if (!isMap(tree)) {
    return { form: null, errors: [{ path: '', message: 'must be a section' }] }
  }
  const typeText = (asText(tree.type) ?? '').trim()
  const kind = (TEMPLATE_KINDS as readonly string[]).includes(typeText.toLowerCase())
    ? typeText.toLowerCase() as TemplateKind
    : null
  if (kind === null) {
    add('type', typeText === '' ? 'is required' : `"${typeText}" is not a type (ban, mute, warn, kick or ladder)`)
  }
  const text = (key: string): string | undefined => {
    const value = tree[key]
    if (value === undefined) {
      return undefined
    }
    const result = asText(value)
    if (result === null) {
      add(key, 'must be a text')
      return undefined
    }
    return result
  }
  const flag = (key: string): boolean => {
    const value = tree[key]
    if (value === undefined) {
      return false
    }
    if (typeof value === 'boolean') {
      return value
    }
    add(key, 'must be true or false')
    return false
  }
  const extra: Record<string, JsonValue> = {}
  for (const [key, value] of Object.entries(tree)) {
    if (key in REPLACED_KEYS) {
      add(key, `is no longer read: ${REPLACED_KEYS[key]}`)
    } else if (!PRESET_KEYS.has(key) && !LADDER_KEYS.has(key)) {
      extra[key] = value as JsonValue
    }
  }
  const base: CommonForm = {
    name,
    reason: text('reason') ?? '',
    ip: flag('ip'),
    shadow: flag('shadow'),
    scope: text('scope') ?? '',
    permissionRequired: flag('permission-required'),
    actions: readActions(tree.actions, 'actions', add),
    groups: [],
    message: text('message') ?? '',
    display: readDisplay(text('display'), add),
    silent: flag('silent'),
    extra
  }
  if (tree.groups !== undefined) {
    if (!isMap(tree.groups)) {
      add('groups', 'must be a section of group: weight')
    } else {
      for (const [group, weight] of Object.entries(tree.groups)) {
        if (typeof weight === 'number') {
          base.groups.push({ name: group, weight })
        } else {
          add(`groups.${group}`, 'must be a weight (a number)')
        }
      }
    }
  }
  if (kind === null) {
    return { form: null, errors }
  }
  // Keys of the other kind are kept as they came, so nothing is lost; validation reports them.
  const own = kind === 'ladder' ? LADDER_KEYS : PRESET_KEYS
  for (const key of Object.keys(OTHER_KIND)) {
    if (!own.has(key) && tree[key] !== undefined) {
      extra[key] = tree[key] as JsonValue
    }
  }
  if (kind !== 'ladder') {
    const durationText = text('duration')
    let duration = openDuration()
    if (durationText !== undefined) {
      const read = readDuration(durationText)
      if (read) {
        duration = read
      } else {
        add('duration', `"${durationText}" is not a duration or 'permanent'`)
        duration = { mode: 'fixed', length: durationText }
      }
    }
    return { form: { ...base, kind: 'preset', type: kind, duration }, errors }
  }
  const steps: TemplateStepForm[] = []
  if (tree.steps !== undefined) {
    if (!Array.isArray(tree.steps)) {
      add('steps', 'must be a list')
    } else {
      tree.steps.forEach((raw, index) => steps.push(readStep(raw, `steps[${index}]`, add)))
    }
  }
  let expiry = text('expiry') ?? ''
  if (expiry.trim() === '' || expiry.trim().toLowerCase() === NONE) {
    expiry = ''
  } else if (parseDuration(expiry) === null) {
    add('expiry', `"${expiry}" is not a duration or 'none'`)
  }
  return { form: { ...base, kind: 'ladder', steps, expiry, addressShared: flag('address-shared') }, errors }
}

/** TemplateFiles.display: chat or dialog in any case; left out, chat. */
function readDisplay(text: string | undefined, add: (path: string, message: string) => void): TemplateDisplay {
  if (text === undefined) {
    return 'chat'
  }
  const display = text.trim().toLowerCase()
  if ((TEMPLATE_DISPLAYS as readonly string[]).includes(display)) {
    return display as TemplateDisplay
  }
  add('display', `"${text}" is not a display (chat or dialog)`)
  return 'chat'
}

function readActions(value: unknown, path: string, add: (path: string, message: string) => void): string[] {
  if (value === undefined) {
    return []
  }
  if (!Array.isArray(value)) {
    add(path, 'must be a list')
    return []
  }
  const actions: string[] = []
  for (const item of value) {
    const text = asText(item)
    if (text === null) {
      add(path, 'must be a list of texts')
      return []
    }
    actions.push(text)
  }
  return actions
}

function readStep(raw: unknown, path: string, add: (path: string, message: string) => void): TemplateStepForm {
  const step: TemplateStepForm = { type: 'ban', duration: openDuration(), reason: '', message: '', actions: [], extra: {} }
  let text: string | null
  let textPath = path
  if (isMap(raw)) {
    textPath = `${path}.step`
    for (const [key, value] of Object.entries(raw)) {
      if (!STEP_KEYS.has(key)) {
        step.extra[key] = value as JsonValue
      }
    }
    if (raw.step === undefined) {
      add(textPath, 'is required')
    }
    text = raw.step === undefined ? null : asText(raw.step)
    if (raw.step !== undefined && text === null) {
      add(textPath, 'must be a text')
    }
    for (const key of ['reason', 'message'] as const) {
      if (raw[key] !== undefined) {
        const value = asText(raw[key])
        if (value === null) {
          add(`${path}.${key}`, 'must be a text')
        } else {
          step[key] = value
        }
      }
    }
    step.actions = readActions(raw.actions, `${path}.actions`, add)
  } else {
    text = asText(raw)
    if (text === null) {
      add(path, 'must be a text')
    }
  }
  if (text !== null) {
    const parsed = parseStepText(text)
    if (parsed.error) {
      add(textPath, parsed.error)
    } else {
      step.type = parsed.type
      step.duration = parsed.duration
    }
  }
  return step
}

export function definitionToForm(name: string, definition: TemplateDefinition): TemplateForm {
  const read = readTemplate(name, definition)
  if (!read.form) {
    throw new Error(`templates.${name}: ${read.errors.map(error => `${error.path} ${error.message}`).join('; ')}`)
  }
  return read.form
}

// ---- Writing a definition -------------------------------------------------------------------

function normalizeAction(action: string): string {
  const stripped = action.trim()
  return stripped.startsWith('/') ? stripped.slice(1).trim() : stripped
}

function normalizeScope(scope: string): string {
  const value = scope.trim().toLowerCase()
  return value === '*' ? 'global' : value
}

function stepTree(step: TemplateStepForm): JsonValue {
  const text = formatStep(step)
  const reason = step.reason.trim()
  const message = step.message.trim() === '' ? '' : step.message
  if (reason === '' && message === '' && step.actions.length === 0 && Object.keys(step.extra).length === 0) {
    return text
  }
  const tree: Record<string, JsonValue> = { step: text }
  if (reason !== '') {
    tree.reason = reason
  }
  if (message !== '') {
    tree.message = message
  }
  if (step.actions.length > 0) {
    tree.actions = step.actions.map(normalizeAction)
  }
  return { ...tree, ...step.extra }
}

/**
 * The definition as the plugin's export writes it (TemplateFiles.tree): keys in file order,
 * optional keys at their default left out, durations compact, a step as plain text when it has
 * nothing else, groups sorted (protocol section 5.4). Unknown keys follow, as they came.
 */
export function formToDefinition(form: TemplateForm): TemplateDefinition {
  const definition: Record<string, JsonValue> = { type: kindOf(form) }
  if (form.ip) {
    definition.ip = true
  }
  if (form.shadow) {
    definition.shadow = true
  }
  if (form.scope.trim() !== '') {
    definition.scope = normalizeScope(form.scope)
  }
  definition.reason = form.reason.trim()
  if (form.kind === 'preset') {
    const duration = form.type === 'kick' ? null : durationText(form.duration)
    if (duration !== null) {
      definition.duration = duration
    }
  } else {
    definition.steps = form.steps.map(stepTree)
    const expiry = form.expiry.trim()
    if (expiry !== '' && expiry.toLowerCase() !== NONE) {
      definition.expiry = canonicalLength(expiry)
    }
    if (form.addressShared) {
      definition['address-shared'] = true
    }
  }
  if (form.permissionRequired) {
    definition['permission-required'] = true
  }
  if (form.actions.length > 0) {
    definition.actions = form.actions.map(normalizeAction)
  }
  if (form.groups.length > 0) {
    const weights = new Map<string, number>()
    for (const group of form.groups) {
      const key = group.name.trim().toLowerCase()
      weights.set(key, Math.max(group.weight, weights.get(key) ?? group.weight))
    }
    // A TreeMap in the plugin: ordered by code unit, not by locale.
    definition.groups = Object.fromEntries([...weights].sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0)))
  }
  if (form.message.trim() !== '') {
    definition.message = form.message
  }
  if (form.display !== 'chat') {
    definition.display = form.display
  }
  if (form.silent) {
    definition.silent = true
  }
  for (const [key, value] of Object.entries(form.extra)) {
    definition[key] = value
  }
  return definition as TemplateDefinition
}

/** A definition from the payload, rewritten as this site writes it, for comparisons. */
export function normalizeDefinition(definition: TemplateDefinition): TemplateDefinition {
  return formToDefinition(definitionToForm('', definition))
}

export function sameDefinition(a: TemplateDefinition, b: TemplateDefinition): boolean {
  return canonicalJson(normalizeDefinition(a)) === canonicalJson(normalizeDefinition(b))
}

// ---- Validation -----------------------------------------------------------------------------

/** The plugin's checks (TemplateDefinition.validate, TemplateStep.of), with its paths. */
export function validateTemplate(form: TemplateForm, otherNames: ReadonlySet<string>): FieldIssue[] {
  const issues: FieldIssue[] = []
  const add = (path: string, message: string) => issues.push({ path, message })
  if (!isIdentifier(form.name)) {
    add('name', `must be ${IDENTIFIER_HINT}`)
  } else if (otherNames.has(form.name)) {
    add('name', 'another template has this name (names are unique across presets and ladders)')
  }
  if (form.reason.trim().length === 0) {
    add('reason', 'must not be empty')
  } else if (form.reason.length > MAX_REASON) {
    add('reason', `must be at most ${MAX_REASON} characters`)
  }
  for (const key of Object.keys(form.extra)) {
    const misplaced = OTHER_KIND[key]?.[form.kind]
    if (misplaced) {
      add(key, misplaced)
    } else if (key in REPLACED_KEYS) {
      add(key, `is no longer read: ${REPLACED_KEYS[key]}`)
    }
  }
  if (form.kind === 'preset') {
    if (form.type === 'kick') {
      if (form.duration.mode !== 'open') {
        add('duration', 'a kick has no duration')
      }
    } else if (form.duration.mode === 'fixed') {
      const problem = checkLength(form.duration.length)
      if (problem) {
        add('duration', problem)
      }
    }
    if (form.shadow && form.type !== 'mute') {
      add('shadow', 'only a mute is a shadow mute')
    }
    if (form.display === 'dialog' && form.type !== 'mute' && form.type !== 'warn') {
      add('display', 'only a mute or a warning is shown as a dialog')
    } else if (form.display === 'dialog' && form.type === 'mute' && form.shadow) {
      add('display', 'a shadow mute shows the player nothing')
    }
  } else {
    if (form.steps.length === 0 || form.steps.length > MAX_STEPS) {
      add('steps', `must have 1-${MAX_STEPS} steps`)
    }
    form.steps.forEach((step, index) => validateStep(step, `steps[${index}]`, add))
    if (form.expiry.trim() !== '') {
      const check = checkDuration(form.expiry, { none: true })
      if (!check.ok) {
        add('expiry', check.message)
      } else if (check.kind === 'duration') {
        const problem = secondsProblem(check.millis)
        if (problem) {
          add('expiry', problem)
        }
      }
    }
    if (form.shadow && !hasMuteStep(form)) {
      add('shadow', 'needs a mute step')
    }
    if (form.display === 'dialog' && !canShowDialog(form)) {
      add('display', 'needs a mute or warn step')
    }
  }
  if (form.scope.trim() !== '' && !SCOPE_PATTERN.test(normalizeScope(form.scope))) {
    add('scope', 'must be global, server:<name> or category:<name>')
  }
  if (form.message.length > MAX_MESSAGE) {
    add('message', `must be at most ${MAX_MESSAGE} characters`)
  }
  validateActions(form.actions, 'actions', add)
  if (form.groups.length > MAX_GROUPS) {
    add('groups', `has more than ${MAX_GROUPS} groups`)
  }
  const seen = new Set<string>()
  for (const group of form.groups) {
    if (!isIdentifier(group.name)) {
      add(`groups.${group.name}`, `group names must be ${IDENTIFIER_HINT}`)
    } else if (seen.has(group.name)) {
      add(`groups.${group.name}`, 'is listed twice')
    }
    seen.add(group.name)
    if (!Number.isInteger(group.weight) || group.weight < 1 || group.weight > MAX_WEIGHT) {
      add(`groups.${group.name}`, `weight must be 1-${MAX_WEIGHT}`)
    }
  }
  return issues
}

function validateStep(step: TemplateStepForm, path: string, add: (path: string, message: string) => void) {
  const modes = durationModes(step.type, true)
  if (!modes.includes(step.duration.mode)) {
    add(`${path}.step`, step.type === null
      ? 'a step without a punishment has no duration'
      : step.type === 'kick' ? 'a kick has no duration' : 'a ban or mute step needs a duration or permanent')
  } else if (step.duration.mode === 'fixed') {
    const problem = checkLength(step.duration.length)
    if (problem) {
      add(`${path}.step`, problem)
    }
  }
  if (step.type === null) {
    if (step.message.trim() !== '') {
      add(`${path}.message`, 'a step without a punishment has no message')
    }
    if (step.actions.length === 0) {
      add(`${path}.actions`, 'a step without a punishment needs an action')
    }
  }
  if (step.reason.length > MAX_REASON) {
    add(`${path}.reason`, `must be at most ${MAX_REASON} characters`)
  }
  if (step.message.length > MAX_MESSAGE) {
    add(`${path}.message`, `must be at most ${MAX_MESSAGE} characters`)
  }
  for (const key of Object.keys(step.extra)) {
    add(`${path}.${key}`, 'unknown key (a step has step, reason, message and actions)')
  }
  validateActions(step.actions, `${path}.actions`, add)
}

function validateActions(actions: string[], path: string, add: (path: string, message: string) => void) {
  if (actions.length > MAX_ACTIONS) {
    add(path, `has more than ${MAX_ACTIONS} actions`)
  }
  actions.forEach((action, index) => {
    const problem = actionProblem(action)
    if (problem) {
      add(`${path}[${index}]`, problem)
    }
  })
}

// ---- Texts ----------------------------------------------------------------------------------

export function permissionNode(name: string): string {
  return `sakurabans.template.${name}`
}

/** How staff use it, with the default command names: `/mute <player> -t spam`, `/punish <player> toxicity`. */
export function usageHint(form: TemplateForm): string {
  const name = form.name || '<name>'
  return form.kind === 'ladder' ? `/punish <player> ${name}` : `/${form.type} <player> -t ${name}`
}

function ordinal(n: number): string {
  const tens = n % 100
  if (tens >= 11 && tens <= 13) {
    return `${n}th`
  }
  return `${n}${['th', 'st', 'nd', 'rd'][n % 10] ?? 'th'}`
}

/** "7 days", "permanent", or null when open. */
function lengthText(duration: DurationForm): string | null {
  if (duration.mode === 'open') {
    return null
  }
  if (duration.mode === 'permanent') {
    return 'permanent'
  }
  const millis = parseDuration(duration.length)
  return millis === null ? `"${duration.length}"` : describeDuration(millis)
}

/** A chip label for a step: "Warn", "Kick", "Mute 1 hour", "Ban permanent", "No punishment". */
export function stepLabel(step: TemplateStepForm): string {
  if (step.type === null) {
    return 'No punishment'
  }
  const length = step.type === 'kick' ? null : lengthText(step.duration)
  return length ? `${TYPE_LABEL[step.type]} ${length}` : TYPE_LABEL[step.type]
}

function presetText(form: PresetForm): string {
  const subject = form.type === 'mute' && form.shadow ? 'shadow mute' : form.type === 'warn' ? 'warning' : form.type
  const upper = subject.charAt(0).toUpperCase() + subject.slice(1)
  if (form.type === 'kick') {
    return 'Kick'
  }
  switch (form.duration.mode) {
    case 'open':
      return `${upper}; the command decides how long`
    case 'permanent':
      return `Permanent ${subject}`
    case 'fixed':
      return `${upper} for ${lengthText(form.duration)}`
  }
}

/**
 * "Mute for 1 hour", "1st: warn, 2nd: kick, then ban permanent; uses older than 30 days don't
 * count; counts with: spam (weight 1)".
 */
export function summarizeTemplate(form: TemplateForm, all: readonly TemplateForm[]): string {
  const parts: string[] = []
  if (form.kind === 'preset') {
    parts.push(form.ip ? `${presetText(form)}, on the address` : presetText(form))
  } else {
    const steps = form.steps.map(step => stepLabel(step).toLowerCase())
    if (steps.length === 1) {
      parts.push(`always ${steps[0]}`)
    } else if (steps.length > 1) {
      const head = steps.slice(0, -1).map((text, index) => `${ordinal(index + 1)}: ${text}`)
      parts.push(`${head.join(', ')}, then ${steps.at(-1)}`)
    }
    const millis = form.expiry.trim() === '' ? null : parseDuration(form.expiry)
    if (millis !== null) {
      parts.push(`uses older than ${describeDuration(millis)} don't count`)
    }
  }
  const mates: string[] = []
  for (const group of form.groups) {
    for (const other of all) {
      if (other === form || other.name === form.name) {
        continue
      }
      const shared = other.groups.find(candidate => candidate.name === group.name)
      if (shared) {
        mates.push(`${other.name} (weight ${shared.weight})`)
      }
    }
  }
  if (mates.length > 0) {
    parts.push(`counts with: ${[...new Set(mates)].join(', ')}`)
  }
  return parts.join('; ')
}

/**
 * The templates as templates.yml writes them (for the YAML export): global definitions under
 * `templates`, variants (`variant` other than `global`) under `variants.<scope>`.
 */
export function templatesToYamlObject(forms: ReadonlyArray<TemplateForm & { variant?: string }>): {
  templates: Record<string, TemplateDefinition>
  variants?: Record<string, Record<string, TemplateDefinition>>
} {
  const templates: Record<string, TemplateDefinition> = {}
  const variants: Record<string, Record<string, TemplateDefinition>> = {}
  for (const form of [...forms].sort((a, b) => a.name.localeCompare(b.name))) {
    if (form.variant === undefined || form.variant === 'global') {
      templates[form.name] = formToDefinition(form)
    } else {
      (variants[form.variant] ??= {})[form.name] = formToDefinition(form)
    }
  }
  const scopes = Object.keys(variants).sort()
  return scopes.length === 0
    ? { templates }
    : { templates, variants: Object.fromEntries(scopes.map(scope => [scope, variants[scope]!])) }
}
