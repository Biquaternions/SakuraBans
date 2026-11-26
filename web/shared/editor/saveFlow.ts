// After a save (protocol section 9, "Browser"): wait up to 20 s for the `result` notice when
// the plugin is connected, otherwise (or then) show the command and keep listening. Only while
// the browser's own socket is down, ask `GET …/result` every 5 s instead.

import type { ApplyResult } from '../protocol/schema'

export const LIVE_WAIT_MS = 20_000
export const FALLBACK_POLL_MS = 5_000

export type SavePhase
  = | { kind: 'applying', code: string }
    | { kind: 'command', code: string }
    | { kind: 'outcome', code: string, result: ApplyResult }
    | { kind: 'stopped', code: string }

export interface SaveFlowDeps {
  /** The result for the code, opened; null while there is none. May throw (retried later). */
  fetchResult: (code: string) => Promise<ApplyResult | null>
  onPhase: (phase: SavePhase) => void
  /** Schedules a callback; returns a cancel function. */
  setTimer: (callback: () => void, ms: number) => () => void
}

export class SaveFlow {
  private phase: SavePhase
  private cancelLiveWait: (() => void) | null = null
  private cancelPoll: (() => void) | null = null
  private fetching = false

  constructor(readonly code: string, live: boolean, socketUp: boolean, private readonly deps: SaveFlowDeps) {
    this.phase = live ? { kind: 'applying', code } : { kind: 'command', code }
    deps.onPhase(this.phase)
    if (live) {
      this.cancelLiveWait = deps.setTimer(() => {
        this.cancelLiveWait = null
        if (this.phase.kind === 'applying') {
          this.set({ kind: 'command', code })
        }
      }, LIVE_WAIT_MS)
    }
    this.socket(socketUp)
  }

  get current(): SavePhase {
    return this.phase
  }

  private get active(): boolean {
    return this.phase.kind === 'applying' || this.phase.kind === 'command'
  }

  private set(phase: SavePhase) {
    this.phase = phase
    this.deps.onPhase(phase)
    if (!this.active) {
      this.cancelLiveWait?.()
      this.cancelPoll?.()
      this.cancelLiveWait = null
      this.cancelPoll = null
    }
  }

  /** The socket announced a result. */
  notice(code: string): void {
    if (this.active && code.toUpperCase() === this.code.toUpperCase()) {
      void this.fetch()
    }
  }

  /** The browser's socket went up or down. */
  socket(up: boolean): void {
    if (!this.active) {
      return
    }
    if (up) {
      this.cancelPoll?.()
      this.cancelPoll = null
      // A result may have been announced while the socket was down.
      void this.fetch()
    } else if (this.cancelPoll === null) {
      this.schedulePoll()
    }
  }

  private schedulePoll() {
    this.cancelPoll = this.deps.setTimer(() => {
      this.cancelPoll = null
      void this.fetch().then(() => {
        if (this.active && this.cancelPoll === null) {
          this.schedulePoll()
        }
      })
    }, FALLBACK_POLL_MS)
  }

  private async fetch(): Promise<void> {
    if (this.fetching || !this.active) {
      return
    }
    this.fetching = true
    try {
      const result = await this.deps.fetchResult(this.code)
      if (result && this.active) {
        this.set({ kind: 'outcome', code: this.code, result })
      }
    } catch {
      // Retried on the next notice or poll.
    } finally {
      this.fetching = false
    }
  }

  /** The dialog was closed: stop listening. */
  stop(): void {
    if (this.active) {
      this.set({ kind: 'stopped', code: this.code })
    }
  }
}

/** Browser reconnect delays (protocol section 9): 2, 5, 15, then every 30 seconds. */
export function reconnectDelay(attempt: number): number {
  return [2_000, 5_000, 15_000][attempt] ?? 30_000
}

export const PING_INTERVAL_MS = 20_000
