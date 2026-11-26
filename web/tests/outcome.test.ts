import { describe, expect, it } from 'vitest'
import {
  buildChangeSet, initialDraft, listChanges, nextSequence, refId, validateDraft
} from '../shared/editor/draft'
import { applyOutcome, groupResult, mapErrorPath, refForResultChange, simulateResult } from '../shared/editor/outcome'
import { reconnectDelay, SaveFlow } from '../shared/editor/saveFlow'
import type { SavePhase } from '../shared/editor/saveFlow'
import { resultSchema } from '../shared/protocol/schema'
import type { ApplyResult, ChangeSet, Payload } from '../shared/protocol/schema'
import { draftFromChanges, fixtureChanges, fixturePayload, fixtureResult } from './fixtures'

const payload = (): Payload => fixturePayload()

describe('sequence', () => {
  it('follows the clock but always grows', () => {
    const draft = initialDraft(payload())
    expect(nextSequence(draft, 1000)).toBe(1000)
    draft.lastSequence = 5000
    expect(nextSequence(draft, 1000)).toBe(5001)
    expect(nextSequence(draft, 9000.7)).toBe(9000)
  })
})

function sectionsOf(changes: ChangeSet): string[] {
  return (['settings', 'messages', 'templates'] as const).filter(section => changes[section] !== undefined)
}

describe('after an applied result', () => {
  it('asks for the fresh snapshot when its revision is newer, and changes nothing yet', () => {
    const p = payload()
    const changes = fixtureChanges()
    const result = fixtureResult()
    const draft = draftFromChanges(p, changes)
    const pendingBefore = listChanges(p, draft)
    const effect = applyOutcome(p, draft, changes, result)
    expect(effect.refreshTo).toBe(result.payloadRevision)
    expect(effect.stale).toEqual([])
    expect(effect.conflicts).toEqual(result.changes.filter(change => change.action === 'conflict').map(refForResultChange))
    expect(listChanges(p, draft)).toEqual(pendingBefore)
  })

  it('doesn\'t refresh to a revision it already has', () => {
    const p = payload()
    const changes = fixtureChanges()
    const draft = draftFromChanges(p, changes)
    draft.revision = 99
    expect(applyOutcome(p, draft, changes, fixtureResult()).refreshTo).toBeNull()
  })

  it('without a fresh snapshot: applied changes stop being pending, sections are marked stale', () => {
    const p = payload()
    const changes = fixtureChanges()
    const result = { ...fixtureResult(), payloadRevision: null }
    const draft = draftFromChanges(p, changes)
    const effect = applyOutcome(p, draft, changes, result)
    expect(effect.refreshTo).toBeNull()
    expect(effect.stale).toEqual(sectionsOf(changes))
    const conflicts = result.changes.filter(change => change.action === 'conflict').map(refForResultChange)
    const pending = listChanges(p, draft)
    expect(pending.map(item => refId(item.ref)).sort()).toEqual(conflicts.map(ref => refId(ref!)).sort())
    expect(pending.every(item => item.stale)).toBe(true)
    // Only the conflicting changes would be sent again; nothing is locked.
    const again = buildChangeSet(p, draft, changes.sequence + 1)
    const sentRefs = [
      ...Object.keys(again.settings?.set ?? {}).map(key => `settings:${key}`),
      ...(again.settings?.reset ?? []).map(key => `settings:${key}`),
      ...(again.templates ?? []).map(change => `templates:${change.name}`)
    ]
    expect(sentRefs.every(ref => conflicts.some(conflict => refId(conflict!) === ref))).toBe(true)
    expect(validateDraft(p, draft)).toEqual([])
  })
})

