// Settings: client-side validation that mirrors the plugin's types and bounds
// (core/configuration/SettingsMapper.java). The plugin validates everything again.

import { checkDuration } from '../duration'
import type { JsonValue, SettingField } from '../protocol/schema'
import { valueMatchesType } from '../protocol/schema'
import { actionProblem, IDENTIFIER_HINT, isIdentifier, MAX_REASON } from './limits'

/** STRING keys whose value is "a duration, or never" (config.yml). */
const DURATION_OR_NEVER = new Set(['alts.record-expiration', 'warnings.expire-after'])

/** STRING keys with the reason length limit. */
const REASON_KEYS = new Set(['reasons.default'])

/** Placeholders of warnings.actions (config.yml comment). */
export const WARNING_ACTION_PLACEHOLDERS = ['victim', 'uuid', 'address', 'id', 'operator',
  'reason', 'duration', 'count'] as const

export const KNOWN_MAP_KEYS = ['scopes.categories', 'reasons.shortcuts', 'warnings.actions'] as const

export function isDurationOrNever(field: SettingField): boolean {
  return field.type === 'STRING'
    && (DURATION_OR_NEVER.has(field.key) || /duration,? or never/i.test(field.description))
}

/** Problems with a value for the field, each with a path below the key ('' for the key). */
export function validateSetting(field: SettingField, value: JsonValue):
Array<{ path: string, message: string }> {
  const issues: Array<{ path: string, message: string }> = []
  const add = (path: string, message: string) => issues.push({ path, message })
  if (!valueMatchesType(field.type, value)) {
    add('', `must be a ${field.type.toLowerCase().replace('_', ' ')} value`)
    return issues
  }
  switch (field.type) {
    case 'INTEGER':
    case 'DECIMAL': {
      const number = value as number
      if (field.min !== null && number < field.min) {
        add('', `must be at least ${field.min}`)
      }
      if (field.max !== null && number > field.max) {
        add('', `must be at most ${field.max}`)
      }
      break
    }
    case 'ENUM':
      if (!field.options.includes(value as string)) {
        add('', `must be one of ${field.options.join(', ')}`)
      }
      break
    case 'DURATION': {
      // The only network DURATION key, notifications.attempt-throttle, accepts 0s ("off").
      const check = checkDuration(value as string, { zero: true })
      if (!check.ok) {
        add('', check.message)
      }
      break
    }
    case 'STRING': {
      const text = value as string
      if (field.key.endsWith('.color') && text !== '' && !/^#[0-9a-fA-F]{6}$/.test(text)) {
        add('', 'must be a colour like #e74c3c')
      }
      if (isDurationOrNever(field)) {
        const check = checkDuration(text, { never: true })
        if (!check.ok) {
          add('', check.message)
        }
      }
      if (REASON_KEYS.has(field.key)) {
        if (text.trim() === '') {
          add('', 'must not be empty')
        } else if (text.length > MAX_REASON) {
          add('', `must be at most ${MAX_REASON} characters`)
        }
      }
      break
    }
    case 'MAP':
      if (field.key === 'limits:groups') {
        validateLimitGroups(value as Record<string, JsonValue>, add)
      } else if (/^webhooks:routes\.[^.]+\.fields$/.test(field.key)) {
        validateEmbedFields(value as Record<string, JsonValue>, add)
      } else {
        validateMap(field.key, value as Record<string, JsonValue>, add)
      }
      break
    default:
      break
  }
  return issues
}

