import { describe, expect, it } from 'vitest'
import { KeyedMutex } from '../server/lib/mutex'
import { isWebSocketUpgrade } from '../server/lib/upgrades'

function request(upgrade?: string) {
  return { headers: upgrade === undefined ? {} : { upgrade } } as Parameters<typeof isWebSocketUpgrade>[0]
}

describe('upgrades', () => {
  it('treats only WebSocket upgrades as upgrades', () => {
    expect(isWebSocketUpgrade(request('websocket'))).toBe(true)
    expect(isWebSocketUpgrade(request('WebSocket'))).toBe(true)
    expect(isWebSocketUpgrade(request('h2c'))).toBe(false)
    expect(isWebSocketUpgrade(request('h2c, websocket'))).toBe(true)
    expect(isWebSocketUpgrade(request())).toBe(false)
  })
})

describe('KeyedMutex', () => {
  it('runs work for one key one at a time, other keys in parallel, and survives errors', async () => {
    const mutex = new KeyedMutex()
    const log: string[] = []
    const step = (name: string, ms: number) => async () => {
      log.push(`${name}+`)
      await new Promise(resolve => setTimeout(resolve, ms))
      log.push(`${name}-`)
      return name
    }
    const failing = mutex.run('a', async () => {
      throw new Error('boom')
    })
    const results = await Promise.all([
      failing.catch(() => 'failed'),
      mutex.run('a', step('a1', 20)),
      mutex.run('a', step('a2', 1)),
      mutex.run('b', step('b1', 5))
    ])
    expect(results).toEqual(['failed', 'a1', 'a2', 'b1'])
    expect(log.indexOf('a1-')).toBeLessThan(log.indexOf('a2+'))
    expect(log.indexOf('b1+')).toBeLessThan(log.indexOf('a1-'))
  })
})