describe('after a refused result', () => {
  it('keeps every edit pending and maps error paths (block index, key, scope) to fields', () => {
    const p = payload()
    const changes = fixtureChanges()
    const draft = draftFromChanges(p, changes)
    const before = listChanges(p, draft).length
    const blockIndex = changes.settings!.findIndex(block => Object.keys(block.set).length > 0)
    const block = changes.settings![blockIndex]!
    const setKey = Object.keys(block.set)[0]!
    const message = changes.messages![0]!
    const messageKey = Object.keys(message.set)[0]!
    const template = changes.templates!.findIndex(change => change.op !== 'delete')
    const effect = applyOutcome(p, draft, changes, {
      format: 1, sequence: changes.sequence, outcome: 'refused', by: 'live', changes: [],
      errors: [
        { path: `settings[${blockIndex}].set.${setKey}`, message: 'too long' },
        { path: `messages[0].set.${messageKey}`, message: 'bad tag' },
        { path: `templates[${template}].definition.ladder[0]`, message: 'not a duration' },
        { path: 'permissions', message: 'no permission' }
      ],
      summary: { applied: 0, unchanged: 0, conflicts: 0 },
      payloadRevision: null
    })
    expect(listChanges(p, draft)).toHaveLength(before)
    const name = changes.templates![template]!.name
    expect(effect.issues.map(issue => [issue.ref, issue.path])).toEqual([
      [{ section: 'settings', scope: block.scope, key: setKey }, `settings[${block.scope}].set.${setKey}`],
      [{ section: 'messages', scope: message.scope, locale: message.locale, key: messageKey }, `messages[${message.scope}][${message.locale}].set.${messageKey}`],
      [{ section: 'templates', name }, `templates.${name}.ladder[0]`]
    ])
    expect(effect.unmapped).toEqual([{ path: 'permissions', message: 'no permission' }])
  })

  it('maps paths below a key, reset and publish entries by index, and the unindexed global form', () => {
    const p = payload()
    const changes = fixtureChanges()
    const mapKey = p.settings!.fields.find(field => field.type === 'MAP')!.key
    expect(mapErrorPath(p, changes, `settings.set.${mapKey}.something`)?.ref).toEqual({ section: 'settings', scope: 'global', key: mapKey })
    changes.settings!.forEach((block, index) => {
      if (block.reset.length > 0) {
        expect(mapErrorPath(p, changes, `settings[${index}].reset[0]`)?.ref).toEqual({ section: 'settings', scope: block.scope, key: block.reset[0] })
      }
      if (block.publish) {
        expect(mapErrorPath(p, changes, `settings[${index}].publish.keys[0]`)?.ref).toEqual({ section: 'publish', key: block.publish.keys[0] })
      }
    })
    const message = changes.messages![0]!
    if (message.reset.length > 0) {
      expect(mapErrorPath(p, changes, 'messages[0].reset[0]')?.ref).toEqual({ section: 'messages', scope: message.scope, locale: message.locale, key: message.reset[0] })
    }
    expect(mapErrorPath(p, changes, `templates[${changes.templates!.length}].name`)).toBeNull()
  })

  it('reads result entries with their scope, published entries as publish', () => {
    for (const change of fixtureResult().changes) {
      const ref = refForResultChange(change)
      if (change.action === 'published') {
        expect(ref).toEqual({ section: 'publish', key: change.target })
      } else if (change.section !== 'templates') {
        expect(ref).toMatchObject({ section: change.section, scope: change.scope })
      }
    }
  })
})

describe('demo result', () => {
  it('reports every pending change as applied, with scopes, without secrets', () => {
    const p = payload()
    const changes = fixtureChanges()
    const draft = draftFromChanges(p, changes)
    const result = resultSchema.parse(simulateResult(p, draft, changes, 2))
    const pending = listChanges(p, draft)
    expect(result.sequence).toBe(changes.sequence)
    expect(result.payloadRevision).toBe(2)
    expect(result.summary).toEqual({ applied: result.changes.length, unchanged: 0, conflicts: 0 })
    for (const group of groupResult(result)) {
      expect(group.changes.length).toBe(pending.filter(item => item.section === group.section).length
        + pending.filter(item => item.section === group.section && item.kind === 'rename').length)
    }
    for (const block of changes.settings!) {
      for (const key of Object.keys(block.set)) {
        expect(result.changes.find(change => change.target === key && change.scope === block.scope)?.action).toBe('set')
      }
    }
    const secrets = new Set(p.settings!.fields.filter(field => field.secret).map(field => field.key))
    for (const block of changes.settings!) {
      for (const [key, value] of Object.entries(block.set)) {
        if (secrets.has(key)) {
          expect(JSON.stringify(result)).not.toContain(String(value))
        }
      }
    }
    expect(result.changes.some(change => change.action === 'published')).toBe(changes.settings!.some(block => block.publish))
  })
})

