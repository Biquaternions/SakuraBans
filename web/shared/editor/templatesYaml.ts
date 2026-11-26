// templates.yml import and export in the browser.

import { parse, stringify } from 'yaml'
import type { TemplateDefinition } from '../protocol/schema'
import { templateDefinitionSchema } from '../protocol/schema'
import { removeKey } from '../json'
import { definitionToForm, templatesToYamlObject } from './templates'
import type { TemplateForm } from './templates'

export interface YamlImport {
  templates: TemplateForm[]
  errors: string[]
}

const HEADER = `# SakuraBans templates, exported from the web editor.
# Import them on a server with /sb templates import (see templates.yml for every key).
`

export function exportTemplatesYaml(forms: readonly TemplateForm[]): string {
  return HEADER + stringify(templatesToYamlObject(forms), { lineWidth: 0 })
}

function asText(value: unknown): string | null {
  if (typeof value === 'string') {
    return value
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value)
  }
  return null
}

function textList(value: unknown, path: string, errors: string[]): string[] | undefined {
  if (value === undefined || value === null) {
    return undefined
  }
  if (!Array.isArray(value)) {
    errors.push(`${path}: must be a list`)
    return undefined
  }
  const out: string[] = []
  for (const item of value) {
    const text = asText(item)
    if (text === null) {
      errors.push(`${path}: must be a list of texts`)
      return undefined
    }
    out.push(text)
  }
  return out
}

/** Reads a templates.yml text; templates with errors are left out and listed in `errors`. */
export function importTemplatesYaml(text: string): YamlImport {
  let root: unknown
  try {
    root = parse(text)
  } catch (error) {
    return { templates: [], errors: [`Not valid YAML: ${(error as Error).message}`] }
  }
  const section = typeof root === 'object' && root !== null
    ? (root as Record<string, unknown>).templates
    : undefined
  if (typeof section !== 'object' || section === null || Array.isArray(section)) {
    return { templates: [], errors: ['The file has no "templates:" section.'] }
  }
  const templates: TemplateForm[] = []
  const errors: string[] = []
  for (const [name, raw] of Object.entries(section as Record<string, unknown>)) {
    const path = `templates.${name}`
    if (typeof raw !== 'object' || raw === null || Array.isArray(raw)) {
      errors.push(`${path}: must be a section`)
      continue
    }
    const before = errors.length
    const node = raw as Record<string, unknown>
    const candidate: Record<string, unknown> = { ...node }
    candidate.type = typeof node.type === 'string' ? node.type.trim().toLowerCase() : node.type
    candidate.reason = asText(node.reason) ?? ''
    candidate.ladder = textList(node.ladder, `${path}.ladder`, errors) ?? []
    for (const key of ['ladder-expiry', 'scope']) {
      if (node[key] !== undefined && node[key] !== null) {
        candidate[key] = asText(node[key]) ?? node[key]
      } else {
        removeKey(candidate, key)
      }
    }
    const actions = textList(node.actions, `${path}.actions`, errors)
    if (actions === undefined) {
      delete candidate.actions
    } else {
      candidate.actions = actions
    }
    if (node['step-actions'] !== undefined && node['step-actions'] !== null) {
      const steps = node['step-actions']
      if (typeof steps !== 'object' || Array.isArray(steps)) {
        errors.push(`${path}.step-actions: must be a section`)
      } else {
        const converted: Record<string, string[]> = {}
        for (const [step, list] of Object.entries(steps as Record<string, unknown>)) {
          converted[step] = textList(list, `${path}.step-actions.${step}`, errors) ?? []
        }
        candidate['step-actions'] = converted
      }
    } else {
      delete candidate['step-actions']
    }
    if (node.groups === null) {
      delete candidate.groups
    }
    if (node.message === null || node.message === undefined) {
      removeKey(candidate, 'message')
    } else {
      candidate.message = asText(node.message) ?? node.message
    }
    const stepMessages = node['step-messages']
    if (stepMessages === null || stepMessages === undefined) {
      removeKey(candidate, 'step-messages')
    } else if (typeof stepMessages !== 'object' || Array.isArray(stepMessages)) {
      errors.push(`${path}.step-messages: must be a section`)
    } else {
      candidate['step-messages'] = Object.fromEntries(Object.entries(stepMessages as Record<string, unknown>)
        .map(([step, text]) => [step, asText(text) ?? text]))
    }
    const parsed = templateDefinitionSchema.safeParse(candidate)
    if (!parsed.success) {
      for (const issue of parsed.error.issues) {
        errors.push(`${path}.${issue.path.join('.')}: ${issue.message}`)
      }
      continue
    }
    if (errors.length > before) {
      continue
    }
    const definition = parsed.data as TemplateDefinition
    if (definition['ladder-expiry']?.trim().toLowerCase() === 'none') {
      removeKey(definition, 'ladder-expiry')
    }
    templates.push(definitionToForm(name.toLowerCase(), definition))
  }
  return { templates, errors }
}
