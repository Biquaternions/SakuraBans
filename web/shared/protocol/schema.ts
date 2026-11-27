// Zod schemas of the payload, the change set and the result, format 1 (protocol sections 5, 6
// and 9). The TypeScript types are inferred from them.

import * as z from 'zod'

// No eval: Zod's JIT probes `new Function`, which the site's CSP forbids (and reports).
z.config({ jitless: true })

export const NAME_PATTERN = /^[a-z0-9_-]{1,64}$/
export const LOCALE_PATTERN = /^[a-z]{2,3}_[A-Z]{2}$/
export const SCOPE_PATTERN = /^(global|server:[a-z0-9_-]{1,64}|category:[a-z0-9_-]{1,64})$/
export const GLOBAL = 'global'

export const SECTIONS = ['settings', 'messages', 'templates'] as const
export const sectionSchema = z.enum(SECTIONS)
export type Section = z.infer<typeof sectionSchema>

export const SETTING_TYPES = ['BOOLEAN', 'INTEGER', 'DECIMAL', 'STRING', 'ENUM', 'DURATION',
  'STRING_LIST', 'MAP'] as const
export const settingTypeSchema = z.enum(SETTING_TYPES)
export type SettingType = z.infer<typeof settingTypeSchema>

export const SETTING_GROUPS = ['config', 'limits', 'webhooks'] as const
export const settingGroupSchema = z.enum(SETTING_GROUPS)
export type SettingGroup = z.infer<typeof settingGroupSchema>

export type JsonValue = string | number | boolean | null | JsonValue[] | { [key: string]: JsonValue }
export const jsonValueSchema: z.ZodType<JsonValue> = z.json()

export type MapValue = Record<string, JsonValue>

/** Whether a JSON value has the shape protocol section 5.2 gives for the type. */
export function valueMatchesType(type: SettingType, value: unknown): boolean {
  switch (type) {
    case 'BOOLEAN':
      return typeof value === 'boolean'
    case 'INTEGER':
      return typeof value === 'number' && Number.isInteger(value)
    case 'DECIMAL':
      return typeof value === 'number' && Number.isFinite(value)
    case 'STRING':
    case 'ENUM':
    case 'DURATION':
      return typeof value === 'string'
    case 'STRING_LIST':
      return Array.isArray(value) && value.every(item => typeof item === 'string')
    case 'MAP':
      return typeof value === 'object' && value !== null && !Array.isArray(value)
  }
}

export const settingFieldSchema = z.object({
  key: z.string().min(1),
  group: settingGroupSchema,
  type: settingTypeSchema,
  description: z.string(),
  options: z.array(z.string()),
  min: z.number().nullable(),
  max: z.number().nullable(),
  default: jsonValueSchema,
  level: z.enum(['GLOBAL', 'SCOPED']),
  secret: z.boolean()
}).superRefine((field, ctx) => {
  if (!field.secret && !valueMatchesType(field.type, field.default)) {
    ctx.addIssue({ code: 'custom', path: ['default'], message: `is not a ${field.type} value` })
  }
})
export type SettingField = z.infer<typeof settingFieldSchema>

export const scopeOverridesSchema = z.object({
  scope: z.string().regex(SCOPE_PATTERN),
  version: z.number().int().nonnegative(),
  overrides: z.record(z.string(), jsonValueSchema)
})
export type ScopeOverrides = z.infer<typeof scopeOverridesSchema>

export const serverSchema = z.object({
  id: z.string(),
  name: z.string(),
  categories: z.array(z.string()),
  platform: z.string(),
  pluginVersion: z.string(),
  lastSeenAt: z.number().int(),
  fileValues: z.record(z.string(), jsonValueSchema)
})
export type ServerInfo = z.infer<typeof serverSchema>

export const settingsSectionSchema = z.object({
  fields: z.array(settingFieldSchema),
  scopes: z.array(scopeOverridesSchema),
  servers: z.array(serverSchema)
}).superRefine((section, ctx) => {
  if (section.scopes[0]?.scope !== GLOBAL) {
    ctx.addIssue({ code: 'custom', path: ['scopes'], message: 'global must be present and first' })
  }
  const fields = new Map(section.fields.map(field => [field.key, field]))
  const check = (path: Array<string | number>, key: string, value: unknown) => {
    const field = fields.get(key)
    if (!field) {
      return
    }
    const ok = field.secret ? typeof value === 'boolean' : valueMatchesType(field.type, value)
    if (!ok) {
      ctx.addIssue({ code: 'custom', path, message: field.secret ? 'a secret is true or false here' : `is not a ${field.type} value` })
    }
  }
  section.scopes.forEach((scope, index) => {
    for (const [key, value] of Object.entries(scope.overrides)) {
      check(['scopes', index, 'overrides', key], key, value)
    }
  })
  section.servers.forEach((server, index) => {
    for (const [key, value] of Object.entries(server.fileValues)) {
      check(['servers', index, 'fileValues', key], key, value)
    }
  })
})
export type SettingsSection = z.infer<typeof settingsSectionSchema>

