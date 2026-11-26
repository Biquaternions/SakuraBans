<script setup lang="ts">
import { HIDDEN, scopeLabel } from '#shared/editor/draft'
import type { ChangeItem } from '#shared/editor/draft'
import type { OutcomeEffect } from '#shared/editor/outcome'
import { SaveFlow } from '#shared/editor/saveFlow'
import type { SavePhase } from '#shared/editor/saveFlow'
import type { ApplyResult } from '#shared/protocol/schema'
import { SaveError } from '~/composables/useEditor'
import type { SaveResult } from '~/composables/useEditor'

const open = defineModel<boolean>('open', { default: false })

const store = useEditor()
const demo = store.session.id === null

const saving = ref(false)
const error = ref<string | null>(null)
const result = ref<SaveResult | null>(null)
const phase = ref<SavePhase | null>(null)
const outcome = ref<{ result: ApplyResult, effect: OutcomeEffect } | null>(null)
let flow: SaveFlow | null = null
const cleanups: Array<() => void> = []

function stopFlow() {
  flow?.stop()
  flow = null
  cleanups.splice(0).forEach(cleanup => cleanup())
}

watch(open, (value) => {
  if (value) {
    error.value = null
    result.value = null
    phase.value = null
    outcome.value = null
  } else {
    // The dialog is closed: stop listening for the result.
    stopFlow()
  }
})
onBeforeUnmount(stopFlow)

function timer(callback: () => void, ms: number): () => void {
  const id = setTimeout(callback, ms)
  return () => clearTimeout(id)
}

function follow(saved: SaveResult) {
  const changeSet = saved.changeSet
  const onPhase = (next: SavePhase) => {
    phase.value = next
    if (next.kind === 'outcome') {
      cleanups.splice(0).forEach(cleanup => cleanup())
      // Shows the outcome, then loads the fresh snapshot and re-bases (protocol section 10).
      void store.handleResult(next.code.toUpperCase(), changeSet, next.result).then((effect) => {
        outcome.value = { result: next.result, effect }
      })
    }
  }
  // While this dialog follows the code, the store leaves its result notice to us.
  cleanups.push(store.follow(saved.code.toUpperCase()))
  if (saved.kind === 'demo') {
    // Demo: a made-up live apply, so the outcome view can be seen.
    const made = store.demoResult(changeSet)
    flow = new SaveFlow(saved.code, true, true, {
      setTimer: timer, onPhase, fetchResult: async () => made
    })
    cleanups.push(timer(() => flow?.notice(saved.code), 1500))
    return
  }
  flow = new SaveFlow(saved.code, saved.live, store.socketState.value === 'up', {
    setTimer: timer, onPhase, fetchResult: code => store.fetchResult(code)
  })
  const current = flow
  cleanups.push(store.onResultNotice(code => current.notice(code)))
  cleanups.push(store.onSocketChange(up => current.socket(up)))
}

const SECTION_LABEL = { settings: 'Settings', messages: 'Messages', templates: 'Templates' } as const
const KIND_LABEL: Record<ChangeItem['kind'], string> = {
  set: 'set', reset: 'override removed', publish: 'published', create: 'created', update: 'updated', delete: 'deleted', rename: 'renamed'
}
const KIND_COLOR: Record<ChangeItem['kind'], 'primary' | 'neutral' | 'success' | 'error' | 'warning' | 'info'> = {
  set: 'primary', reset: 'info', publish: 'success', create: 'success', update: 'primary', delete: 'error', rename: 'warning'
}

const sendable = computed(() => store.changes.value.length)

const grouped = computed(() => {
  const groups: Array<{ section: ChangeItem['section'], items: ChangeItem[] }> = []
  for (const section of ['settings', 'messages', 'templates'] as const) {
    const items = store.changes.value.filter(change => change.section === section)
    if (items.length > 0) {
      groups.push({ section, items })
    }
  }
  return groups
})

async function save() {
  saving.value = true
  error.value = null
  try {
    result.value = await store.save()
    follow(result.value)
  } catch (caught) {
    error.value = caught instanceof SaveError ? caught.message : 'Saving failed.'
  } finally {
    saving.value = false
  }
}

