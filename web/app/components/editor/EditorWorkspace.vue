<script setup lang="ts">
import type { TabsItem } from '@nuxt/ui'
import { NOTE_TEXT } from '#shared/editor/rebase'
import { editorTabs, RECENT_TAB } from '#shared/editor/recent'
import type { Section } from '#shared/protocol/schema'
import type { EditorSession } from '~/composables/useEditor'
import { createEditorStore, EDITOR_STORE } from '~/composables/useEditor'

const props = defineProps<{
  session: EditorSession
}>()

const store = createEditorStore(props.session)
provide(EDITOR_STORE, store)

const meta = computed(() => store.payload.value.meta)
const demo = props.session.id === null

const LABELS: Record<Section, { label: string, icon: string }> = {
  settings: { label: 'Settings', icon: 'i-lucide-sliders-horizontal' },
  messages: { label: 'Messages', icon: 'i-lucide-message-square-text' },
  templates: { label: 'Templates', icon: 'i-lucide-list-ordered' }
}
const ORDER: Section[] = ['settings', 'messages', 'templates']
const sections = computed(() => ORDER.filter(section => meta.value.sections.includes(section)))

const recentCount = computed(() => (store.payload.value.recentChanges ?? []).length)
const tabs = computed(() => editorTabs(sections.value, recentCount.value))

const tab = ref<string>(tabs.value[0] ?? 'settings')
watch(tabs, (now) => {
  if (!now.includes(tab.value as Section) && now[0]) {
    tab.value = now[0]
  }
})

function countFor(section: Section): number {
  return store.changes.value.filter(change => change.section === section).length
}

const items = computed<TabsItem[]>(() => [
  ...sections.value.map(section => ({
    label: LABELS[section].label,
    icon: LABELS[section].icon,
    value: section,
    badge: countFor(section) > 0 ? { label: String(countFor(section)), color: 'primary' as const, variant: 'subtle' as const } : undefined
  })),
  ...(recentCount.value > 0
    ? [{
        label: 'Recent changes',
        icon: 'i-lucide-history',
        value: RECENT_TAB,
        badge: { label: String(recentCount.value), color: 'neutral' as const, variant: 'subtle' as const }
      }]
    : [])
])

const now = ref(Date.now())
let ticker: ReturnType<typeof setInterval> | undefined
onMounted(() => {
  ticker = setInterval(() => (now.value = Date.now()), 30_000)
  // The live socket stays open while the tab is, in the background too (protocol section 9).
  store.connect()
})
function stopSocket() {
  store.disconnect()
}
onBeforeUnmount(() => {
  clearInterval(ticker)
  stopSocket()
})

const connection = computed(() => {
  if (store.socketState.value !== 'up') {
    return { label: 'Reconnecting…', icon: 'i-lucide-loader-circle', color: 'neutral' as const, text: 'Reconnecting to the editor site. Saving still works; you may get a command to run in game.' }
  }
  return store.live.value
    ? { label: 'Connected', icon: 'i-lucide-radio', color: 'success' as const, text: demo ? 'Demo: saving simulates a live apply.' : 'Connected to the server: changes apply when you save.' }
    : { label: 'Not connected', icon: 'i-lucide-terminal', color: 'neutral' as const, text: 'Not connected: you\'ll get a command to run in game.' }
})

const STALE_TEXT = 'Changes in this section were applied, but the editor couldn\'t load the server\'s fresh snapshot. What you see here may be out of date, and saving this section again will probably conflict. Run /sb editor for a new link.'

const report = computed(() => store.refreshReport.value)
const reportOpen = ref(true)
watch(report, () => {
  reportOpen.value = true
})
const reportSummary = computed(() => {
  const notes = report.value?.notes ?? []
  const count = (kind: string) => notes.filter(note => note.kind === kind).length
  const parts = [
    count('applied') ? `${count('applied')} applied` : '',
    count('kept') ? `${count('kept')} still pending` : '',
    count('conflict') ? `${count('conflict')} changed on the server meanwhile` : '',
    count('template-gone') + count('template-exists') ? `${count('template-gone') + count('template-exists')} template${count('template-gone') + count('template-exists') === 1 ? '' : 's'} to resolve` : ''
  ].filter(Boolean)
  return parts.length > 0 ? `${parts.join(', ')}.` : 'Nothing of yours was pending.'
})
const flaggedNotes = computed(() => (report.value?.notes ?? []).filter(note => note.kind !== 'applied' && note.kind !== 'kept'))

const expiresAt = computed(() => props.session.expiresAt)
const timeLeft = computed(() => {
  if (expiresAt.value === null) {
    return null
  }
  const minutes = Math.max(0, Math.round((expiresAt.value - now.value) / 60_000))
  if (minutes === 0) {
    return 'expired'
  }
  return minutes >= 60 ? `${Math.floor(minutes / 60)} h ${minutes % 60} min left` : `${minutes} min left`
})

const createdText = computed(() => new Date(meta.value.createdAt).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }))

const reviewOpen = ref(false)

// Warn before leaving with changes that were never saved.
function beforeUnload(event: BeforeUnloadEvent) {
  if (store.unsaved.value) {
    event.preventDefault()
  }
}
onMounted(() => {
  window.addEventListener('beforeunload', beforeUnload)
  window.addEventListener('pagehide', stopSocket)
})
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', beforeUnload)
  window.removeEventListener('pagehide', stopSocket)
})
onBeforeRouteLeave(() => {
  if (store.unsaved.value && !window.confirm('You have changes that were not saved. Leave the editor anyway?')) {
    return false
  }
})
</script>

