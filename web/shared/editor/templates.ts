// Templates in the editor: an editable form model, its conversion to and from the protocol's
// definition (protocol section 5.4), validation and the summary line.
//
// A ladder step may name its own type (decision D36): "7d" (the template's type), "kick",
// "mute 1h", "ban permanent". Step actions and step messages belong to their step in the model
// and are numbered only when written out, so reordering carries them along.

import { checkDuration, describeDuration, parseDuration } from '../duration'
import { canonicalJson } from '../json'
import type { JsonValue, TemplateDefinition, TemplateType } from '../protocol/schema'
import { SCOPE_PATTERN } from '../protocol/schema'
import {
  actionProblem, IDENTIFIER_HINT, isIdentifier, MAX_ACTIONS, MAX_GROUPS, MAX_MESSAGE, MAX_REASON,
  MAX_STEPS, MAX_WEIGHT
} from './limits'

export interface TemplateStepForm {
  /** Null: the template's type. */
  type: TemplateType | null
  /** Empty for a kick. */
  duration: string
  actions: string[]
  /** Empty: the template's message (or the network's). */
  message: string
}

export interface TemplateGroupForm {
  name: string
  weight: number
}

export interface TemplateForm {
  name: string
  type: TemplateType
  reason: string
  steps: TemplateStepForm[]
  /** Empty for none. */
  ladderExpiry: string
  ip: boolean
  shadow: boolean
  /** Empty for "scopes.default of the server". */
  scope: string
  addressShared: boolean
  permissionRequired: boolean
  actions: string[]
  groups: TemplateGroupForm[]
  /** Empty: the network's message for the step's type. */
  message: string
  /** Keys this site doesn't know, kept as they came. */
  extra: Record<string, JsonValue>
}

const KNOWN_KEYS = new Set(['type', 'reason', 'ladder', 'ip', 'shadow', 'scope', 'ladder-expiry',
  'address-shared', 'permission-required', 'actions', 'step-actions', 'groups', 'message',
  'step-messages'])

export const TEMPLATE_TYPES: readonly TemplateType[] = ['ban', 'mute', 'warn', 'kick']

/** Placeholders of template actions (templates.yml comment). */
export const TEMPLATE_ACTION_PLACEHOLDERS = ['victim', 'uuid', 'address', 'id', 'operator',
  'reason', 'template', 'step', 'duration'] as const

/** The network message a step's message replaces, by the step's type (lang/en_US.yml). */
export const NETWORK_MESSAGE_KEYS: Record<TemplateType, string> = {
  ban: 'screen.ban',
  kick: 'screen.kick',
  mute: 'victim.muted',
  warn: 'victim.warned'
}

/** Placeholders of those messages when the payload has no messages section (lang/en_US.yml). */
export const FALLBACK_MESSAGE_PLACEHOLDERS: Record<TemplateType, readonly string[]> = {
  ban: ['reason', 'operator', 'duration', 'remaining', 'expires', 'id', 'display_id', 'scope', 'type', 'server'],
  kick: ['reason', 'operator', 'duration', 'remaining', 'expires', 'id', 'display_id', 'scope', 'type', 'server'],
  mute: ['operator', 'reason', 'duration'],
  warn: ['operator', 'reason']
}

export function stepType(step: TemplateStepForm, template: TemplateType): TemplateType {
  return step.type ?? template
}

/** Reads one ladder entry: "7d", "permanent", "kick", "mute 1h", "ban permanent". */
export function parseStep(text: string): Pick<TemplateStepForm, 'type' | 'duration'> {
  const parts = text.trim().split(/\s+/)
  const first = parts[0]?.toLowerCase() ?? ''
  if ((TEMPLATE_TYPES as readonly string[]).includes(first)) {
    return { type: first as TemplateType, duration: parts.slice(1).join(' ') }
  }
  return { type: null, duration: text.trim() }
}

/** Writes one ladder entry: just the duration when the step has the template's type. */
export function formatStep(step: Pick<TemplateStepForm, 'type' | 'duration'>, template: TemplateType): string {
  const duration = step.duration.trim()
  if (step.type === null) {
    return template === 'kick' && duration === '' ? 'permanent' : duration
  }
  return step.type === 'kick' || duration === '' ? step.type : `${step.type} ${duration}`
}

/** A step as "<type> <duration>" whatever way it was written, for comparisons. */
function canonicalStep(text: string, template: TemplateType): string {
  const step = parseStep(text)
  const type = step.type ?? template
  if (type === 'kick') {
    return 'kick'
  }
  const duration = step.duration.toLowerCase()
  return `${type} ${['perm', 'permanent', 'forever'].includes(duration) ? 'permanent' : duration}`
}

export function emptyStep(type: TemplateType | null, template: TemplateType): TemplateStepForm {
  const effective = type ?? template
  return { type, duration: effective === 'kick' ? '' : '1d', actions: [], message: '' }
}

