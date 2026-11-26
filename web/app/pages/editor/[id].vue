<script setup lang="ts">
import { SESSION_ID_PATTERN } from '#shared/protocol/constants'
import { readPayload } from '#shared/protocol/format'
import { openJson, parseKey, SealError } from '#shared/protocol/seal'
import type { EditorSession } from '~/composables/useEditor'

definePageMeta({
  layout: false
})

useSeoMeta({
  title: 'Editor',
  robots: 'noindex, nofollow'
})

type State
  = | { kind: 'loading' }
    | { kind: 'missing-key' }
    | { kind: 'bad-link' }
    | { kind: 'not-found' }
    | { kind: 'decrypt-failed' }
    | { kind: 'newer-format', format: number }
    | { kind: 'older-format', format: number }
    | { kind: 'invalid', issues: string[] }
    | { kind: 'network', message: string }
    | { kind: 'ready', session: EditorSession }

const route = useRoute()
const id = computed(() => String(route.params.id ?? ''))
const state = shallowRef<State>({ kind: 'loading' })

async function loadDemo() {
  const fixture = await import('#protocol/fixtures/payload-v1.json')
  const result = readPayload(JSON.parse(JSON.stringify(fixture.default)))
  state.value = result.ok
    ? { kind: 'ready', session: { id: null, key: null, payload: result.payload, revision: 1, expiresAt: null } }
    : { kind: 'invalid', issues: 'issues' in result ? result.issues : [] }
}

async function load() {
  state.value = { kind: 'loading' }
  if (id.value === 'demo') {
    await loadDemo()
    return
  }
  if (!SESSION_ID_PATTERN.test(id.value)) {
    state.value = { kind: 'bad-link' }
    return
  }
  // The key never leaves this function's memory: it isn't logged, stored or sent.
  const fragment = window.location.hash.replace(/^#/, '')
  if (fragment === '') {
    state.value = { kind: 'missing-key' }
    return
  }
  const key = parseKey(fragment)
  if (key === null) {
    state.value = { kind: 'decrypt-failed' }
    return
  }
  let response: Response
  try {
    response = await fetch(`/api/sessions/${encodeURIComponent(id.value)}`, {
      credentials: 'omit',
      cache: 'no-store',
      referrerPolicy: 'no-referrer'
    })
  } catch {
    state.value = { kind: 'network', message: 'The editor site could not be reached.' }
    return
  }
  if (response.status === 404) {
    state.value = { kind: 'not-found' }
    return
  }
  if (!response.ok) {
    state.value = { kind: 'network', message: `The editor site answered ${response.status}.` }
    return
  }
  const blob = new Uint8Array(await response.arrayBuffer())
  let value: unknown
  try {
    value = await openJson(key, 'payload', blob)
  } catch (error) {
    state.value = error instanceof SealError
      ? { kind: 'decrypt-failed' }
      : { kind: 'network', message: 'The snapshot could not be read.' }
    return
  }
  const result = readPayload(value)
  if (!result.ok) {
    state.value = result.reason === 'invalid'
      ? { kind: 'invalid', issues: result.issues }
      : { kind: result.reason, format: result.format }
    return
  }
  const expires = Number(response.headers.get('x-expires-at'))
  state.value = {
    kind: 'ready',
    session: {
      id: id.value,
      key,
      payload: result.payload,
      revision: Number(response.headers.get('x-revision')) || 1,
      expiresAt: Number.isFinite(expires) && expires > 0 ? expires : null
    }
  }
}

onMounted(load)
watch(id, load)
</script>

<template>
  <div>
    <EditorWorkspace
      v-if="state.kind === 'ready'"
      :key="state.session.id ?? 'demo'"
      :session="state.session"
    />

    <UContainer
      v-else
      class="py-16"
    >
      <div class="mx-auto max-w-xl">
        <div
          v-if="state.kind === 'loading'"
          class="flex items-center gap-3 text-muted"
          role="status"
        >
          <UIcon
            name="i-lucide-loader-circle"
            class="size-5 animate-spin"
          />
          Opening the snapshot…
        </div>

        <UAlert
          v-else-if="state.kind === 'missing-key'"
          color="warning"
          variant="subtle"
          icon="i-lucide-key-round"
          title="The link has no key"
          description="The key is the part after # in the link the plugin gave you. Copy the whole link, including everything after #, and open it again."
        />

        <UAlert
          v-else-if="state.kind === 'bad-link'"
          color="warning"
          variant="subtle"
          icon="i-lucide-link-2-off"
          title="This isn't an editor link"
          description="Editor links look like /editor/<22 characters>#<key>. Run /sb editor to get one."
        />

        <UAlert
          v-else-if="state.kind === 'not-found'"
          color="neutral"
          variant="subtle"
          icon="i-lucide-clock"
          title="This session doesn't exist or has expired"
          description="Editor sessions last a limited time (24 hours by default). Run /sb editor again for a new link."
        />

        <UAlert
          v-else-if="state.kind === 'decrypt-failed'"
          color="error"
          variant="subtle"
          icon="i-lucide-lock"
          title="The snapshot can't be decrypted"
          description="The key in the link doesn't open this session. Make sure you copied the whole link, or run /sb editor again."
        />

        <UAlert
          v-else-if="state.kind === 'newer-format'"
          color="warning"
          variant="subtle"
          icon="i-lucide-refresh-cw"
          title="This site needs an update"
          :description="`The plugin sent format ${state.format}, which this editor site doesn't know yet. Update the site (or use one that is up to date).`"
        />

        <UAlert
          v-else-if="state.kind === 'older-format'"
          color="warning"
          variant="subtle"
          icon="i-lucide-refresh-cw"
          title="The plugin needs an update"
          :description="`The plugin sent format ${state.format}, which this site no longer reads. Update SakuraBans on your servers.`"
        />

        <UAlert
          v-else-if="state.kind === 'invalid'"
          color="error"
          variant="subtle"
          icon="i-lucide-file-warning"
          title="The snapshot is not valid"
          description="It decrypted, but its content doesn't match what this site expects."
        >
          <template #actions>
            <ul class="list-inside list-disc font-mono text-xs">
              <li
                v-for="issue in state.issues"
                :key="issue"
              >
                {{ issue }}
              </li>
            </ul>
          </template>
        </UAlert>

        <UAlert
          v-else-if="state.kind === 'network'"
          color="error"
          variant="subtle"
          icon="i-lucide-wifi-off"
          title="Could not load the snapshot"
          :description="state.message"
          :actions="[{ label: 'Try again', onClick: load }]"
        />

        <div
          v-if="state.kind !== 'loading'"
          class="mt-6"
        >
          <UButton
            to="/editor"
            label="About the editor"
            color="neutral"
            variant="ghost"
            icon="i-lucide-arrow-left"
          />
        </div>
      </div>
    </UContainer>
  </div>
</template>
