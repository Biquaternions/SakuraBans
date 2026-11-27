// templates.yml import and export in the browser, in the file format of decision D37 (the
// plugin's core/src/main/resources/defaults/templates.yml).

import { parse, stringify } from 'yaml'
import { readTemplate, templatesToYamlObject, validateTemplate } from './templates'
import type { TemplateForm } from './templates'

export interface YamlImport {
  /** `variant`: `global`, or a variant's server or category (the `variants:` section, S62). */
  templates: Array<TemplateForm & { variant: string }>
  errors: string[]
}

const HEADER = `# SakuraBans templates, exported from the web editor.
# Import them on a server with /sb templates import (see templates.yml for every key).
# Presets (type: ban, mute, warn, kick) are used with -t on the command of their type,
# ladders (type: ladder) with /punish <player> <name>.
`

export function exportTemplatesYaml(forms: ReadonlyArray<TemplateForm & { variant?: string }>): string {
  return HEADER + stringify(templatesToYamlObject(forms), { lineWidth: 0 })
}

/**
 * Reads a templates.yml text as /sb templates import --all does: a template with any error is
 * left out and its errors are listed (`templates.<name>.<key>: <message>`); the others are read,
 * variants from `variants.<scope>.<name>` too.
 * Keys of the format before D37 (`ladder`, `step-actions`, ...) are errors naming the new key.
 */
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
  const templates: Array<TemplateForm & { variant: string }> = []
  const errors: string[] = []
  const read = (rawName: string, raw: unknown, path: string, variant: string) => {
    const name = rawName.toLowerCase()
    const result = readTemplate(name, raw)
    // What didn't read isn't reported again by validation (a step that didn't parse is a ban).
    const unread = (issue: string) => result.errors.some(error => issue === error.path || issue.startsWith(`${error.path}.`))
    const problems = result.form
      ? [...result.errors, ...validateTemplate(result.form, new Set()).filter(issue => !unread(issue.path))]
      : result.errors
    if (result.form && variant !== 'global' && result.form.groups.length > 0) {
      problems.push({ path: 'groups', message: 'a variant counts in its global template\'s groups; set them there' })
    }
    if (problems.length > 0 || !result.form) {
      for (const problem of problems) {
        errors.push(`${problem.path ? `${path}.${problem.path}` : path}: ${problem.message}`)
      }
      return
    }
    templates.push({ ...result.form, variant })
  }
  for (const [rawName, raw] of Object.entries(section as Record<string, unknown>)) {
    read(rawName, raw, `templates.${rawName}`, 'global')
  }
  const variants = (root as Record<string, unknown>).variants
  if (variants !== undefined && variants !== null) {
    if (typeof variants !== 'object' || Array.isArray(variants)) {
      errors.push('variants: must be a section')
    } else {
      for (const [scope, entries] of Object.entries(variants as Record<string, unknown>)) {
        if (!/^(server|category):[a-z0-9_-]{1,64}$/.test(scope)) {
          errors.push(`variants.${scope}: "${scope}" is not a server or category scope (server:<name> or category:<name>)`)
          continue
        }
        if (typeof entries !== 'object' || entries === null || Array.isArray(entries)) {
          errors.push(`variants.${scope}: must be a section`)
          continue
        }
        for (const [rawName, raw] of Object.entries(entries as Record<string, unknown>)) {
          read(rawName, raw, `variants.${scope}.${rawName}`, scope)
        }
      }
    }
  }
  return { templates, errors }
}