<template>
  <div class="flex min-h-[calc(100vh-var(--ui-header-height))] flex-col">
    <div class="sticky top-(--ui-header-height) z-10 border-b border-default bg-default/90 backdrop-blur">
      <UContainer class="flex flex-wrap items-center gap-x-6 gap-y-2 py-3">
        <div class="min-w-0 flex-1">
          <div class="flex flex-wrap items-center gap-2">
            <h1 class="truncate text-lg font-semibold text-highlighted">
              {{ meta.server || 'Unnamed server' }}
            </h1>
            <UBadge
              :label="meta.platform === 'paper' ? 'Paper' : 'Velocity'"
              color="neutral"
              variant="subtle"
              size="sm"
            />
            <UBadge
              v-if="demo"
              label="Demo"
              color="warning"
              variant="subtle"
              size="sm"
            />
          </div>
          <p class="mt-0.5 text-sm text-muted">
            Opened by {{ meta.uploader.name }} · snapshot from {{ createdText }} · SakuraBans {{ meta.pluginVersion }}
            <template v-if="timeLeft">
              · <span :class="timeLeft === 'expired' ? 'text-error' : ''">{{ timeLeft }}</span>
            </template>
          </p>
        </div>

        <div class="flex items-center gap-2">
          <UTooltip :text="connection.text">
            <UBadge
              :icon="connection.icon"
              :label="connection.label"
              :color="connection.color"
              variant="subtle"
              tabindex="0"
              :aria-label="connection.text"
              class="cursor-help"
            />
          </UTooltip>
          <span
            class="text-sm"
            :class="store.changes.value.length > 0 ? 'text-highlighted' : 'text-muted'"
            aria-live="polite"
          >
            {{ store.changes.value.length === 0 ? 'No changes' : `${store.changes.value.length} change${store.changes.value.length === 1 ? '' : 's'}` }}
            <span
              v-if="store.issues.value.length > 0"
              class="text-error"
            >· {{ store.issues.value.length }} error{{ store.issues.value.length === 1 ? '' : 's' }}</span>
          </span>
          <UButton
            icon="i-lucide-list-checks"
            :label="demo ? 'Review' : 'Review and save'"
            :disabled="store.changes.value.length === 0"
            @click="reviewOpen = true"
          />
        </div>
      </UContainer>
    </div>

    <UContainer class="w-full flex-1 py-4">
      <UAlert
        v-if="demo"
        class="mb-4"
        color="warning"
        variant="subtle"
        icon="i-lucide-flask-conical"
        title="Demo mode"
        description="This is a sample network loaded in your browser. Saving shows the change set the plugin would receive; nothing is uploaded."
      />
      <UAlert
        v-if="store.restoredDraft.value"
        class="mb-4"
        color="info"
        variant="subtle"
        icon="i-lucide-history"
        title="Your earlier edits in this tab were restored."
        :actions="[{ label: 'Discard them', color: 'neutral', variant: 'outline', onClick: () => { store.discardAll(); store.restoredDraft.value = false } }]"
        :close="true"
        @update:open="store.restoredDraft.value = false"
      />

      <UTabs
        v-if="tabs.length > 1"
        v-model="tab"
        :items="items"
        :content="false"
        variant="link"
        class="mb-4"
      />

      <p
        v-if="sections.length === 0"
        class="text-muted"
      >
        This snapshot has no section you may edit.
      </p>

      <UAlert
        v-if="report && report.notable && reportOpen"
        class="mb-4"
        color="info"
        variant="subtle"
        icon="i-lucide-refresh-cw"
        title="Updated from the server"
        :description="`The editor now shows the server's current snapshot. Your edits: ${reportSummary}`"
        :close="true"
        @update:open="reportOpen = false"
      >
        <template
          v-if="flaggedNotes.length > 0"
          #actions
        >
          <ul class="space-y-0.5 text-xs">
            <li
              v-for="note in flaggedNotes"
              :key="note.kind + note.target"
            >
              <code>{{ note.target }}</code>: {{ NOTE_TEXT[note.kind] }}
            </li>
          </ul>
        </template>
      </UAlert>

      <UAlert
        v-if="store.refreshFailed.value && store.draft.stale.length === 0"
        class="mb-4"
        color="warning"
        variant="subtle"
        icon="i-lucide-cloud-off"
        title="The snapshot couldn't be refreshed"
        description="Your changes were applied, but the editor couldn't load the server's fresh snapshot. Run /sb editor for a new link to keep editing on current data."
      />

      <UAlert
        v-if="store.orphanSections.value.length > 0"
        class="mb-4"
        color="warning"
        variant="subtle"
        icon="i-lucide-shield-off"
        title="Some of your edits can't be saved any more"
        :description="`The latest snapshot no longer includes ${store.orphanSections.value.join(', ')} (a permission may have changed). Your edits there are kept in this tab but won't be sent.`"
      />

      <UAlert
        v-if="tab !== RECENT_TAB && store.isStale(tab as Section)"
        class="mb-4"
        color="warning"
        variant="subtle"
        icon="i-lucide-triangle-alert"
        title="This section is out of date"
        :description="STALE_TEXT"
      />

      <KeepAlive>
        <EditorSettingsTab v-if="tab === 'settings' && sections.includes('settings')" />
        <EditorMessagesTab v-else-if="tab === 'messages' && sections.includes('messages')" />
        <EditorTemplatesTab v-else-if="tab === 'templates' && sections.includes('templates')" />
        <EditorRecentChanges v-else-if="tab === 'recent' && recentCount > 0" />
      </KeepAlive>
    </UContainer>

    <EditorReviewModal v-model:open="reviewOpen" />
  </div>
</template>
