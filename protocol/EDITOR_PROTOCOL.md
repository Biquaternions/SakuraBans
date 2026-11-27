# SakuraBans editor protocol, format 1

The contract between the plugin (`core.admin`) and the web app (`web/`). Both sides are tested
against the files in `protocol/fixtures/`; change this document, the fixtures and both sides
together. A change that an older side can't read gets a new `format` number.

## 1. Roles

- **Plugin:** builds a *payload*, uploads it, later downloads a *change set* and applies it.
  The only party that reads or writes the SakuraBans database.
- **Web server** (Nitro routes of the Nuxt app): stores opaque blobs for a limited time. It
  can't read them (no key) and holds no accounts.
- **Browser:** downloads the payload, decrypts it with the key from the URL fragment, edits
  locally, uploads a change set sealed with the same key.

## 2. Flow

1. `/sb editor` → plugin creates a random 32-byte key, seals the payload (section 4),
   `POST /api/sessions`, stores `(session id, key, who, when)` in `sb_editor_sessions`, replies
   with `<web.url>/editor/<id>#<key>` (key in base64url, no padding).
2. Browser: `GET /api/sessions/<id>`, opens the blob with the key from `location.hash`.
3. Save: browser seals the change set, `POST /api/sessions/<id>/changes`, shows
   `/sb applyedits <code>`.
4. **Live apply** (section 9): while the editor tab stays open, the instance that created the
   session watches it and applies a saved change set by itself; the browser shows the result.
5. **Otherwise** `/sb applyedits <code>` → plugin `GET /api/changes/<code>`, looks the session
   up in `sb_editor_sessions` (any instance on the database can apply), opens the blob,
   validates everything, applies (section 7).

A session may be saved and applied several times until it expires. Each change set is applied
at most once (section 6, `sequence`). After every apply the plugin replaces the session's
payload with a fresh snapshot, so the editor keeps working on current data (section 10).

## 3. HTTP API

Base: `web.url` without a trailing slash. Blobs are `application/octet-stream`.

| Method and path | Request | Success | Errors |
|---|---|---|---|
| `POST /api/sessions` | sealed payload | `201` `{"id": "<id>", "token": "<session token>"}` | `401` bad upload token, `413` too large, `429` rate limited |
| `GET /api/sessions/{id}` | — | `200` sealed payload | `404` unknown or expired |
| `PUT /api/sessions/{id}` | sealed payload (plugin; `Authorization: Bearer <session token>`) | `200` `{"revision": <n>}` | `401`, `404`, `413` |
| `POST /api/sessions/{id}/changes` | sealed change set | `201` `{"code": "<code>"}` | `404` session unknown or expired, `413`, `429` |
| `GET /api/changes/{code}` | — | `200` `{"session": "<id>", "data": "<base64 of the sealed change set>"}` | `404` unknown or expired |
| `POST /api/changes/{code}/result` | sealed result (plugin; `Authorization: Bearer <session token>`) | `201` | `401`, `404`, `409` a result exists, `413` |
| `GET /api/sessions/{id}/socket?role=browser` | WebSocket upgrade (browser) | `101` | `404` |
| `GET /api/sessions/{id}/socket?role=plugin` | WebSocket upgrade (plugin; `Authorization: Bearer <session token>`) | `101` | `401`, `404` |
| `GET /api/changes/{code}/result` | — (browser) | `200` sealed result | `404` none yet, or unknown code |

- `id`: 22 characters, base64url of 16 random bytes, made by the server.
- `code`: 10 characters from `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`, made by the server; the
  plugin accepts it in any case.
- Lifetimes (server settings, these are the defaults): payload 24 hours, change set 1 hour.
  The plugin forgets its session after 24 hours too.
- Size limit: 1 MiB per blob (after sealing).
- Rate limit per client address: 30 uploads per 10 minutes by default (sessions and change
  sets). The result routes and socket connections have their own, looser limit (default 120
  requests per minute) and never count as uploads.
- **Session token:** 32 random bytes in base64url, made by the server with the session and
  known only to the plugin (kept in `sb_editor_sessions`, so any instance can post a result).
  It proves "I am the plugin that uploaded this session"; it is not the encryption key and
  the browser never has it.
- A result lives as long as its change set.
- **Revisions:** the payload uploaded with `POST /api/sessions` is revision 1; each `PUT`
  replaces it and adds one. `GET /api/sessions/{id}` sends the current one as `X-Revision`.
  A `PUT` doesn't change when the session expires, and counts against the looser rate limit.