function validateMap(key: string, map: Record<string, JsonValue>,
  add: (path: string, message: string) => void) {
  switch (key) {
    case 'scopes.categories':
      for (const [category, servers] of Object.entries(map)) {
        if (!isIdentifier(category)) {
          add(category, `category names must be ${IDENTIFIER_HINT}`)
        }
        if (!Array.isArray(servers) || !servers.every(server => typeof server === 'string')) {
          add(category, 'must be a list of server names')
          continue
        }
        for (const server of servers as string[]) {
          if (!isIdentifier(server)) {
            add(category, `"${server}" is not a server name (${IDENTIFIER_HINT})`)
          }
        }
      }
      break
    case 'reasons.shortcuts':
      for (const [name, text] of Object.entries(map)) {
        if (!isIdentifier(name)) {
          add(name, `shortcut names must be ${IDENTIFIER_HINT}`)
        }
        if (typeof text !== 'string' || text.trim() === '' || text.length > MAX_REASON) {
          add(name, `must be a reason of 1-${MAX_REASON} characters`)
        }
      }
      break
    case 'warnings.actions':
      for (const [count, commands] of Object.entries(map)) {
        if (!/^\d+$/.test(count.trim()) || Number(count) < 1) {
          add(count, 'must be a number of warnings (1 or more)')
        }
        if (!Array.isArray(commands) || !commands.every(command => typeof command === 'string')) {
          add(count, 'must be a list of commands')
          continue
        }
        for (const command of commands as string[]) {
          const problem = actionProblem(command)
          if (problem) {
            add(count, `a command ${problem}`)
          }
        }
      }
      break
    default:
      break
  }
}

/** The group of a key: its first segment ("alts.join-notification.limit" → "alts"). */
export function settingGroup(key: string): string {
  const dot = key.indexOf('.')
  return dot < 0 ? key : key.slice(0, dot)
}

/** limits.yml: the types max-duration and cooldown know (core/resources/defaults/limits.yml). */
export const LIMIT_DURATION_TYPES = ['ban', 'mute', 'warn'] as const
export const LIMIT_COOLDOWN_TYPES = ['ban', 'mute', 'warn', 'kick'] as const
const LIMIT_GROUP_KEYS = new Set(['permission', 'max-duration', 'cooldown', 'require-template'])

function validateLimitGroups(groups: Record<string, JsonValue>,
  add: (path: string, message: string) => void) {
  for (const [name, group] of Object.entries(groups)) {
    if (!isIdentifier(name)) {
      add(name, `group names must be ${IDENTIFIER_HINT}`)
    }
    if (typeof group !== 'object' || group === null || Array.isArray(group)) {
      add(name, 'must be a section')
      continue
    }
    for (const key of Object.keys(group)) {
      if (!LIMIT_GROUP_KEYS.has(key)) {
        add(`${name}.${key}`, 'is not a key of a limits group')
      }
    }
    if (typeof group.permission !== 'string' || group.permission.trim() === '') {
      add(`${name}.permission`, 'must be a permission')
    }
    if (group['require-template'] !== undefined && typeof group['require-template'] !== 'boolean') {
      add(`${name}.require-template`, 'must be true or false')
    }
    const durations = group['max-duration']
    if (durations !== undefined) {
      if (typeof durations !== 'object' || durations === null || Array.isArray(durations)) {
        add(`${name}.max-duration`, 'must be a section of type: duration')
      } else {
        for (const [type, text] of Object.entries(durations)) {
          if (!(LIMIT_DURATION_TYPES as readonly string[]).includes(type)) {
            add(`${name}.max-duration.${type}`, 'must be ban, mute or warn')
          } else if (typeof text !== 'string' || !checkDuration(text, { permanent: true }).ok) {
            add(`${name}.max-duration.${type}`, 'must be a duration or permanent')
          }
        }
      }
    }
    const cooldowns = group.cooldown
    if (cooldowns !== undefined) {
      if (typeof cooldowns !== 'object' || cooldowns === null || Array.isArray(cooldowns)) {
        add(`${name}.cooldown`, 'must be a section of type: duration')
      } else {
        for (const [type, text] of Object.entries(cooldowns)) {
          if (!(LIMIT_COOLDOWN_TYPES as readonly string[]).includes(type)) {
            add(`${name}.cooldown.${type}`, 'must be ban, mute, warn or kick')
          } else if (typeof text !== 'string' || !checkDuration(text).ok) {
            add(`${name}.cooldown.${type}`, 'must be a duration')
          }
        }
      }
    }
  }
}

export const MAX_EMBED_FIELDS = 25

function validateEmbedFields(fields: Record<string, JsonValue>,
  add: (path: string, message: string) => void) {
  const entries = Object.entries(fields)
  if (entries.length > MAX_EMBED_FIELDS) {
    add('', `has more than ${MAX_EMBED_FIELDS} fields`)
  }
  for (const [name, text] of entries) {
    if (name.trim() === '') {
      add(name, 'a field needs a name')
    }
    if (typeof text !== 'string') {
      add(name, 'must be text')
    }
  }
}