export const messageFieldSchema = z.object({
  key: z.string().min(1),
  description: z.string(),
  placeholders: z.array(z.string()),
  default: z.string()
})
export type MessageField = z.infer<typeof messageFieldSchema>

// Locales from the plugin are taken as they come (a language file may be named "de"); only
// locales created in the editor must have the xx_YY form.
export const localeFilesSchema = z.object({
  locale: z.string().min(1),
  fileTexts: z.record(z.string(), z.string())
})
export type LocaleFiles = z.infer<typeof localeFilesSchema>

export const messageScopeSchema = z.object({
  scope: z.string().regex(SCOPE_PATTERN),
  locale: z.string().min(1),
  version: z.number().int().nonnegative(),
  overrides: z.record(z.string(), z.string())
})
export type MessageScope = z.infer<typeof messageScopeSchema>

/** The four punishment types: of a preset, and of a ladder step. */
export const punishmentTypeSchema = z.enum(['ban', 'mute', 'warn', 'kick'])
export type PunishmentType = z.infer<typeof punishmentTypeSchema>

/** A template's `type` (protocol 5.4, decision D37): a preset of one punishment type, or a ladder. */
export const templateKindSchema = z.enum(['ban', 'mute', 'warn', 'kick', 'ladder'])
export type TemplateKind = z.infer<typeof templateKindSchema>

/** How the mute notice and the warning reach an online player (S61). */
export const templateDisplaySchema = z.enum(['chat', 'dialog'])
export type TemplateDisplay = z.infer<typeof templateDisplaySchema>

/** A ladder step: its text (`warn`, `mute 1h`, `none`) or a map with that text as `step`. */
export const templateStepSchema = z.union([
  z.string(),
  z.looseObject({
    step: z.string(),
    reason: z.string().optional(),
    message: z.string().optional(),
    actions: z.array(z.string()).optional()
  })
])
export type TemplateStepDefinition = z.infer<typeof templateStepSchema>

// Keys in templates.yml order. Loose: a key this site doesn't know is kept (and sent back on update) instead of being
// dropped. Which keys belong to which shape is checked by the editor's validation, as the
// plugin does (it also refuses unknown keys).
export const templateDefinitionSchema = z.looseObject({
  'type': templateKindSchema,
  'ip': z.boolean().optional(),
  'shadow': z.boolean().optional(),
  'scope': z.string().optional(),
  'reason': z.string(),
  'duration': z.string().optional(),
  'steps': z.array(templateStepSchema).optional(),
  'expiry': z.string().optional(),
  'address-shared': z.boolean().optional(),
  'permission-required': z.boolean().optional(),
  'actions': z.array(z.string()).optional(),
  'groups': z.record(z.string(), z.number().int()).optional(),
  'message': z.string().optional(),
  // A text, read as the plugin reads it (any case); this site writes chat or dialog.
  'display': z.string().optional()
})
export type TemplateDefinition = z.infer<typeof templateDefinitionSchema>

/**
 * A template's definition: `scope` is `global` for the global definition, or the server or
 * category of a variant (S62) — where the definition is in force, not the definition's own
 * `scope` key (where its punishments apply).
 */
export const templateItemSchema = z.object({
  scope: z.string().regex(SCOPE_PATTERN),
  name: z.string().min(1),
  version: z.number().int().nonnegative(),
  definition: templateDefinitionSchema
})
export type TemplateItem = z.infer<typeof templateItemSchema>

export const payloadSchema = z.object({
  format: z.literal(1),
  meta: z.object({
    pluginVersion: z.string(),
    platform: z.enum(['paper', 'velocity']),
    server: z.string(),
    uploader: z.object({
      name: z.string(),
      uuid: z.string().nullable()
    }),
    createdAt: z.number().int(),
    sections: z.array(sectionSchema)
  }),
  settings: settingsSectionSchema.optional(),
  messages: z.object({
    fields: z.array(messageFieldSchema),
    locales: z.array(localeFilesSchema),
    scopes: z.array(messageScopeSchema)
  }).optional(),
  templates: z.object({
    items: z.array(templateItemSchema)
  }).optional(),
  lookups: z.object({
    servers: z.array(z.string()),
    categories: z.array(z.string()),
    locales: z.array(z.string())
  })
}).superRefine((payload, ctx) => {
  for (const section of SECTIONS) {
    const listed = payload.meta.sections.includes(section)
    const present = payload[section] !== undefined
    if (listed !== present) {
      ctx.addIssue({
        code: 'custom',
        path: [section],
        message: listed ? 'is listed in meta.sections but missing' : 'is present but not listed in meta.sections'
      })
    }
  }
})
export type Payload = z.infer<typeof payloadSchema>

