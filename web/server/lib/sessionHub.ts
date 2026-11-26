// Live apply notices (protocol section 9): who is connected to which session, and what the
// site tells them. The site originates every frame itself; it never relays a client's frame.
// Plain module, no network: the WebSocket route is a thin adapter around it.

export type Role = 'browser' | 'plugin'

export interface HubPeer {
  send: (text: string) => void
  close: (code: number, reason: string) => void
}

export const CLOSE_REPLACED = 4000
export const CLOSE_EXPIRED = 4004
export const CLOSE_SILENT = 4008
/** Standard "policy violation": too many frames, too many browsers. */
export const CLOSE_POLICY = 1008
/** Standard "message too big". */
export const CLOSE_TOO_BIG = 1009

export interface HubOptions {
  maxBrowsers: number
  maxFrameBytes: number
  maxFramesPerSecond: number
  silenceMs: number
}

export const DEFAULT_HUB_OPTIONS: HubOptions = {
  maxBrowsers: 5,
  maxFrameBytes: 1024,
  maxFramesPerSecond: 5,
  silenceMs: 60_000
}

interface Connection {
  peer: HubPeer
  session: string
  role: Role
  expiresAt: number
  lastSeen: number
  windowStart: number
  frames: number
  closed: boolean
}

interface SessionPeers {
  plugin: Connection | null
  browsers: Set<Connection>
}

export class SessionHub {
  private readonly sessions = new Map<string, SessionPeers>()
  private readonly connections = new Map<HubPeer, Connection>()
  private options: HubOptions

  constructor(options: Partial<HubOptions> = {}) {
    this.options = { ...DEFAULT_HUB_OPTIONS, ...options }
  }

  configure(options: Partial<HubOptions>): void {
    this.options = { ...this.options, ...options }
  }

  get silenceMs(): number {
    return this.options.silenceMs
  }

  private peers(session: string): SessionPeers {
    let peers = this.sessions.get(session)
    if (!peers) {
      peers = { plugin: null, browsers: new Set() }
      this.sessions.set(session, peers)
    }
    return peers
  }

  private static send(connection: Connection | null, frame: object) {
    if (connection && !connection.closed) {
      connection.peer.send(JSON.stringify(frame))
    }
  }

  private close(connection: Connection, code: number, reason: string) {
    if (connection.closed) {
      return
    }
    connection.closed = true
    this.detach(connection)
    connection.peer.close(code, reason)
  }

  /** A socket opened (the upgrade checks passed). `pending`: codes without a result yet. */
  join(peer: HubPeer, session: string, role: Role, expiresAt: number, now: number,
    pending: readonly string[] = []): void {
    const peers = this.peers(session)
    const connection: Connection = {
      peer, session, role, expiresAt, lastSeen: now, windowStart: now, frames: 0, closed: false
    }
    if (role === 'browser') {
      if (peers.browsers.size >= this.options.maxBrowsers) {
        connection.closed = true
        peer.close(CLOSE_POLICY, 'too many browsers for this session')
        return
      }
      this.connections.set(peer, connection)
      peers.browsers.add(connection)
      SessionHub.send(connection, { type: 'hello', plugin: peers.plugin !== null })
      SessionHub.send(peers.plugin, { type: 'browsers', count: peers.browsers.size })
      return
    }
    const previous = peers.plugin
    if (previous) {
      previous.closed = true
      this.connections.delete(previous.peer)
      peers.plugin = null
      previous.peer.close(CLOSE_REPLACED, 'replaced by a newer plugin connection')
    }
    this.connections.set(peer, connection)
    peers.plugin = connection
    SessionHub.send(connection, { type: 'hello', browsers: peers.browsers.size, pending: [...pending] })
    if (!previous) {
      for (const browser of peers.browsers) {
        SessionHub.send(browser, { type: 'plugin', connected: true })
      }
    }
  }

  /** A socket closed, for whatever reason. */
  leave(peer: HubPeer): void {
    const connection = this.connections.get(peer)
    if (connection) {
      connection.closed = true
      this.detach(connection)
    }
  }

  private detach(connection: Connection) {
    if (this.connections.get(connection.peer) !== connection) {
      return
    }
    this.connections.delete(connection.peer)
    const peers = this.sessions.get(connection.session)
    if (!peers) {
      return
    }
    if (connection.role === 'browser') {
      peers.browsers.delete(connection)
      SessionHub.send(peers.plugin, { type: 'browsers', count: peers.browsers.size })
    } else if (peers.plugin === connection) {
      peers.plugin = null
      for (const browser of peers.browsers) {
        SessionHub.send(browser, { type: 'plugin', connected: false })
      }
    }
    if (!peers.plugin && peers.browsers.size === 0) {
      this.sessions.delete(connection.session)
    }
  }

  /** A frame from a client. Only `ping` means anything; the rest is ignored. */
  message(peer: HubPeer, text: string, now: number): void {
    const connection = this.connections.get(peer)
    if (!connection) {
      return
    }
    if (text.length > this.options.maxFrameBytes) {
      this.close(connection, CLOSE_TOO_BIG, 'frame too large')
      return
    }
    if (now - connection.windowStart >= 1000) {
      connection.windowStart = now
      connection.frames = 0
    }
    connection.frames++
    if (connection.frames > this.options.maxFramesPerSecond) {
      this.close(connection, CLOSE_POLICY, 'too many frames')
      return
    }
    connection.lastSeen = now
    let frame: unknown
    try {
      frame = JSON.parse(text)
    } catch {
      return
    }
    if (typeof frame === 'object' && frame !== null && (frame as { type?: unknown }).type === 'ping') {
      SessionHub.send(connection, { type: 'pong' })
    }
  }

  /** `POST …/changes` stored a change set. */
  changesSaved(session: string, code: string): void {
    SessionHub.send(this.sessions.get(session)?.plugin ?? null, { type: 'changes', code })
  }

  /** `POST …/result` stored a result. */
  resultPosted(session: string, code: string): void {
    for (const browser of this.sessions.get(session)?.browsers ?? []) {
      SessionHub.send(browser, { type: 'result', code })
    }
  }

  /** Closes silent sockets (4008) and those of expired sessions (4004). */
  sweep(now: number): void {
    for (const connection of [...this.connections.values()]) {
      if (now >= connection.expiresAt) {
        this.close(connection, CLOSE_EXPIRED, 'session expired')
      } else if (now - connection.lastSeen >= this.options.silenceMs) {
        this.close(connection, CLOSE_SILENT, 'silent too long')
      }
    }
  }

  stats(session: string): { plugin: boolean, browsers: number } {
    const peers = this.sessions.get(session)
    return { plugin: Boolean(peers?.plugin), browsers: peers?.browsers.size ?? 0 }
  }
}
