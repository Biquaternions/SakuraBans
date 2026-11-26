import { describe, expect, it } from 'vitest'
import {
  CLOSE_EXPIRED, CLOSE_POLICY, CLOSE_REPLACED, CLOSE_SILENT, CLOSE_TOO_BIG, SessionHub
} from '../server/lib/sessionHub'

function client() {
  const got: Array<Record<string, unknown>> = []
  const closed: number[] = []
  return {
    got,
    closed,
    peer: {
      send: (text: string) => got.push(JSON.parse(text) as Record<string, unknown>),
      close: (code: number) => closed.push(code)
    }
  }
}

const S = 'session-a'
const LATER = 1e12

describe('SessionHub', () => {
  it('greets each side with the other\'s presence and announces joins and leaves', () => {
    const hub = new SessionHub()
    const browser = client()
    hub.join(browser.peer, S, 'browser', LATER, 0)
    expect(browser.got).toEqual([{ type: 'hello', plugin: false }])

    const plugin = client()
    hub.join(plugin.peer, S, 'plugin', LATER, 0, ['AAAA', 'BBBB'])
    expect(plugin.got).toEqual([{ type: 'hello', browsers: 1, pending: ['AAAA', 'BBBB'] }])
    expect(browser.got.at(-1)).toEqual({ type: 'plugin', connected: true })

    const second = client()
    hub.join(second.peer, S, 'browser', LATER, 0)
    expect(second.got).toEqual([{ type: 'hello', plugin: true }])
    expect(plugin.got.at(-1)).toEqual({ type: 'browsers', count: 2 })

    hub.leave(second.peer)
    expect(plugin.got.at(-1)).toEqual({ type: 'browsers', count: 1 })
    hub.leave(plugin.peer)
    expect(browser.got.at(-1)).toEqual({ type: 'plugin', connected: false })
  })

  it('keeps one plugin socket per session: a new one replaces the old with 4000', () => {
    const hub = new SessionHub()
    const browser = client()
    hub.join(browser.peer, S, 'browser', LATER, 0)
    const first = client()
    hub.join(first.peer, S, 'plugin', LATER, 0)
    const second = client()
    hub.join(second.peer, S, 'plugin', LATER, 0)
    expect(first.closed).toEqual([CLOSE_REPLACED])
    expect(hub.stats(S)).toEqual({ plugin: true, browsers: 1 })
    // The browser never sees the plugin as gone during a replacement.
    expect(browser.got.filter(frame => frame.type === 'plugin')).toEqual([{ type: 'plugin', connected: true }])
    hub.changesSaved(S, 'CODE')
    expect(first.got.some(frame => frame.type === 'changes')).toBe(false)
    expect(second.got.at(-1)).toEqual({ type: 'changes', code: 'CODE' })
    // The replaced socket's late close changes nothing.
    hub.leave(first.peer)
    expect(hub.stats(S)).toEqual({ plugin: true, browsers: 1 })
  })

  it('answers ping, ignores everything else and never relays', () => {
    const hub = new SessionHub()
    const browser = client()
    const plugin = client()
    hub.join(browser.peer, S, 'browser', LATER, 0)
    hub.join(plugin.peer, S, 'plugin', LATER, 0)
    const before = plugin.got.length
    hub.message(browser.peer, '{"type":"ping"}', 10)
    expect(browser.got.at(-1)).toEqual({ type: 'pong' })
    hub.message(browser.peer, '{"type":"result","code":"FAKE"}', 20)
    hub.message(browser.peer, '{"type":"changes","code":"FAKE"}', 30)
    hub.message(browser.peer, 'not json', 40)
    expect(plugin.got.length).toBe(before)
    expect(browser.closed).toEqual([])
  })

  it('closes oversized frames, floods and the sixth browser', () => {
    const hub = new SessionHub()
    const big = client()
    hub.join(big.peer, S, 'browser', LATER, 0)
    hub.message(big.peer, `{"type":"ping","x":"${'a'.repeat(2000)}"}`, 0)
    expect(big.closed).toEqual([CLOSE_TOO_BIG])

    const flood = client()
    hub.join(flood.peer, S, 'browser', LATER, 0)
    for (let i = 0; i < 6; i++) {
      hub.message(flood.peer, '{"type":"ping"}', 100 + i)
    }
    expect(flood.closed).toEqual([CLOSE_POLICY])

    const many = Array.from({ length: 6 }, () => client())
    many.forEach(item => hub.join(item.peer, 'session-b', 'browser', LATER, 0))
    expect(many.slice(0, 5).every(item => item.closed.length === 0)).toBe(true)
    expect(many[5]!.closed).toEqual([CLOSE_POLICY])
    expect(hub.stats('session-b').browsers).toBe(5)
  })

  it('closes sockets silent for 60 s with 4008 and those of expired sessions with 4004', () => {
    const hub = new SessionHub()
    const quiet = client()
    const talker = client()
    const expiring = client()
    hub.join(quiet.peer, S, 'browser', LATER, 0)
    hub.join(talker.peer, S, 'browser', LATER, 0)
    hub.join(expiring.peer, 'session-c', 'plugin', 30_000, 0)
    hub.message(talker.peer, '{"type":"ping"}', 40_000)
    hub.sweep(30_000)
    expect(expiring.closed).toEqual([CLOSE_EXPIRED])
    hub.sweep(60_000)
    expect(quiet.closed).toEqual([CLOSE_SILENT])
    expect(talker.closed).toEqual([])
    expect(hub.stats(S).browsers).toBe(1)
  })

  it('sends results only to the session\'s browsers', () => {
    const hub = new SessionHub()
    const mine = client()
    const other = client()
    hub.join(mine.peer, S, 'browser', LATER, 0)
    hub.join(other.peer, 'session-z', 'browser', LATER, 0)
    hub.resultPosted(S, 'CODE')
    expect(mine.got.at(-1)).toEqual({ type: 'result', code: 'CODE' })
    expect(other.got.some(frame => frame.type === 'result')).toBe(false)
  })
})
