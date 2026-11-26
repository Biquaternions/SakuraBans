// The live apply socket (protocol section 9): a thin crossws adapter around SessionHub.
// The upgrade is refused with a plain HTTP status (404, 401, 400, 429) before any socket opens.

import { checkSocket } from '../../../lib/blobApi'
import type { HubPeer, Role } from '../../../lib/sessionHub'

interface SocketContext {
  session?: string
  role?: Role
  expiresAt?: number
  pending?: string[]
}

// The parts of a crossws peer used here (crossws isn't a direct dependency of this app).
interface Peer {
  id: string
  send: (data: string) => unknown
  close: (code?: number, reason?: string) => void
}

const peers = new Map<string, HubPeer>()

function hubPeer(peer: Peer): HubPeer {
  let found = peers.get(peer.id)
  if (!found) {
    found = {
      send: text => peer.send(text),
      close: (code, reason) => peer.close(code, reason)
    }
    peers.set(peer.id, found)
  }
  return found
}

function address(request: unknown, headers: Headers, trustProxy: boolean): string {
  if (trustProxy) {
    const last = (headers.get('x-forwarded-for') ?? '').split(',').map(part => part.trim()).filter(Boolean).at(-1)
    if (last) {
      return last
    }
  }
  // crossws's Node request proxy keeps the IncomingMessage as _req.
  const socket = (request as { _req?: { socket?: { remoteAddress?: string } } })._req?.socket
  return socket?.remoteAddress ?? 'unknown'
}

function refuse(status: number, error: string, message: string): Response {
  return new Response(JSON.stringify({ error, message }), {
    status,
    headers: { 'content-type': 'application/json', 'cache-control': 'no-store' }
  })
}

export default defineWebSocketHandler({
  async upgrade(request) {
    const url = new URL(request.url)
    const id = /\/api\/sessions\/([^/]+)\/socket$/.exec(url.pathname)?.[1] ?? ''
    const config = editorConfig()
    const check = await checkSocket(editorDeps, config, {
      id,
      role: url.searchParams.get('role'),
      authorization: request.headers.get('authorization'),
      address: address(request, request.headers, config.trustProxy)
    })
    if (!check.ok) {
      return refuse(check.status, check.error, check.message)
    }
    const context = request.context as SocketContext
    context.session = check.session
    context.role = check.role
    context.expiresAt = check.expiresAt
    context.pending = check.pending
  },

  open(peer) {
    const context = peer.context as SocketContext
    if (!context.session || !context.role || context.expiresAt === undefined) {
      peer.close(4004, 'session expired or unknown')
      return
    }
    sessionHub.join(hubPeer(peer), context.session, context.role, context.expiresAt, Date.now(),
      context.pending ?? [])
  },

  message(peer, message) {
    sessionHub.message(hubPeer(peer), message.text(), Date.now())
  },

  close(peer) {
    const found = peers.get(peer.id)
    if (found) {
      sessionHub.leave(found)
      peers.delete(peer.id)
    }
  },

  error(peer) {
    const found = peers.get(peer.id)
    if (found) {
      sessionHub.leave(found)
      peers.delete(peer.id)
    }
  }
})
