// Mounts the blob storage from runtime config (so Redis needs no rebuild) and purges expired
// blobs on a timer.

import fsLiteDriver from 'unstorage/drivers/fs-lite'
import redisDriver from 'unstorage/drivers/redis'
import { purgeExpired } from '../lib/blobApi'

export default defineNitroPlugin((nitroApp) => {
  const config = editorConfig()
  const storage = useStorage()
  if (config.storageDriver === 'redis') {
    if (!config.redisUrl) {
      throw new Error('NUXT_EDITOR_REDIS_URL must be set when NUXT_EDITOR_STORAGE_DRIVER is redis')
    }
    storage.mount(EDITOR_STORAGE, redisDriver({ url: config.redisUrl, base: 'sakurabans-editor' }))
  } else {
    storage.mount(EDITOR_STORAGE, fsLiteDriver({ base: config.storageDir }))
  }

  const purge = () => {
    purgeExpired(useStorage(EDITOR_STORAGE), Date.now()).catch((error: unknown) => {
      console.error('[editor] purging expired blobs failed:', error)
    })
  }
  // Live apply sockets: close silent ones and those of expired sessions.
  sessionHub.configure({ silenceMs: config.socketSilence * 1000, maxBrowsers: config.socketMaxBrowsers })
  const sweeper = setInterval(() => sessionHub.sweep(Date.now()),
    Math.max(500, Math.min(10_000, sessionHub.silenceMs / 4)))
  sweeper.unref?.()
  nitroApp.hooks.hook('close', () => clearInterval(sweeper))

  if (config.purgeInterval > 0) {
    const timer = setInterval(purge, config.purgeInterval * 1000)
    timer.unref?.()
    nitroApp.hooks.hook('close', () => clearInterval(timer))
  }
  purge()
})