describe('save flow', () => {
  function timers() {
    let time = 0
    let queue: Array<{ at: number, run: () => void, id: number }> = []
    let next = 0
    return {
      setTimer: (run: () => void, ms: number) => {
        const id = next++
        queue.push({ at: time + ms, run, id })
        return () => {
          queue = queue.filter(item => item.id !== id)
        }
      },
      async advance(ms: number) {
        const end = time + ms
        for (;;) {
          queue.sort((a, b) => a.at - b.at)
          const due = queue[0]
          if (!due || due.at > end) {
            break
          }
          queue.shift()
          time = due.at
          due.run()
          await flush()
        }
        time = end
      },
      pending: () => queue.length
    }
  }

  async function flush() {
    for (let i = 0; i < 5; i++) {
      await Promise.resolve()
    }
  }

  it('live: applying, then the outcome when the result notice arrives', async () => {
    const clock = timers()
    const phases: string[] = []
    let available: ApplyResult | null = null
    const flow = new SaveFlow('CODE', true, true, {
      setTimer: clock.setTimer, onPhase: phase => phases.push(phase.kind),
      fetchResult: async () => available
    })
    await flush()
    await clock.advance(5000)
    available = fixtureResult()
    flow.notice('code')
    await flush()
    expect(phases).toEqual(['applying', 'outcome'])
    expect(flow.current).toMatchObject({ kind: 'outcome', code: 'CODE' })
    expect(clock.pending()).toBe(0)
  })

  it('live: falls back to the command after 20 s, then shows a late result', async () => {
    const clock = timers()
    const phases: SavePhase['kind'][] = []
    let available: ApplyResult | null = null
    const flow = new SaveFlow('CODE', true, true, {
      setTimer: clock.setTimer, onPhase: phase => phases.push(phase.kind),
      fetchResult: async () => available
    })
    await clock.advance(20_000)
    expect(phases).toEqual(['applying', 'command'])
    available = fixtureResult()
    flow.notice('OTHER')
    await flush()
    expect(flow.current.kind).toBe('command')
    flow.notice('CODE')
    await flush()
    expect(phases).toEqual(['applying', 'command', 'outcome'])
  })

  it('not live: shows the command at once and polls only while the socket is down', async () => {
    const clock = timers()
    const phases: SavePhase['kind'][] = []
    let fetches = 0
    const flow = new SaveFlow('CODE', false, true, {
      setTimer: clock.setTimer, onPhase: phase => phases.push(phase.kind),
      fetchResult: async () => {
        fetches++
        return null
      }
    })
    await flush()
    const afterConnect = fetches
    await clock.advance(30_000)
    expect(fetches).toBe(afterConnect)
    flow.socket(false)
    await clock.advance(15_000)
    expect(fetches).toBe(afterConnect + 3)
    flow.socket(true)
    await flush()
    const afterReconnect = fetches
    await clock.advance(30_000)
    expect(fetches).toBe(afterReconnect)
    flow.stop()
    expect(phases).toEqual(['command', 'stopped'])
    expect(clock.pending()).toBe(0)
  })

  it('reconnects after 2, 5, 15, then every 30 seconds', () => {
    expect([0, 1, 2, 3, 9].map(reconnectDelay)).toEqual([2000, 5000, 15_000, 30_000, 30_000])
  })
})
