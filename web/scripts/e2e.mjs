// End-to-end check of the built server (run `pnpm build` first): starts .output/server with
// short lifetimes in a temporary data directory, exercises every HTTP route and the live-apply
// WebSocket with Node's built-in clients, fetches the pages, and stops the server.
//
//   node scripts/e2e.mjs

import { spawn } from 'node:child_process'
import { request } from 'node:http'
import { mkdtempSync, readFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('..', import.meta.url))
const fixtures = fileURLToPath(new URL('../../protocol/fixtures/', import.meta.url))
const crypto = JSON.parse(readFileSync(join(fixtures, 'crypto-v1.json'), 'utf8'))
const PORT = 3900 + Math.floor(Math.random() * 90)
const BASE = `http://127.0.0.1:${PORT}`
const WS = `ws://127.0.0.1:${PORT}`
const data = mkdtempSync(join(tmpdir(), 'sakurabans-e2e-'))

const server = spawn(process.execPath, [join(root, '.output/server/index.mjs')], {
  env: {
    ...process.env,
    PORT: String(PORT),
    HOST: '127.0.0.1',
    NUXT_EDITOR_STORAGE_DIR: data,
    NUXT_EDITOR_CHANGES_TTL: '4',
    NUXT_EDITOR_SOCKET_SILENCE: '3'
  },
  stdio: ['ignore', 'pipe', 'pipe']
})
let serverLog = ''
server.stdout.on('data', chunk => (serverLog += chunk))
server.stderr.on('data', chunk => (serverLog += chunk))

let failures = 0
function check(name, condition, detail = '') {
  console.log(`${condition ? 'ok  ' : 'FAIL'} ${name}${condition || !detail ? '' : `: ${detail}`}`)
  if (!condition) {
    failures++
  }
}

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
const blob = base64 => Buffer.from(base64, 'base64')

async function waitForServer() {
  for (let i = 0; i < 50; i++) {
    try {
      await fetch(`${BASE}/api/sessions/none`)
      return
    } catch {
      await sleep(200)
    }
  }
  throw new Error(`server did not start:\n${serverLog}`)
}

/** A WebSocket that records frames and its close code. */
function open(url, headers) {
  return new Promise((resolve) => {
    const socket = new WebSocket(url, headers ? { headers } : undefined)
    const client = { socket, frames: [], closed: null, opened: false, failed: false }
    socket.addEventListener('message', event => client.frames.push(JSON.parse(String(event.data))))
    socket.addEventListener('open', () => {
      client.opened = true
      resolve(client)
    })
    socket.addEventListener('close', (event) => {
      client.closed = event.code
      resolve(client)
    })
    socket.addEventListener('error', () => {
      client.failed = true
    })
  })
}

/** A plain HTTP/1.1 request with exactly the headers given (fetch drops Upgrade headers). */
function rawRequest(method, path, body, headers) {
  return new Promise((resolve) => {
    const req = request(`${BASE}${path}`, { method, headers: body ? { ...headers, 'content-length': String(body.length) } : headers })
    req.on('response', (response) => {
      let text = ''
      response.setEncoding('utf8')
      response.on('data', chunk => (text += chunk))
      response.on('end', () => resolve({ status: response.statusCode, body: text }))
    })
    req.on('upgrade', (response, socket) => {
      socket.destroy()
      resolve({ status: response.statusCode, body: '' })
    })
    req.on('error', error => resolve({ status: `error: ${error.message}`, body: '' }))
    if (body) {
      req.write(body)
    }
    req.end()
  })
}

/** The HTTP status a raw upgrade request gets (101 when it is accepted). */
function upgradeStatus(path) {
  return new Promise((resolve) => {
    const req = request(`${BASE}${path}`, {
      headers: { 'connection': 'Upgrade', 'upgrade': 'websocket', 'sec-websocket-version': '13', 'sec-websocket-key': 'dGhlIHNhbXBsZSBub25jZQ==' }
    })
    req.on('upgrade', (response, socket) => {
      socket.destroy()
      resolve(response.statusCode)
    })
    req.on('response', (response) => {
      response.resume()
      resolve(response.statusCode)
    })
    req.on('error', error => resolve(`error: ${error.message}`))
    req.end()
  })
}

async function until(condition, ms = 3000) {
  const end = Date.now() + ms
  while (Date.now() < end) {
    if (condition()) {
      return true
    }
    await sleep(50)
  }
  return condition()
}

function has(client, frame) {
  return client.frames.some(item => JSON.stringify(item) === JSON.stringify(frame))
}