- **Upload token (optional):** if the server is configured with one, `POST /api/sessions`
  needs `Authorization: Bearer <token>`; the plugin sends `web.token` when it is set. The other
  routes never need it (the browser has no token).
- Errors are JSON `{"error": "<code>", "message": "<text>"}`.
- Further errors any route may give: `400` `empty` (no body), `bad_blob` (the first byte isn't
  the sealing version), `bad_role` (socket `role`); `503` `busy`. A refused socket upgrade is
  a plain HTTP response with these statuses, before any socket opens.
- `POST /api/sessions`, `GET /api/sessions/{id}`, `POST …/changes` and `GET /api/changes/{code}`
  also send `X-Expires-At` (epoch ms), which the editor uses to show the time left.
  `POST …/result` answers `201` `{"code": "<code>"}`.
- Socket limits: at most 5 browser sockets per session (a further one is closed with 1008),
  frames up to 1024 characters (1009), at most 5 frames per second (1008).
- The plugin sends `User-Agent: SakuraBans/<version>` and nothing that identifies the server.

## 4. Sealing

```
blob = 0x01 | nonce (12 random bytes) | AES-256-GCM(key, nonce, aad, gzip(utf8(json)))
```

- The GCM output is ciphertext followed by the 16-byte tag (what `javax.crypto` and WebCrypto
  produce).
- `aad` is the ASCII text `sakurabans-editor:1:payload` for payloads,
  `sakurabans-editor:1:changes` for change sets and `sakurabans-editor:1:result` for results,
  so one can't be passed off as another.
- A fresh nonce for every blob. The first byte is the sealing version.
- A blob that doesn't open (wrong key, tampered, wrong role) is refused with one message; no
  detail is given.

`fixtures/crypto-v1.json` holds a key and three sealed blobs whose plaintext is the three
JSON fixtures.

## 5. Payload (`fixtures/payload-v1.json`)

```
format            1
meta              pluginVersion, platform ("paper" | "velocity"), server (may be ""),
                  uploader {name, uuid | null}, createdAt (epoch ms),
                  sections: which of "settings", "messages", "templates" are present
settings          fields[], scopes[], servers[]
messages          fields[], locales[], scopes[]
templates         items[]
lookups           servers[], categories[], locales[]
```

A section the uploader may not edit, or didn't ask for, is absent and not listed in
`meta.sections`. `settings` covers `config.yml`, `limits.yml` and `webhooks.yml`.

### 5.1 Scopes

A scope is written as in the plugin: `global`, `server:<name>` or `category:<name>`.
Settings and messages are **layered**. For a server, the value of a key is the first of:

1. the override in `server:<its name>`;
2. the overrides of its categories, in order (the first category that has one wins);
3. the override in `global`;
4. the value from that server's file;
5. the default.

A server's categories, in order: the categories of the effective `scopes.categories` that
list the server, in the order that map declares them, then the entries of the server's own
`categories` (its `server.categories` file key) that aren't among them, in their order.
`scopes.categories` is global-only, so its effective value is its `global` override or else
the server's own file value. A server with an empty name has no server scope and takes part
in categories only through its own `categories`.

The plugin resolves exactly this way; the editor uses the same rule to show each server's
effective configuration.

### 5.2 Settings

**`fields[]`**: every key that can be overridden, in schema order: `key`, `group`
(`config`, `limits` or `webhooks`: the file it comes from), `type`, `description`, `options`
(for `ENUM`), `min`, `max` (numbers or null), `default`, `level` (`GLOBAL`: only the global
scope may override it; `SCOPED`: any scope may), `secret` (boolean).

Keys of `config.yml` are written as in the file (`reasons.default`); keys of the other files
carry the file as a prefix (`limits:groups`, `webhooks:routes.ban.url`).

| `type` | JSON value |
|---|---|
| `BOOLEAN` | boolean |
| `INTEGER`, `DECIMAL` | number |
| `STRING`, `ENUM` | string |
| `DURATION` | string in the plugin's duration syntax (`250ms`, `30s`, `1h30m`, `7d`, `1mo`, `1y`) |
| `STRING_LIST` | array of strings |
| `MAP` | object; its values are strings, numbers, booleans, arrays or objects, as in the YAML file |

A **secret** key (webhook URLs) never travels in a payload: wherever its value would stand,
there is `true` (a value is set) or `false` (it is empty).

**`scopes[]`**: `scope`, `version`, `overrides` (key → value). `global` is always present and
first; other scopes appear when they have overrides. A scope that isn't listed has version 0.