const command = computed(() => (result.value?.kind === 'saved' ? `/sb applyedits ${result.value.code}` : ''))
const copied = ref(false)

async function copy() {
  try {
    await navigator.clipboard.writeText(command.value)
    copied.value = true
    setTimeout(() => (copied.value = false), 2000)
  } catch {
    copied.value = false
  }
}

const expiresText = computed(() => {
  if (result.value?.kind !== 'saved' || result.value.expiresAt === null) {
    return 'for a limited time (1 hour by default)'
  }
  const at = new Date(result.value.expiresAt)
  const minutes = Math.max(0, Math.round((result.value.expiresAt - Date.now()) / 60_000))
  return `until ${at.toLocaleTimeString(undefined, { timeStyle: 'short' })} (about ${minutes} minutes)`
})

// Secrets are masked here too: the change set shown is for reading, never a secret's value.
const demoJson = computed(() => {
  if (result.value?.kind !== 'demo') {
    return ''
  }
  const secrets = new Set((store.payload.value.settings?.fields ?? []).filter(field => field.secret).map(field => field.key))
  const masked = {
    ...result.value.changeSet,
    settings: result.value.changeSet.settings?.map(block => ({
      ...block,
      set: Object.fromEntries(Object.entries(block.set).map(([key, value]) => [key, secrets.has(key) ? (value === '' ? '' : HIDDEN) : value]))
    }))
  }
  return JSON.stringify(masked, null, 2)
})

const title = computed(() => {
  if (!result.value) {
    return 'Review your changes'
  }
  switch (phase.value?.kind) {
    case 'applying':
      return 'Applying…'
    case 'outcome':
      return outcome.value?.result.outcome === 'refused' ? 'Refused' : 'Applied'
    default:
      return 'Saved'
  }
})
</script>