function addOnce(ctx: z.RefinementCtx, keys: Iterable<string>, seen: Map<string, string>, where: string) {
  for (const key of keys) {
    const before = seen.get(key)
    if (before) {
      ctx.addIssue({ code: 'custom', path: [where], message: `${key} is in both ${before} and ${where}` })
    }
    seen.set(key, where)
  }
}

export const settingsBlockSchema = z.object({
  scope: z.string().regex(SCOPE_PATTERN),
  expectedVersion: z.number().int().nonnegative(),
  set: z.record(z.string(), jsonValueSchema),
  reset: z.array(z.string()),
  publish: z.object({
    server: z.string(),
    keys: z.array(z.string())
  }).optional()
}).superRefine((block, ctx) => {
  const seen = new Map<string, string>()
  addOnce(ctx, Object.keys(block.set), seen, 'set')
  addOnce(ctx, block.reset, seen, 'reset')
  if (block.publish) {
    if (block.scope !== GLOBAL) {
      ctx.addIssue({ code: 'custom', path: ['publish'], message: 'publish is only allowed in the global block' })
    }
    addOnce(ctx, block.publish.keys, seen, 'publish')
  }
})
export type SettingsBlock = z.infer<typeof settingsBlockSchema>

export const messagesBlockSchema = z.object({
  scope: z.string().regex(SCOPE_PATTERN),
  locale: z.string().min(1),
  expectedVersion: z.number().int().nonnegative(),
  set: z.record(z.string(), z.string()),
  reset: z.array(z.string())
}).superRefine((block, ctx) => {
  const seen = new Map<string, string>()
  addOnce(ctx, Object.keys(block.set), seen, 'set')
  addOnce(ctx, block.reset, seen, 'reset')
})
export type MessagesBlock = z.infer<typeof messagesBlockSchema>

export const templateChangeSchema = z.discriminatedUnion('op', [
  z.object({ op: z.literal('create'), scope: z.string(), name: z.string(), definition: templateDefinitionSchema }),
  z.object({
    op: z.literal('update'),
    scope: z.string(),
    name: z.string(),
    expectedVersion: z.number().int().nonnegative(),
    definition: templateDefinitionSchema
  }),
  z.object({ op: z.literal('delete'), scope: z.string(), name: z.string(), expectedVersion: z.number().int().nonnegative() })
])
export type TemplateChange = z.infer<typeof templateChangeSchema>

export const changeSetSchema = z.object({
  format: z.literal(1),
  sequence: z.number().int().positive(),
  settings: z.array(settingsBlockSchema).optional(),
  messages: z.array(messagesBlockSchema).optional(),
  templates: z.array(templateChangeSchema).optional()
}).superRefine((changes, ctx) => {
  const scopes = new Set<string>()
  for (const block of changes.settings ?? []) {
    if (scopes.has(block.scope)) {
      ctx.addIssue({ code: 'custom', path: ['settings'], message: `two blocks for ${block.scope}` })
    }
    scopes.add(block.scope)
  }
  const pairs = new Set<string>()
  for (const block of changes.messages ?? []) {
    const pair = `${block.scope} ${block.locale}`
    if (pairs.has(pair)) {
      ctx.addIssue({ code: 'custom', path: ['messages'], message: `two blocks for ${pair}` })
    }
    pairs.add(pair)
  }
})
export type ChangeSet = z.infer<typeof changeSetSchema>

export const RESULT_ACTIONS = ['set', 'reset', 'published', 'created', 'updated', 'deleted',
  'unchanged', 'conflict'] as const

export const resultSchema = z.object({
  format: z.literal(1),
  sequence: z.number().int().positive(),
  outcome: z.enum(['applied', 'refused']),
  by: z.enum(['live', 'command']),
  changes: z.array(z.object({
    section: sectionSchema,
    scope: z.string(),
    target: z.string(),
    action: z.enum(RESULT_ACTIONS),
    detail: z.string()
  })),
  errors: z.array(z.object({ path: z.string(), message: z.string() })),
  summary: z.object({
    applied: z.number().int().nonnegative(),
    unchanged: z.number().int().nonnegative(),
    conflicts: z.number().int().nonnegative()
  }),
  /** The revision of the fresh payload uploaded after applying; null when there is none. */
  payloadRevision: z.number().int().positive().nullable()
})
export type ApplyResult = z.infer<typeof resultSchema>
export type ResultChange = ApplyResult['changes'][number]
