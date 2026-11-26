import { fileURLToPath } from 'node:url'

// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  modules: [
    '@nuxt/eslint',
    '@nuxt/ui',
    '@nuxt/content'
  ],

  $production: {
    routeRules: {
      '/**': {
        headers: {
          // Rendered pages replace this with a nonce-based policy (server/plugins/security.ts).
          'content-security-policy': 'default-src \'none\'; frame-ancestors \'none\'',
          'referrer-policy': 'no-referrer',
          'x-content-type-options': 'nosniff',
          'x-frame-options': 'DENY',
          'cross-origin-opener-policy': 'same-origin',
          'cross-origin-resource-policy': 'same-origin',
          'permissions-policy': 'camera=(), microphone=(), geolocation=(), payment=(), usb=()'
        }
      }
    }
  },

  devtools: {
    enabled: false
  },

  css: ['~/assets/css/main.css'],

  content: {
    // The docs' SQLite copy, built at startup; relative to .output/server, which must let the
    // server create .data/content (the Docker image prepares it).
    database: {
      type: 'sqlite',
      filename: './.data/content/contents.sqlite'
    },
    build: {
      markdown: {
        toc: {
          searchDepth: 1
        }
      }
    },
    experimental: {
      sqliteConnector: 'native'
    }
  },

  // No cookies, no external origins: fonts are the system stack, icons are bundled and served
  // by this server only.
  ui: {
    fonts: false,
    eslint: {
      rules: {
        'no-unknown-classes': 'error',
        'no-conflicting-classes': 'error',
        'no-palette-colors': 'error'
      }
    }
  },

  runtimeConfig: {
    editor: {
      // Storage of the blobs: "fs" (a directory) or "redis".
      storageDriver: 'fs',
      storageDir: './.data/editor',
      redisUrl: '',
      // Lifetimes in seconds (protocol section 3).
      payloadTtl: 86400,
      changesTtl: 3600,
      maxBlobBytes: 1048576,
      // Uploads per client address per window (seconds).
      rateLimit: 30,
      rateLimitWindow: 600,
      // presence, watch and result calls per client address per window (seconds).
      liveRateLimit: 120,
      liveRateLimitWindow: 60,
      // Whether the client address is read from X-Forwarded-For (only behind a reverse proxy).
      trustProxy: false,
      // When set, POST /api/sessions needs "Authorization: Bearer <token>".
      uploadToken: '',
      // How often expired blobs are purged (seconds).
      purgeInterval: 600,
      // Live apply sockets: closed after this many seconds without a frame; browsers per session.
      socketSilence: 60,
      socketMaxBrowsers: 5
    },
    public: {
      // A public editor instance to name in the docs; empty names none.
      publicInstanceUrl: ''
    }
  },

  alias: {
    '#protocol': fileURLToPath(new URL('../protocol', import.meta.url))
  },

  routeRules: {
    // The editor holds decrypted configuration: it renders in the browser only.
    '/editor/**': { ssr: false },
    '/api/sessions/**': { headers: { 'cache-control': 'no-store' } },
    '/api/changes/**': { headers: { 'cache-control': 'no-store' } }
  },

  experimental: {
    asyncContext: true
  },

  compatibilityDate: '2026-06-30',

  nitro: {
    experimental: {
      // Live apply (protocol section 9).
      websocket: true
    }
  },

  vite: {
    server: {
      fs: {
        // The demo imports the shared protocol fixture from ../protocol.
        allow: [fileURLToPath(new URL('../protocol', import.meta.url))]
      }
    }
  },

  eslint: {
    config: {
      stylistic: {
        commaDangle: 'never',
        braceStyle: '1tbs'
      }
    }
  },

  icon: {
    provider: 'server',
    fallbackToApi: false,
    clientBundle: {
      scan: true
    }
  }
})