**`servers[]`**: the servers of the network, from the plugin's registry: `id` (the instance
id), `name` (may be ""), `categories` (its own `server.categories`), `platform`,
`pluginVersion`, `lastSeenAt` (epoch ms), `fileValues` (key → the value its files give, the
default where a file has no such key). The uploading server is the one whose `name` equals
`meta.server` and is always listed first.

### 5.3 Messages

**`fields[]`**: every message key: `key`, `description` (the key itself when there is no
better text), `placeholders` (names without brackets), `default` (the shipped `en_US` text).
**`locales[]`**: `locale` (`en_US` form) and `fileTexts` (key → the text of the uploading
server's language file, or the default). File texts of other servers aren't sent.
**`scopes[]`**: `scope`, `locale`, `version`, `overrides` (key → text): one entry per scope
and locale that has overrides. A pair that isn't listed has version 0.
Texts are MiniMessage. Messages resolve as in 5.1 for each locale the plugin tries, in its
usual order (the player's locale, the same language, the default locale, `en_US`): the first
locale in which any layer (overrides, then the server's language file) has the key wins,
then the default. So a scoped `en_US` override also reaches a locale with no text of its own.

### 5.4 Templates

**`items[]`**: `scope`, `name`, `version`, `definition`. A definition is the template as it is
written in `templates.yml`, as JSON with the same keys. Names are unique across every type.
Items are listed by name, each template's global definition first, then its variants by scope.

**Variants** (S62). `scope` is where the definition is **in force**: `global` for a template's
global definition, or `server:<name>` / `category:<name>` for a **variant**, a whole definition
(no field-by-field merge) of a template that has a global definition, of the **same `type`**,
without `groups` (a variant counts in its global definition's groups; progress on a ladder is
shared by name across all of a template's definitions). It is unrelated to the definition's own
`scope` key, which is where its punishments apply. Each definition has its own `version`. A use
gets: the scope its command names (`-g` = the global definition); else, on a backend, this
server; on a proxy, the server the staff member is on; else (the proxy console, an API call
without a scope) the global definition. From a server: its own variant, else its categories'
in the order of 5.1, else the global definition; from a category: its variant, else global.
A punishment remembers the variant it came from, and every server shows its message from that
variant (from the global definition once the variant is deleted).

- `type` (required): `ban`, `mute`, `warn` or `kick` for a **preset** (one punishment of that
  type), or `ladder`.
- `reason` (required).
- optional on both: `ip`, `shadow` (a mute preset, or a ladder with a mute step), `scope`,
  `permission-required`, `actions` (array), `groups` (object: group → weight; a preset in a
  group counts towards its group mates' ladders), `message` (MiniMessage: what the
  punished player is shown, instead of the network's message for that type) and `display`
  (`chat`, the default, or `dialog`: the mute notice and the warning shown to an online
  player as a dialog, with chat as the fallback where the platform can't show one; only on
  a mute or warn preset that isn't a shadow mute, or a ladder with a mute or warn step).
- presets only: `duration`, a duration (`7d`) or `permanent`; left out, the command decides.
  A kick has none. Every duration in a definition (`duration`, a step's, `expiry`) is whole
  seconds, at least one (`90s`, not `1500ms`).
- ladders only: `steps` (required, 1-64), `expiry` (a duration) and `address-shared`. A step
  is a text or an object:
  - the text is `none` (no punishment, only actions), `kick`, `warn` (a warning with the
    configured expiry), or a type and a duration or `permanent` (`warn 30d`, `mute 1h`,
    `ban 7d`, `ban permanent`); every step names its type;
  - the object is `{step, reason, message, actions}` with that text as `step` (required) and
    the others optional: the step's reason and message win over the template's, its actions
    run after the template's. A `none` step has at least one action and no message.
  Both sides write a step as the plain text when it has nothing else.

Optional keys at their default are left out, exactly as the YAML export leaves them out. The
keys of the format before D37 (`ladder`, `ladder-expiry`, `step-actions`, `step-messages`)
are refused with an error that names the new key.

### 5.5 Lookups

Names for suggestions only: `servers` and `categories` the network knows, `locales` that have
a language file or overrides. The editor accepts values outside them.

## 6. Change set (`fixtures/changes-v1.json`)

```
format     1
sequence   epoch ms of the save, made by the browser; larger than in its previous save
settings   [ { scope, expectedVersion, set: {key: value}, reset: [key],
               publish: {server, keys: [key]} } ]                              optional
messages   [ { scope, locale, expectedVersion, set: {key: text}, reset: [key] } ] optional
templates  [ {op: "create", scope, name, definition}
           | {op: "update", scope, name, expectedVersion, definition}
           | {op: "delete", scope, name, expectedVersion} ]                    optional
```

- Only what the user changed is sent. `set` creates or replaces an override in that scope;
  `reset` removes it, so the next layer applies again. A key is never in both. At most one
  block per scope (settings) and per scope and locale (messages).
- **`publish`** (global block only): make the named server's file values the global
  overrides for these keys. The plugin takes the values from its registry, so secrets can be
  published without the browser ever holding them. A key is never in `publish` and `set`.
- A secret key is `set` to a string (the new value) or to `""` (an empty override).
- An update carries the whole new definition.
- A template change names its definition by `scope` (as in 5.4: `global`, or the variant's
  server or category) and `name`; a pair is changed at most once. Deleting a global definition
  deletes its variants too, whatever their versions (the result lists each). The plugin applies
  template changes in this order, and the editor writes them in it: variant deletions, global
  creations and updates, variant creations and updates, global deletions. A variant whose
  global definition is missing, or of another `type`, and a global definition changing its
  `type` while variants of the old one remain, are refused when applied (a `conflict` with the
  reason).
- `expectedVersion` is the version from the payload (0 for a scope, or a scope and locale,
  that has no overrides yet).
- **`sequence`:** the plugin remembers the last sequence it applied for the session (in
  `sb_editor_sessions`) and refuses a change set whose sequence isn't larger, with or without
  a command. So a change set can't be applied twice, and a server replaying or reordering
  stored blobs achieves nothing.

## 7. Applying

1. The plugin validates the whole change set first: known keys and scopes, that a key's
   `level` allows the scope, value types, bounds, message length, template definitions, and
   that the settings would still load for the applying server. Any error
   refuses everything, with each error's path, which follows the change set's own structure:
   `settings[0].set.reasons.default`, `settings[0].publish.keys[2]`, `messages[1].reset[0]`,
   `templates[1].definition.steps[0]`.
2. It then checks permissions per section: `sakurabans.editor` opens the editor and applies;
   each section needs the same node as its in-game administration
   (`sakurabans.admin.settings`, `sakurabans.admin.messages`, `sakurabans.admin.templates`).
   A section the sender may not change refuses everything.
3. It applies section by section through the same services the commands use (audited with
   source `web`, synced to the other instances). A version that no longer matches is a
   **conflict** for that section or template; what doesn't conflict is still applied.
4. It tells whoever ran the command what changed, one line per change, as LuckPerms does: in
   the console when the console ran it, in chat when a player did. Each line names the thing
   and the change (a setting: its scope when not global, old value → new value, or "reset";
   a published key; a message: scope, locale and key, set or reset; a template: created, updated with the fields that changed, or
   deleted), then conflicts and a closing summary (applied, unchanged, conflicts). Long values
   are shortened. This is plugin-side only; nothing is sent back to the site.

5. **The session's creator is told too**, with the same lines, when someone else applied it
   (and always on a live apply, where nobody ran a command): a player creator while online on
   the applying instance, a console creator in that instance's console.

After a conflict the user opens a new session (`/sb editor`) to see the current state.

## 8. Versioning

`format` is one number for payload and change set. The web app keeps reading every format it
has ever shipped for; when it meets a newer one it says the site needs an update, and for an
unknown older one that the plugin does. The plugin refuses a change set whose `format` isn't
the one it sent.

## 9. Live apply

As in LuckPerms, a change set saved while the editor tab has stayed open is applied without a
command. Browser and plugin each hold a WebSocket to the site for the session. The socket
carries only small notices; every blob still travels over the HTTP routes of section 3, so the
site never relays content and size limits live in one place.

**Frames** are JSON text, one object each, with a `type`:

| From | Frame | Meaning |
|---|---|---|
| either client | `{"type":"ping"}` | every 20 seconds; the site answers `{"type":"pong"}` and closes a socket that has been silent for 60 seconds |
| site → plugin | `{"type":"hello","browsers":<n>,"pending":[<codes>]}` | on connect: how many browsers are connected, and the session's change sets that have no result yet, oldest first |
| site → plugin | `{"type":"browsers","count":<n>}` | a browser connected or left |
| site → plugin | `{"type":"changes","code":"<code>"}` | a change set was just saved (`POST …/changes`) |
| site → browser | `{"type":"hello","plugin":<bool>}` | on connect: whether the plugin is connected |
| site → browser | `{"type":"plugin","connected":<bool>}` | the plugin connected or left |
| site → browser | `{"type":"result","code":"<code>"}` | a result was just posted for that code |

Unknown frame types are ignored by everyone. One plugin socket per session: a new one replaces
the old (the old is closed with code 4000). Close codes: 4000 replaced, 4004 session expired
or unknown, 4008 silent too long.

- **Watching (plugin).** After `/sb editor`, the instance that created the session connects.
  It disconnects, for good, when: no browser has connected within 2 minutes; no browser has
  been connected for 2 minutes; the creator, if a player, is no longer online on that
  instance; the same sender opens a newer session; the session expires; the plugin stops; or
  `web.live-apply` is `false` (then it never connects). One watched session per sender. A
  dropped connection is retried (after 2, 5, 15 and 30 seconds) while none of those holds.
- **Applying.** For each code from `hello.pending` and each `changes` frame, in order, the
  plugin fetches the change set over HTTP and applies it as section 7 describes, with the
  **creator** as the actor: the creator's permissions are checked at that moment, and the
  creator sees the lines (7.4). The `sequence` rule (section 6) makes a repeated notice
  harmless.
- **Result.** After applying or refusing (by a live apply or by `/sb applyedits`, from any
  instance), the plugin posts a sealed result for the code (`fixtures/result-v1.json`); the
  site then sends `result` to the session's browsers.

  ```
  format     1
  sequence   the change set's sequence
  outcome    "applied" | "refused"
  by         "live" | "command"
  changes    [ {section, scope, target, action, detail} ]   one per line of 7.4, in order
             section: "settings" | "messages" | "templates"
             scope:   the scope for settings and messages; for templates the definition's
                      scope of 5.4 (`global` or the variant's)
             action:  "set" | "reset" | "published" | "created" | "updated" | "deleted"
                      | "unchanged" | "conflict"
             target:  the key, "<locale>:<key>", or the template name
             detail:  plain text for display (old → new, changed fields), may be ""; never a secret's value
  errors     [ {path, message} ]                      only when refused
  summary    {applied, unchanged, conflicts}
  payloadRevision   the revision of the fresh payload the plugin uploaded after applying,
                    or null (refused, or the upload failed)
  ```

- **Browser.** It connects when the editor opens and reconnects when the socket drops (after
  2, 5, 15, then every 30 seconds). It is *live* while the plugin is connected, and shows
  that state. After a save: if live, it waits up to 20 seconds for the `result` notice, then
  fetches and shows the result; if not live, or none arrives in time, it shows
  `/sb applyedits <code>` and keeps listening (the command's result is announced the same
  way). While its socket is down it asks `GET …/result` every 5 seconds instead.
- **Trust.** Only someone holding the link can produce a change set the plugin will open; only
  the plugin that uploaded the session can connect as the plugin or post a result (session
  token); the creator's permissions are re-checked on every apply; and the window closes with
  the tab. Admins who want every change confirmed in game set `web.live-apply: false`.
- **Hosting.** Socket state lives in the memory of one site process. A site run as several
  processes must route a session's requests to one of them, or accept that live apply only
  works when browser and plugin reach the same process; the command always works.

## 10. Keeping the editor current

As LuckPerms does with its follow-up session, the editor continues on a fresh snapshot after
an apply; here the link stays the same.

- **Plugin.** After an apply with outcome `applied` (live or by command, from any instance,
  conflicts included), it builds a new payload for the session's **creator** (the sections the
  creator may edit now), seals it with the session's key and `PUT`s it. The revision it gets
  back goes into the result as `payloadRevision`, and the result is posted after the `PUT`. If
  building or uploading fails, the result carries `null` and the apply still stands.
- **Browser.** For every result it learns of (its own save, or a `result` notice for a code it
  didn't save, such as another tab or a colleague on the same link), when `payloadRevision` is
  larger than the revision it has loaded, it fetches the payload again and **re-bases**:
  - a pending edit whose value equals the new snapshot is dropped (it was applied);
  - every other pending edit stays pending against the new snapshot and its new versions,
    including the ones reported as `conflict`, which the user now sees next to the current
    value;
  - a pending update or delete of a template that no longer exists, and a pending create of
    one that now exists, are kept and flagged for the user to resolve (recreate, discard, or
    edit the existing one). Templates are matched by `scope` and `name`, so each variant
    re-bases on its own.
  Nothing is locked after an apply. With `payloadRevision` `null` the editor keeps what it
  has, tells the user the snapshot couldn't be refreshed, and suggests `/sb editor`.
- **Sections.** A refreshed payload may hold fewer sections than before (the creator lost a
  permission); edits pending for a section that disappeared are kept in the draft but can't be
  sent, and the editor says so.
