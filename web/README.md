# SakuraBans web

One Nuxt 4 application with the landing page (`/`), the documentation (`/docs/**`, Nuxt
Content), the configuration editor (`/editor`, `/editor/<id>`) and the editor's backend: a
short-lived store of encrypted blobs plus the live-apply WebSocket. The contract with the
plugin is [`../protocol/EDITOR_PROTOCOL.md`](../protocol/EDITOR_PROTOCOL.md); both sides test
against [`../protocol/fixtures/`](../protocol/fixtures).

## Layout

```
app/                 pages, components (editor UI in components/editor), composables
content/docs/        the documentation (Markdown)
server/api/          the HTTP routes and the socket route of protocol section 3
server/lib/          blob store logic (blobApi), rate limiter, live-apply hub (sessionHub),
                     per-key mutex, the "only WebSocket upgrades" fix (upgrades)
server/plugins/      storage mount and purge timer, security headers (CSP with nonces)
shared/protocol/     Zod schemas, sealing (AES-256-GCM + gzip), base64, format handling
shared/editor/       draft model, change-set building, validation, templates, outcomes, save flow,
                     re-basing on a fresh snapshot (rebase), scope resolution (layers)
shared/duration.ts   the plugin's duration grammar
shared/minimessage.ts  the MiniMessage parser used for previews
shared/discord.ts    the Discord markdown parser used for webhook embed previews
tests/               Vitest: contract tests against ../protocol/fixtures and unit tests
scripts/e2e.mjs      end-to-end check of the built server (HTTP and WebSocket)
```

## Develop

Node 24+ and pnpm 12.

```bash
pnpm install
pnpm dev            # http://127.0.0.1:3000 (security headers are production-only)
```

`pnpm dev` binds to `127.0.0.1` so a Java plugin on the same machine can reach it: use
`web.url: http://127.0.0.1:3000` in the test server's `config.yml`, then `/sb reload` and
`/sb editor`. Test the built server (`pnpm build && node .output/server/index.mjs`) before
deploying, since only it sends the security headers. The docs page "Hosting the editor
yourself" has the full steps.

## Check

```bash
pnpm lint
pnpm typecheck
pnpm test           # Vitest
pnpm build
node scripts/e2e.mjs   # starts .output/server with short lifetimes and checks every route
```

## Run

```bash
pnpm build
node .output/server/index.mjs
```

Or with Docker, from the repository root (the image needs `protocol/`):

```bash
docker build -f web/Dockerfile -t sakurabans-web .
docker run -d -p 3000:3000 -v sakurabans-editor:/data sakurabans-web
# or: docker compose -f web/docker-compose.yml up -d
```

## Configure

Server settings are environment variables, all listed with their defaults in
[`.env.example`](.env.example) and explained in the docs page "Hosting the editor yourself"
(`content/docs/6.web-editor/2.self-hosting.md`): storage driver (filesystem or Redis),
lifetimes, size and rate limits, trusting `X-Forwarded-For`, the upload token, socket limits,
and the public instance named in the docs.

Site links are in [`app/app.config.ts`](app/app.config.ts) under `links` (`repository`,
`download`, `issues`, `community`); each is hidden while empty.

Live apply keeps socket state in one process's memory: run a single process, or route a
session's requests to one process.
