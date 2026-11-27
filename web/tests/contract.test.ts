import { describe, expect, it } from 'vitest'
import { buildChangeSet, initialDraft } from '../shared/editor/draft'
import { simulateResult } from '../shared/editor/outcome'
import { definitionToForm, formToDefinition } from '../shared/editor/templates'
import { decodeBase64 } from '../shared/protocol/base64'
import { aadFor } from '../shared/protocol/constants'
import { readPayload } from '../shared/protocol/format'
import { changeSetSchema, payloadSchema, resultSchema } from '../shared/protocol/schema'
import type { TemplateDefinition } from '../shared/protocol/schema'
import { openBytes, openJson, parseKey, SealError, sealBytes, sealJson } from '../shared/protocol/seal'
import { cryptoFixture, draftFromChanges, fixtureBytes, fixtureChanges, fixtureJson, fixturePayload, fixtureResult } from './fixtures'

describe('payload fixture', () => {
  it('parses with the payload schema', () => {
    const result = payloadSchema.safeParse(fixtureJson('payload-v1.json'))
    expect(result.error?.issues ?? []).toEqual([])
    expect(result.data?.settings?.fields.length).toBeGreaterThan(0)
    expect(result.data?.settings?.scopes[0]?.scope).toBe('global')
  })

  it('is read as format 1', () => {
    expect(readPayload(fixtureJson('payload-v1.json')).ok).toBe(true)
  })

  it('reports newer and older formats', () => {
    const payload = fixtureJson('payload-v1.json') as Record<string, unknown>
    expect(readPayload({ ...payload, format: 2 })).toMatchObject({ ok: false, reason: 'newer-format', format: 2 })
    expect(readPayload({ ...payload, format: 0 })).toMatchObject({ ok: false, reason: 'older-format', format: 0 })
    expect(readPayload({ ...payload, format: 'x' })).toMatchObject({ ok: false, reason: 'invalid' })
  })

  it('refuses a value of the wrong type and an unlisted section', () => {
    const payload = structuredClone(fixtureJson('payload-v1.json')) as {
      settings: { fields: Array<{ key: string, type: string, secret: boolean }>, scopes: Array<{ overrides: Record<string, unknown> }> }
    }
    const field = payload.settings.fields.find(item => !item.secret)!
    payload.settings.scopes[0]!.overrides[field.key] = field.type === 'MAP' ? 3 : { wrong: true }
    expect(payloadSchema.safeParse(payload).success).toBe(false)
    const secret = structuredClone(fixtureJson('payload-v1.json')) as typeof payload
    const secretField = secret.settings.fields.find(item => item.secret)
    if (secretField) {
      secret.settings.scopes[0]!.overrides[secretField.key] = 'https://leaked.example'
      expect(payloadSchema.safeParse(secret).success).toBe(false)
    }
    const unlisted = structuredClone(fixtureJson('payload-v1.json')) as { meta: { sections: string[] } }
    unlisted.meta.sections = ['settings', 'messages']
    expect(payloadSchema.safeParse(unlisted).success).toBe(false)
  })
})