export function emptyTemplate(name: string, type: TemplateType = 'ban'): TemplateForm {
  return {
    name,
    type,
    reason: '',
    steps: [emptyStep(null, type)],
    ladderExpiry: '',
    ip: false,
    shadow: false,
    scope: '',
    addressShared: false,
    permissionRequired: false,
    actions: [],
    groups: [],
    message: '',
    extra: {}
  }
}

export function definitionToForm(name: string, definition: TemplateDefinition): TemplateForm {
  const stepActions = definition['step-actions'] ?? {}
  const stepMessages = definition['step-messages'] ?? {}
  const expiry = definition['ladder-expiry'] ?? ''
  const extra: Record<string, JsonValue> = {}
  for (const [key, value] of Object.entries(definition)) {
    if (!KNOWN_KEYS.has(key)) {
      extra[key] = value as JsonValue
    }
  }
  return {
    name,
    type: definition.type,
    reason: definition.reason,
    steps: definition.ladder.map((text, index) => {
      const parsed = parseStep(text)
      const kickOfKick = parsed.type === null && definition.type === 'kick'
      return {
        type: parsed.type,
        duration: kickOfKick && parsed.duration.toLowerCase() === 'permanent' ? '' : parsed.duration,
        actions: [...(stepActions[String(index + 1)] ?? [])],
        message: stepMessages[String(index + 1)] ?? ''
      }
    }),
    ladderExpiry: expiry.trim().toLowerCase() === 'none' ? '' : expiry,
    ip: definition.ip ?? false,
    shadow: definition.shadow ?? false,
    scope: definition.scope ?? '',
    addressShared: definition['address-shared'] ?? false,
    permissionRequired: definition['permission-required'] ?? false,
    actions: [...(definition.actions ?? [])],
    groups: Object.entries(definition.groups ?? {}).map(([group, weight]) => ({ name: group, weight })),
    message: definition.message ?? '',
    extra
  }
}

/**
 * The definition as the plugin's export writes it, with optional keys at their default left out
 * (protocol section 5.4). Keys are in the order of templates.yml.
 */
export function formToDefinition(form: TemplateForm): TemplateDefinition {
  const definition: Record<string, JsonValue> = { type: form.type }
  if (form.ip) {
    definition.ip = true
  }
  if (form.shadow) {
    definition.shadow = true
  }
  if (form.scope.trim() !== '') {
    definition.scope = form.scope.trim()
  }
  definition.reason = form.reason
  definition.ladder = form.steps.map(step => formatStep(step, form.type))
  if (form.ladderExpiry.trim() !== '' && form.ladderExpiry.trim().toLowerCase() !== 'none') {
    definition['ladder-expiry'] = form.ladderExpiry.trim()
  }
  if (form.addressShared) {
    definition['address-shared'] = true
  }
  if (form.permissionRequired) {
    definition['permission-required'] = true
  }
  if (form.actions.length > 0) {
    definition.actions = [...form.actions]
  }
  const stepActions: Record<string, JsonValue> = {}
  const stepMessages: Record<string, JsonValue> = {}
  form.steps.forEach((step, index) => {
    if (step.actions.length > 0) {
      stepActions[String(index + 1)] = [...step.actions]
    }
    if (step.message.trim() !== '') {
      stepMessages[String(index + 1)] = step.message
    }
  })
  if (Object.keys(stepActions).length > 0) {
    definition['step-actions'] = stepActions
  }
  if (form.groups.length > 0) {
    const groups: Record<string, JsonValue> = {}
    for (const group of form.groups) {
      groups[group.name] = group.weight
    }
    definition.groups = groups
  }
  if (form.message.trim() !== '') {
    definition.message = form.message
  }
  if (Object.keys(stepMessages).length > 0) {
    definition['step-messages'] = stepMessages
  }
  for (const [key, value] of Object.entries(form.extra)) {
    definition[key] = value
  }
  return definition as TemplateDefinition
}

/** A definition from the payload, rewritten with defaults left out, for comparisons. */
export function normalizeDefinition(definition: TemplateDefinition): TemplateDefinition {
  const normal = formToDefinition(definitionToForm('', definition))
  return { ...normal, ladder: normal.ladder.map(step => canonicalStep(step, normal.type)) }
}

export function sameDefinition(a: TemplateDefinition, b: TemplateDefinition): boolean {
  return canonicalJson(normalizeDefinition(a)) === canonicalJson(normalizeDefinition(b))
}

export interface FieldIssue {
  /** Path inside the template, in the change set's terms (`ladder[0]`, `groups.cheating`). */
  path: string
  message: string
}

export function hasMuteStep(form: TemplateForm): boolean {
  return form.steps.some(step => stepType(step, form.type) === 'mute')
}