<template>
  <UModal
    v-model:open="open"
    :title="title"
    :description="result ? undefined : 'Every change below is sent to the plugin when you save. Undo any you don\'t want.'"
    :ui="{ content: 'sm:max-w-3xl' }"
  >
    <template #body>
      <div
        v-if="result && (phase?.kind === 'applying' || (phase?.kind === 'outcome' && !outcome))"
        class="flex items-center gap-3 py-6"
        role="status"
      >
        <UIcon
          name="i-lucide-loader-circle"
          class="size-6 animate-spin text-primary"
        />
        <div>
          <p class="font-medium text-highlighted">
            The server is applying your changes…
          </p>
          <p class="text-sm text-muted">
            The plugin is connected and applies the change set as the person who opened the editor. This takes a few seconds.
          </p>
        </div>
      </div>

      <div
        v-else-if="result && outcome"
        class="space-y-3"
      >
        <EditorOutcomeView
          :result="outcome.result"
          :effect="outcome.effect"
        />
        <UCollapsible v-if="result.kind === 'demo'">
          <UButton
            label="Show the change set that was sent"
            color="neutral"
            variant="ghost"
            size="sm"
            trailing-icon="i-lucide-chevron-down"
          />
          <template #content>
            <UAlert
              class="mt-2"
              color="warning"
              variant="subtle"
              icon="i-lucide-flask-conical"
              title="Demo: nothing was uploaded"
              :description="`In a real session this change set is encrypted (${result.sealedBytes} bytes here) and uploaded; the result above was made up in your browser.`"
            />
            <pre class="mt-2 max-h-96 overflow-auto rounded-md bg-elevated p-3 font-mono text-xs">{{ demoJson }}</pre>
          </template>
        </UCollapsible>
      </div>

      <div
        v-else-if="result?.kind === 'saved'"
        class="space-y-4"
      >
        <p>Run this command in game or in the console of any server on the network:</p>
        <div class="flex items-center gap-2">
          <code class="flex-1 rounded-md bg-elevated px-3 py-2 font-mono text-base break-all text-highlighted select-all">{{ command }}</code>
          <UButton
            :icon="copied ? 'i-lucide-check' : 'i-lucide-copy'"
            :label="copied ? 'Copied' : 'Copy'"
            @click="copy"
          />
        </div>
        <p class="text-sm text-muted">
          The code works {{ expiresText }}. It needs <code>sakurabans.editor</code> and the permission of each section you changed.
        </p>
        <p
          class="flex items-center gap-2 text-sm text-muted"
          role="status"
        >
          <UIcon
            name="i-lucide-loader-circle"
            class="size-4 animate-spin"
          />
          Keep this open to see the result here once the command has run.
        </p>
        <UAlert
          color="info"
          variant="subtle"
          icon="i-lucide-info"
          title="Nothing has changed yet"
          description="The network only changes when someone runs the command (or, while the plugin is connected, when it applies the change set by itself). You can keep editing and save again: each code holds all your pending changes, so run only the newest one."
        />
      </div>

      <div
        v-else
        class="space-y-5"
      >
        <UAlert
          v-if="store.issues.value.length > 0"
          color="error"
          variant="subtle"
          icon="i-lucide-circle-alert"
          :title="`Fix ${store.issues.value.length} error${store.issues.value.length === 1 ? '' : 's'} before saving`"
        >
          <template #description>
            <ul class="mt-1 space-y-0.5 font-mono text-xs">
              <li
                v-for="issue in store.issues.value"
                :key="issue.path + issue.message"
              >
                {{ issue.path }}: {{ issue.message }}
              </li>
            </ul>
          </template>
        </UAlert>

        <p
          v-if="grouped.length === 0"
          class="text-muted"
        >
          No changes.
        </p>

        <section
          v-for="group in grouped"
          :key="group.section"
        >
          <h3 class="mb-2 text-sm font-semibold text-highlighted">
            {{ SECTION_LABEL[group.section] }}
          </h3>
          <ul class="divide-y divide-default rounded-lg border border-default">
            <li
              v-for="change in group.items"
              :key="change.title + change.kind"
              class="flex items-start gap-3 p-3"
            >
              <div class="min-w-0 flex-1">
                <div class="flex flex-wrap items-center gap-2">
                  <UBadge
                    v-if="change.scope"
                    :label="scopeLabel(change.scope)"
                    color="neutral"
                    variant="outline"
                    size="sm"
                  />
                  <span class="font-mono text-sm break-all text-highlighted">{{ change.title }}</span>
                  <UBadge
                    :label="KIND_LABEL[change.kind]"
                    :color="KIND_COLOR[change.kind]"
                    variant="subtle"
                    size="sm"
                  />
                </div>
                <div
                  v-if="change.before !== undefined || change.after !== undefined"
                  class="mt-1 grid gap-1 text-xs sm:grid-cols-[auto_minmax(0,1fr)]"
                >
                  <span class="text-muted">was</span>
                  <code class="break-all whitespace-pre-wrap line-through decoration-error/50">{{ change.before }}</code>
                  <span class="text-muted">now</span>
                  <code class="break-all whitespace-pre-wrap">{{ change.after }}</code>
                </div>
                <p
                  v-if="change.stale"
                  class="mt-1 text-xs text-warning"
                >
                  This section's snapshot is out of date: saving this change will probably conflict. Run /sb editor for a new link.
                </p>
                <p
                  v-if="change.note && change.kind !== 'set'"
                  class="mt-1 text-xs text-muted"
                >
                  {{ change.note }}
                </p>
              </div>
              <UButton
                icon="i-lucide-undo-2"
                label="Undo"
                color="neutral"
                variant="ghost"
                size="xs"
                @click="store.revert(change.ref)"
              />
            </li>
          </ul>
        </section>

        <p
          v-if="error"
          class="text-sm text-error"
          role="alert"
        >
          {{ error }}
        </p>
      </div>
    </template>

    <template #footer>
      <div class="flex w-full flex-wrap justify-end gap-2">
        <UButton
          v-if="result"
          label="Keep editing"
          color="neutral"
          variant="outline"
          @click="open = false"
        />
        <template v-else>
          <UButton
            label="Cancel"
            color="neutral"
            variant="ghost"
            @click="open = false"
          />
          <UButton
            :label="demo ? 'Save (demo)' : store.live.value ? 'Save and apply' : 'Save'"
            icon="i-lucide-lock"
            :loading="saving"
            :disabled="store.issues.value.length > 0 || sendable === 0"
            @click="save"
          />
        </template>
      </div>
    </template>
  </UModal>
</template>
