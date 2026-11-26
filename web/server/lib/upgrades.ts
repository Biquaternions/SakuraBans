// Only WebSocket upgrades are upgrades here. Node's HTTP server hands every request that
// carries "Connection: Upgrade" to the `upgrade` listener (the WebSocket adapter), which refuses
// anything but a WebSocket GET; so a client offering "Upgrade: h2c" on a plain POST got
// "405 Invalid HTTP method". RFC 9110 (7.8) lets a server ignore an upgrade it doesn't want,
// which Node allows through `shouldUpgradeCallback`: other upgrades become ordinary requests.
// Nitro creates its server after the plugins ran, so the callback is set when it listens.

import { Server } from 'node:http'
import type { IncomingMessage } from 'node:http'

export function isWebSocketUpgrade(request: IncomingMessage): boolean {
  return String(request.headers.upgrade ?? '').split(',')
    .some(protocol => protocol.trim().toLowerCase() === 'websocket')
}

const PATCHED = Symbol.for('sakurabans.upgrades')

/** Makes every Node HTTP server created afterwards treat only WebSocket upgrades as upgrades. */
export function onlyWebSocketUpgrades(): void {
  const prototype = Server.prototype as Server & { [PATCHED]?: boolean }
  if (prototype[PATCHED]) {
    return
  }
  prototype[PATCHED] = true
  const listen = prototype.listen
  prototype.listen = function (this: Server, ...args: unknown[]) {
    (this as Server & { shouldUpgradeCallback?: (request: IncomingMessage) => boolean })
      .shouldUpgradeCallback = isWebSocketUpgrade
    return (listen as (...rest: unknown[]) => Server).apply(this, args)
  } as typeof listen
}
