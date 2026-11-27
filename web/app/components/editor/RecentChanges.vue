<script setup lang="ts">
// Read-only: the last changes to settings, messages and templates, from the plugin's audit log
// (protocol 5.6). Absent from the payload when there is nothing to show; the tab is then hidden.
import { recentText } from '#shared/editor/recent'

const store = useEditor()
const changes = computed(() => store.payload.value.recentChanges ?? [])
const when = (at: number) => new Date(at).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
</script>

<template>
  <section aria-label="Recent changes">
    <p class="mb-3 text-sm text-muted">
      The last changes to settings, messages and templates, newest first.
    </p>
    <ul class="space-y-2 text-sm">
      <li
        v-for="change in changes"
        :key="`${change.at}-${change.kind}-${change.target}-${change.actor}`"
        class="flex flex-wrap gap-x-3 rounded-lg border border-default px-4 py-2"
      >
        <span class="text-muted">{{ when(change.at) }}</span>
        <span>{{ recentText(change) }}</span>
      </li>
    </ul>
  </section>
</template>