describe('change set fixture', () => {
  it('equals the change set built from the payload with the same edits', () => {
    const payload = fixturePayload()
    const expected = fixtureChanges()
    const draft = draftFromChanges(payload, expected)
    const built = buildChangeSet(payload, draft, expected.sequence)
    expect(changeSetSchema.parse(built)).toEqual(expected)
  })

  it('leaves out edits that change nothing', () => {
    const payload = fixturePayload()
    const expected = fixtureChanges()
    const draft = draftFromChanges(payload, expected)
    // Set an override to the value it already has, and reset a key that has no override.
    const scope = payload.settings!.scopes.find(item => Object.keys(item.overrides).length > 0 && item.scope !== 'global')!
    const [key, value] = Object.entries(scope.overrides).find(([k]) => !(k in (draft.settings[scope.scope] ?? {})))
      ?? Object.entries(scope.overrides)[0]!
    if (!(key in (draft.settings[scope.scope] ?? {}))) {
      (draft.settings[scope.scope] ??= {})[key] = { kind: 'set', value }
    }
    const unset = payload.settings!.fields.find(field => !(field.key in payload.settings!.scopes[0]!.overrides)
      && !(field.key in (draft.settings.global ?? {})) && !(draft.publish?.keys.includes(field.key)))!
    draft.settings.global![unset.key] = { kind: 'reset' }
    expect(changeSetSchema.parse(buildChangeSet(payload, draft, expected.sequence))).toEqual(expected)
  })

  it('is empty when nothing changed', () => {
    const payload = payloadSchema.parse(fixtureJson('payload-v1.json'))
    expect(buildChangeSet(payload, initialDraft(payload), 5)).toEqual({ format: 1, sequence: 5 })
  })

  it('rejects a key in both set and reset', () => {
    const block = { scope: 'global', expectedVersion: 1, set: { a: 1 }, reset: ['a'] }
    expect(changeSetSchema.safeParse({ format: 1, sequence: 1, settings: [block] }).success).toBe(false)
    const publish = { scope: 'global', expectedVersion: 1, set: { a: 1 }, reset: [], publish: { server: 's', keys: ['a'] } }
    expect(changeSetSchema.safeParse({ format: 1, sequence: 1, settings: [publish] }).success).toBe(false)
    const scoped = { scope: 'server:s', expectedVersion: 0, set: {}, reset: [], publish: { server: 's', keys: ['b'] } }
    expect(changeSetSchema.safeParse({ format: 1, sequence: 1, settings: [scoped] }).success).toBe(false)
    const twice = { scope: 'global', expectedVersion: 1, set: { b: 1 }, reset: [] }
    expect(changeSetSchema.safeParse({ format: 1, sequence: 1, settings: [twice, twice] }).success).toBe(false)
  })

  it('writes every definition of the fixtures byte for byte as the plugin does', () => {
    const raw = [
      ...(fixtureJson('payload-v1.json') as { templates: { items: Array<{ name: string, definition: TemplateDefinition }> } }).templates.items,
      ...(fixtureJson('changes-v1.json') as { templates: Array<{ name: string, definition?: TemplateDefinition }> }).templates
    ]
    for (const item of raw) {
      if (item.definition) {
        expect(JSON.stringify(formToDefinition(definitionToForm(item.name, item.definition)))).toBe(JSON.stringify(item.definition))
      }
    }
  })

  it('names the changed keys of a template update as the result fixture does', () => {
    const payload = fixturePayload()
    const changes = fixtureChanges()
    const draft = draftFromChanges(payload, changes)
    const simulated = simulateResult(payload, draft, changes)
    for (const line of fixtureResult().changes.filter(change => change.section === 'templates' && change.action === 'updated')) {
      expect(simulated.changes.find(change => change.target === line.target)).toMatchObject({ action: 'updated', detail: line.detail })
    }
  })

  it('keeps definitions round-tripping through the form model', () => {
    const payload = payloadSchema.parse(fixtureJson('payload-v1.json'))
    for (const item of payload.templates!.items) {
      expect(definitionToForm(item.name, item.definition)).toBeTruthy()
    }
    expect(buildChangeSet(payload, initialDraft(payload)).templates).toBeUndefined()
  })
})

describe('result fixture', () => {
  it('parses with the result schema', () => {
    const result = resultSchema.safeParse(fixtureJson('result-v1.json'))
    expect(result.error?.issues ?? []).toEqual([])
    expect(result.data?.changes.length).toBe((fixtureJson('result-v1.json') as { changes: unknown[] }).changes.length)
  })

  it('refuses unknown actions and a missing sequence', () => {
    const result = structuredClone(fixtureJson('result-v1.json')) as { changes: Array<{ action: string }>, sequence?: number }
    result.changes[0]!.action = 'exploded'
    expect(resultSchema.safeParse(result).success).toBe(false)
    const noSequence = structuredClone(fixtureJson('result-v1.json')) as { sequence?: number }
    delete noSequence.sequence
    expect(resultSchema.safeParse(noSequence).success).toBe(false)
  })
})

