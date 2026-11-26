// Security headers. Every response gets a deny-all CSP (route rules in nuxt.config); rendered
// HTML gets one with a per-request nonce for Nuxt's own inline scripts. No third-party origin
// is allowed anywhere: the editor holds the decryption key in the page.

import { randomBytes } from 'node:crypto'
import { getRequestHost, getRequestProtocol } from 'h3'

export function htmlPolicy(nonce: string, host = '', secure = true): string {
  // 'self' covers same-origin WebSockets in current browsers; the explicit ws(s) origin of this
  // host is for those that don't (Safari before 15.4).
  const safeHost = /^[a-z0-9.\-:[\]]+$/i.test(host) ? host : ''
  const sockets = safeHost ? ` ${secure ? 'wss' : 'ws'}://${safeHost}` : ''
  return [
    // 'self', not 'none': Firefox checks <link rel="modulepreload"> against default-src.
    'default-src \'self\'',
    // wasm-unsafe-eval: Nuxt Content queries a WebAssembly SQLite copy of the docs in the browser.
    `script-src 'self' 'nonce-${nonce}' 'wasm-unsafe-eval'`,
    // Vue and Nuxt UI set style attributes.
    'style-src \'self\' \'unsafe-inline\'',
    'img-src \'self\' data:',
    'font-src \'self\'',
    `connect-src 'self'${sockets}`,
    'manifest-src \'self\'',
    'base-uri \'none\'',
    'form-action \'none\'',
    'frame-ancestors \'none\'',
    'object-src \'none\''
  ].join('; ')
}

const SCRIPT_TAG = /<script(?=[\s>])/g

export default defineNitroPlugin((nitroApp) => {
  if (import.meta.dev) {
    return
  }
  nitroApp.hooks.hook('render:html', (html, { event }) => {
    const nonce = randomBytes(16).toString('base64')
    event.context.cspNonce = nonce
    const add = (chunk: string) => chunk.replace(SCRIPT_TAG, `<script nonce="${nonce}"`)
    html.head = html.head.map(add)
    html.bodyPrepend = html.bodyPrepend.map(add)
    html.body = html.body.map(add)
    html.bodyAppend = html.bodyAppend.map(add)
  })
  nitroApp.hooks.hook('render:response', (response, { event }) => {
    const nonce = event.context.cspNonce as string | undefined
    response.headers ??= {}
    delete response.headers['x-powered-by']
    if (nonce) {
      response.headers['content-security-policy'] = htmlPolicy(nonce, getRequestHost(event), getRequestProtocol(event) === 'https')
    }
  })
})