export function validateTemplate(form: TemplateForm, otherNames: ReadonlySet<string>):
FieldIssue[] {
  const issues: FieldIssue[] = []
  const add = (path: string, message: string) => issues.push({ path, message })
  if (!isIdentifier(form.name)) {
    add('name', `must be ${IDENTIFIER_HINT}`)
  } else if (otherNames.has(form.name)) {
    add('name', 'another template has this name')
  }
  if (form.reason.trim().length === 0) {
    add('reason', 'must not be empty')
  } else if (form.reason.length > MAX_REASON) {
    add('reason', `must be at most ${MAX_REASON} characters`)
  }
  if (form.steps.length === 0 || form.steps.length > MAX_STEPS) {
    add('ladder', `must have 1-${MAX_STEPS} steps`)
  }
  form.steps.forEach((step, index) => {
    const type = stepType(step, form.type)
    if (type === 'kick') {
      const duration = step.duration.trim().toLowerCase()
      if (duration !== '' && !(step.type === null && duration === 'permanent')) {
        add(`ladder[${index}]`, 'a kick has no duration')
      }
    } else {
      const check = checkDuration(step.duration, { permanent: true })
      if (!check.ok) {
        add(`ladder[${index}]`, check.message)
      }
    }
    validateActions(step.actions, `step-actions.${index + 1}`, add)
    if (step.message.length > MAX_MESSAGE) {
      add(`step-messages.${index + 1}`, `must be at most ${MAX_MESSAGE} characters`)
    }
  })
  if (form.ladderExpiry.trim() !== '') {
    const check = checkDuration(form.ladderExpiry, { none: true })
    if (!check.ok) {
      add('ladder-expiry', check.message)
    }
  }
  if (form.shadow && !hasMuteStep(form)) {
    add('shadow', 'shadow applies to mute steps, and the ladder has none')
  }
  if (form.scope.trim() !== '' && !SCOPE_PATTERN.test(form.scope.trim())) {
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

function validateActions(actions: string[], path: string,
  add: (path: string, message: string) => void) {
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

export function permissionNode(name: string): string {
  return `sakurabans.template.${name}`
}

function ordinal(n: number): string {
  const tens = n % 100
  if (tens >= 11 && tens <= 13) {
    return `${n}th`
  }
  return `${n}${['th', 'st', 'nd', 'rd'][n % 10] ?? 'th'}`
}

const TYPE_LABEL: Record<TemplateType, string> = { ban: 'Ban', mute: 'Mute', warn: 'Warn', kick: 'Kick' }

/** "7 days", "permanent", or "" for a kick. */
function durationText(step: TemplateStepForm, template: TemplateType): string {
  if (stepType(step, template) === 'kick') {
    return ''
  }
  const check = checkDuration(step.duration, { permanent: true })
  if (!check.ok) {
    return `"${step.duration}"`
  }
  return check.kind === 'duration' ? describeDuration(check.millis) : 'permanent'
}

/** A chip label for a step: "Kick", "Mute 1 hour", "Ban permanent". */
export function stepLabel(step: TemplateStepForm, template: TemplateType): string {
  const type = stepType(step, template)
  const duration = durationText(step, template)
  return duration ? `${TYPE_LABEL[type]} ${duration}` : TYPE_LABEL[type]
}

/** "1st: 7 days, 2nd: 30 days, then permanent; counts with: xray (weight 1)". */
export function summarizeTemplate(form: TemplateForm, all: readonly TemplateForm[]): string {
  const parts: string[] = []
  const mixed = form.steps.some(step => stepType(step, form.type) !== form.type)
  const steps = form.steps.map(step => (mixed || form.type === 'kick'
    ? stepLabel(step, form.type).toLowerCase()
    : durationText(step, form.type)))
  if (steps.length === 1) {
    parts.push(form.type === 'kick' && !mixed ? 'kick every time' : `always ${steps[0]}`)
  } else if (steps.length > 1) {
    const head = steps.slice(0, -1).map((text, index) => `${ordinal(index + 1)}: ${text}`)
    parts.push(`${head.join(', ')}, then ${steps.at(-1)}`)
  }
  const expiry = form.ladderExpiry.trim()
  if (expiry !== '') {
    const millis = parseDuration(expiry)
    if (millis !== null) {
      parts.push(`earlier punishments older than ${describeDuration(millis)} don't count`)
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

/** The template as templates.yml writes it (for the YAML export). */
export function templatesToYamlObject(forms: readonly TemplateForm[]): {
  templates: Record<string, TemplateDefinition>
} {
  const templates: Record<string, TemplateDefinition> = {}
  for (const form of [...forms].sort((a, b) => a.name.localeCompare(b.name))) {
    templates[form.name] = formToDefinition(form)
  }
  return { templates }
}