describe('sealing', () => {
  const fixture = cryptoFixture()
  const key = parseKey(fixture.key)!

  it('uses the AAD texts of the fixture', () => {
    expect(aadFor('payload')).toBe(fixture.payload.aad)
    expect(aadFor('changes')).toBe(fixture.changes.aad)
    expect(aadFor('result')).toBe(fixture.result.aad)
  })

  it('opens the fixture result blob to the result file bytes, only as a result', async () => {
    const blob = decodeBase64(fixture.result.blob)!
    expect(await openBytes(key, 'result', blob)).toEqual(fixtureBytes(fixture.result.plaintextFile))
    await expect(openBytes(key, 'changes', blob)).rejects.toBeInstanceOf(SealError)
    await expect(openBytes(key, 'result', decodeBase64(fixture.changes.blob)!)).rejects.toBeInstanceOf(SealError)
  })

  it('opens the fixture payload blob to the payload file bytes', async () => {
    const bytes = await openBytes(key, 'payload', decodeBase64(fixture.payload.blob)!)
    expect(bytes).toEqual(fixtureBytes(fixture.payload.plaintextFile))
  })

  it('opens the fixture changes blob to the changes file bytes', async () => {
    const bytes = await openBytes(key, 'changes', decodeBase64(fixture.changes.blob)!)
    expect(bytes).toEqual(fixtureBytes(fixture.changes.plaintextFile))
  })

  it('round-trips seal and open', async () => {
    const value = { format: 1, hello: 'wörld', list: [1, 2, 3] }
    const blob = await sealJson(key, 'changes', value)
    expect(blob[0]).toBe(1)
    expect(await openJson(key, 'changes', blob)).toEqual(value)
  })

  it('uses a fresh nonce for every blob', async () => {
    const a = await sealJson(key, 'payload', { a: 1 })
    const b = await sealJson(key, 'payload', { a: 1 })
    expect(a.subarray(1, 13)).not.toEqual(b.subarray(1, 13))
  })

  it('fails with a wrong key', async () => {
    const other = new Uint8Array(32).fill(7)
    await expect(openBytes(other, 'payload', decodeBase64(fixture.payload.blob)!)).rejects.toBeInstanceOf(SealError)
  })

  it('fails with the wrong role', async () => {
    await expect(openBytes(key, 'changes', decodeBase64(fixture.payload.blob)!)).rejects.toBeInstanceOf(SealError)
  })

  it('fails when a bit is flipped', async () => {
    const blob = decodeBase64(fixture.payload.blob)!
    for (const index of [1, 20, blob.length - 1]) {
      const tampered = blob.slice()
      tampered[index]! ^= 0x01
      await expect(openBytes(key, 'payload', tampered)).rejects.toBeInstanceOf(SealError)
    }
  })

  it('fails for another sealing version and short blobs', async () => {
    const blob = decodeBase64(fixture.payload.blob)!.slice()
    blob[0] = 2
    await expect(openBytes(key, 'payload', blob)).rejects.toBeInstanceOf(SealError)
    await expect(openBytes(key, 'payload', new Uint8Array([1, 2, 3]))).rejects.toBeInstanceOf(SealError)
  })

  it('seals with a given nonce reproducibly', async () => {
    const nonce = new Uint8Array(12).fill(9)
    const plaintext = new TextEncoder().encode('{}')
    const a = await sealBytes(key, 'payload', plaintext, nonce)
    expect(await openBytes(key, 'payload', a)).toEqual(plaintext)
  })

  it('parses keys strictly', () => {
    expect(parseKey(fixture.key)).toHaveLength(32)
    expect(parseKey('short')).toBeNull()
    expect(parseKey(`${fixture.key}AA`)).toBeNull()
    expect(parseKey('A+/' + fixture.key.slice(3))).toBeNull()
  })
})
