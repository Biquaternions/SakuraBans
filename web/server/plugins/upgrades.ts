import { onlyWebSocketUpgrades } from '../lib/upgrades'

// See server/lib/upgrades.ts: an "Upgrade: h2c" offer on an ordinary request is ignored.
export default defineNitroPlugin(() => {
  onlyWebSocketUpgrades()
})
