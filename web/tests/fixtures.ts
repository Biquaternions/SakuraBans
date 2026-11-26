// The protocol fixtures, read from ../protocol/fixtures (shared with the plugin's tests).

import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { initialDraft } from '../shared/editor/draft'
import type { EditorDraft } from '../shared/editor/draft'
import { definitionToForm } from '../shared/editor/templates'
import { changeSetSchema, payloadSchema, resultSchema } from '../shared/protocol/schema'
import type { ApplyResult, ChangeSet, Payload } from '../shared/protocol/schema'

const DIRECTORY = fileURLToPath(new URL('../../protocol/fixtures/', import.meta.url))

export function fixtureBytes(name: string): Uint8Array {
  return new Uint8Array(readFileSync(DIRECTORY + name))
}

export function fixtureJson(name: string): unknown {
  return JSON.parse(readFileSync(DIRECTORY + name, 'utf8')) as unknown
}

export interface CryptoFixture {
  key: string
  payload: { aad: string, plaintextFile: string, blob: string }
  changes: { aad: string, plaintextFile: string, blob: string }
  result: { aad: string, plaintextFile: string, blob: string }
}

export function cryptoFixture(): CryptoFixture {
  return fixtureJson('crypto-v1.json') as CryptoFixture
}

// ---- Drafts derived from the fixtures (no key or name is written in the tests) ----

export function fixturePayload(): Payload {
  return payloadSchema.parse(fixtureJson('payload-v1.json'))
}

export function fixtureChanges(): ChangeSet {
  return changeSetSchema.parse(fixtureJson('changes-v1.json'))
}

export function fixtureResult(): ApplyResult {
  return resultSchema.parse(fixtureJson('result-v1.json'))
}

/** Does through the editor's model what the change set says, as a user would. */
export function draftFromChanges(payload: Payload, changes: ChangeSet): EditorDraft {
  const draft = initialDraft(payload)
  for (const block of changes.settings ?? []) {
    const edits = (draft.settings[block.scope] ??= {})
    for (const [key, value] of Object.entries(block.set)) {
      edits[key] = { kind: 'set', value }
    }
    for (const key of block.reset) {
      edits[key] = { kind: 'reset' }
    }
    if (block.publish) {
      draft.publish = { server: block.publish.server, keys: [...block.publish.keys] }
    }
  }
  const knownLocales = new Set([...(payload.messages?.locales ?? []).map(item => item.locale),
    ...(payload.messages?.scopes ?? []).map(item => item.locale)])
  for (const block of changes.messages ?? []) {
    if (!knownLocales.has(block.locale) && !draft.addedLocales.includes(block.locale)) {
      draft.addedLocales.push(block.locale)
    }
    const edits = ((draft.messages[block.scope] ??= {})[block.locale] ??= {})
    for (const [key, text] of Object.entries(block.set)) {
      edits[key] = { kind: 'set', text }
    }
    for (const key of block.reset) {
      edits[key] = { kind: 'reset' }
    }
  }
  for (const change of changes.templates ?? []) {
    const index = draft.templates.findIndex(form => form.name === change.name)
    if (change.op === 'delete') {
      draft.templates.splice(index, 1)
    } else if (change.op === 'update') {
      draft.templates[index] = { ...definitionToForm(change.name, change.definition), origin: change.name }
    } else {
      draft.templates.push({ ...definitionToForm(change.name, change.definition), origin: null })
    }
  }
  return draft
}
