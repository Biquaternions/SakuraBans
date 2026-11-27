<script setup lang="ts">
// Read-only: the last changes to settings, messages and templates, from the plugin's audit log
// (protocol 5.6). Absent from the payload when there is nothing to show.
import { recentText } from '#shared/editor/recent'

const store = useEditor()
const changes = computed(() => store.payload.value.recentChanges ?? [])
const when = (at: number) => new Date(at).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
</script>

<template>
  <details
    v-if="changes.length > 0"
    class="mt-8 rounded-lg border border-default p-4"
  >
    <summary class="cursor-pointer text-sm font-medium text-highlighted">
      Recent changes ({{ changes.length }})
    </summary>
    <ul class="mt-3 space-y-1 text-sm">
      <li
        v-for="change in changes"
        :key="`${change.at}-${change.kind}-${change.target}-${change.actor}`"
        class="flex flex-wrap gap-x-2"
      >
        <span class="text-muted">{{ when(change.at) }}</span>
        <span>{{ recentText(change) }}</span>
      </li>
    </ul>
  </details>
</template>