try {
  await waitForServer()

  // ---- HTTP routes ----
  const created = await fetch(`${BASE}/api/sessions`, {
    method: 'POST', body: blob(crypto.payload.blob), headers: { 'content-type': 'application/octet-stream' }
  })
  const session = await created.json()
  check('POST /api/sessions → 201 {id, token}', created.status === 201 && /^[A-Za-z0-9_-]{22}$/.test(session.id) && session.token?.length === 43)

  const fetched = await fetch(`${BASE}/api/sessions/${session.id}`)
  check('GET /api/sessions/{id} sends X-Revision 1', fetched.headers.get('x-revision') === '1')
  const fetchedBytes = Buffer.from(await fetched.arrayBuffer())
  check('GET /api/sessions/{id} returns the sealed payload', fetched.status === 200 && fetchedBytes.equals(blob(crypto.payload.blob)))
  check('API responses are not cached', fetched.headers.get('cache-control') === 'no-store')

  // ---- h2c offer on an ordinary request (RFC 9110 7.8: ignored) ----
  const h2c = await rawRequest('POST', '/api/sessions', blob(crypto.payload.blob), {
    'connection': 'Upgrade, HTTP2-Settings', 'upgrade': 'h2c', 'http2-settings': 'AAMAAABkAARAAAAAAAIAAAAA',
    'content-type': 'application/octet-stream'
  })
  check('POST with an "Upgrade: h2c" offer is answered normally (201)', h2c.status === 201, `status=${h2c.status} body=${h2c.body.slice(0, 80)}`)
  const h2cGet = await rawRequest('GET', '/', null, { 'connection': 'Upgrade, HTTP2-Settings', 'upgrade': 'h2c', 'http2-settings': 'AAMAAABkAARAAAAAAAIAAAAA' })
  check('GET / with an "Upgrade: h2c" offer is answered normally (200)', h2cGet.status === 200, `status=${h2cGet.status}`)

  // ---- Revisions ----
  const putPayload = (headers = {}) => fetch(`${BASE}/api/sessions/${session.id}`, {
    method: 'PUT', body: blob(crypto.payload.blob), headers: { 'content-type': 'application/octet-stream', ...headers }
  })
  check('PUT /api/sessions/{id} without the token → 401', (await putPayload()).status === 401)
  const replaced = await putPayload({ authorization: `Bearer ${session.token}` })
  check('PUT /api/sessions/{id} with the token → 200 {revision: 2}', replaced.status === 200 && (await replaced.json()).revision === 2)
  const refetched = await fetch(`${BASE}/api/sessions/${session.id}`)
  check('…and GET sends X-Revision 2 with the same expiry', refetched.headers.get('x-revision') === '2'
  && refetched.headers.get('x-expires-at') === fetched.headers.get('x-expires-at'))
  await refetched.arrayBuffer()

  // ---- Sockets: presence both ways ----
  const browser = await open(`${WS}/api/sessions/${session.id}/socket?role=browser`)
  check('browser socket opens', browser.opened)
  await until(() => browser.frames.length > 0)
  check('browser hello says no plugin', has(browser, { type: 'hello', plugin: false }))

  const bad = await open(`${WS}/api/sessions/${session.id}/socket?role=plugin`, { authorization: 'Bearer wrong' })
  check('plugin socket with a wrong token is refused', !bad.opened, `opened=${bad.opened} closed=${bad.closed}`)
  const unknown = await open(`${WS}/api/sessions/AAAAAAAAAAAAAAAAAAAAAA/socket?role=browser`)
  check('socket for an unknown session is refused', !unknown.opened)
  check('an upgrade without the plugin token answers HTTP 401', await upgradeStatus(`/api/sessions/${session.id}/socket?role=plugin`) === 401)
  check('an upgrade for an unknown session answers HTTP 404', await upgradeStatus('/api/sessions/AAAAAAAAAAAAAAAAAAAAAA/socket?role=browser') === 404)

  const plugin = await open(`${WS}/api/sessions/${session.id}/socket?role=plugin`, { authorization: `Bearer ${session.token}` })
  check('plugin socket with the token opens', plugin.opened)
  await until(() => plugin.frames.length > 0 && browser.frames.length > 1)
  check('plugin hello counts the browser', has(plugin, { type: 'hello', browsers: 1, pending: [] }))
  check('browser hears that the plugin connected', has(browser, { type: 'plugin', connected: true }))

  plugin.socket.send('{"type":"ping"}')
  check('ping is answered with pong', await until(() => has(plugin, { type: 'pong' })))

  // ---- Change set → plugin notice ----
  const saved = await fetch(`${BASE}/api/sessions/${session.id}/changes`, {
    method: 'POST', body: blob(crypto.changes.blob), headers: { 'content-type': 'application/octet-stream' }
  })
  const { code } = await saved.json()
  check('POST …/changes → 201 {code}', saved.status === 201 && /^[A-HJ-NP-Z2-9]{10}$/.test(code))
  check('the plugin socket receives changes', await until(() => has(plugin, { type: 'changes', code })))
  check('the browser does not receive changes', !browser.frames.some(frame => frame.type === 'changes'))

  const changes = await (await fetch(`${BASE}/api/changes/${code.toLowerCase()}`)).json()
  check('GET /api/changes/{code} returns session and data', changes.session === session.id && changes.data === crypto.changes.blob)

  // ---- Result → browser notice ----
  const noToken = await fetch(`${BASE}/api/changes/${code}/result`, { method: 'POST', body: blob(crypto.result.blob) })
  check('POST …/result without the session token → 401', noToken.status === 401)
  check('GET …/result before one exists → 404', (await fetch(`${BASE}/api/changes/${code}/result`)).status === 404)
  const posted = await fetch(`${BASE}/api/changes/${code}/result`, {
    method: 'POST', body: blob(crypto.result.blob), headers: { 'authorization': `Bearer ${session.token}`, 'content-type': 'application/octet-stream' }
  })
  check('POST …/result with the token → 201', posted.status === 201)
  check('the browser socket receives result', await until(() => has(browser, { type: 'result', code })))
  const twice = await fetch(`${BASE}/api/changes/${code}/result`, {
    method: 'POST', body: blob(crypto.result.blob), headers: { authorization: `Bearer ${session.token}` }
  })
  check('a second result → 409', twice.status === 409)
  const result = await fetch(`${BASE}/api/changes/${code}/result`)
  check('GET …/result returns the sealed result', result.status === 200 && Buffer.from(await result.arrayBuffer()).equals(blob(crypto.result.blob)))

  // ---- One plugin socket per session ----
  const second = await open(`${WS}/api/sessions/${session.id}/socket?role=plugin`, { authorization: `Bearer ${session.token}` })
  check('a second plugin socket opens', second.opened)
  check('…and the first is closed with 4000', await until(() => plugin.closed === 4000), `closed=${plugin.closed}`)
  check('the browser is not told the plugin left during a replacement', !has(browser, { type: 'plugin', connected: false }))

  // ---- Silence (the browser keeps pinging, the second plugin socket stays quiet) ----
  const keepAlive = setInterval(() => browser.socket.send('{"type":"ping"}'), 1000)
  check('a silent socket is closed with 4008', await until(() => second.closed === 4008, 8000), `closed=${second.closed}`)
  check('the browser is told the plugin left', has(browser, { type: 'plugin', connected: false }))
  check('the pinging browser socket stays open', browser.closed === null)
  clearInterval(keepAlive)

  // ---- Expiry (change sets live 4 s here) ----
  await sleep(4200)
  check('an expired change set → 404', (await fetch(`${BASE}/api/changes/${code}`)).status === 404)
  check('its result expired with it → 404', (await fetch(`${BASE}/api/changes/${code}/result`)).status === 404)
  const error = await (await fetch(`${BASE}/api/changes/${code}`)).json()
  check('errors have the JSON shape', typeof error.error === 'string' && typeof error.message === 'string')
  browser.socket.close()

  // ---- Races: simultaneous saves and results ----
  const raceSession = await (await fetch(`${BASE}/api/sessions`, { method: 'POST', body: blob(crypto.payload.blob) })).json()
  const saves = await Promise.all(Array.from({ length: 10 }, () => fetch(`${BASE}/api/sessions/${raceSession.id}/changes`, {
    method: 'POST', body: blob(crypto.changes.blob)
  }).then(response => response.json())))
  const raceCodes = saves.map(save => save.code)
  const watcher = await open(`${WS}/api/sessions/${raceSession.id}/socket?role=plugin`, { authorization: `Bearer ${raceSession.token}` })
  await until(() => watcher.frames.length > 0)
  const pending = watcher.frames[0]?.pending ?? []
  check('10 simultaneous saves: all 10 codes are pending', pending.length === 10 && raceCodes.every(code => pending.includes(code)), `pending=${pending.length}`)
  const statuses = await Promise.all(Array.from({ length: 5 }, () => fetch(`${BASE}/api/changes/${raceCodes[0]}/result`, {
    method: 'POST', body: blob(crypto.result.blob), headers: { authorization: `Bearer ${raceSession.token}` }
  }).then(response => response.status)))
  check('5 simultaneous results for one code: one 201, four 409', statuses.filter(status => status === 201).length === 1
  && statuses.filter(status => status === 409).length === 4, statuses.join(','))
  watcher.socket.close()

  // ---- Pages ----
  for (const path of ['/', '/docs', '/docs/web-editor', '/editor', '/editor/demo']) {
    const page = await fetch(`${BASE}${path}`)
    const csp = page.headers.get('content-security-policy') ?? ''
    check(`GET ${path} → 200 with a nonce CSP`, page.status === 200 && csp.includes('nonce-') && page.headers.get('referrer-policy') === 'no-referrer', `status=${page.status}`)
  }
  check('unknown page → 404', (await fetch(`${BASE}/no-such-page`)).status === 404)
  check('no server errors logged', !/\[(?:nitro|nuxt)\].*error|Error:/i.test(serverLog), serverLog.slice(0, 500))
} finally {
  server.kill()
  rmSync(data, { recursive: true, force: true })
}

console.log(failures === 0 ? '\nAll end-to-end checks passed.' : `\n${failures} check(s) failed.`)
process.exit(failures === 0 ? 0 : 1)
