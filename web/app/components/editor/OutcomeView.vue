<script setup lang="ts">
import { scopeLabel } from '#shared/editor/draft'
import { groupResult } from '#shared/editor/outcome'
import type { OutcomeEffect } from '#shared/editor/outcome'
import type { ApplyResult, ResultChange } from '#shared/protocol/schema'

const props = defineProps<{
  result: ApplyResult
  effect: OutcomeEffect
}>()

const groups = computed(() => groupResult(props.result))
const SECTION = { settings: 'Settings', messages: 'Messages', templates: 'Templates' } as const
const COLOR: Record<ResultChange['action'], 'primary' | 'info' | 'success' | 'error' | 'neutral' | 'warning'> = {
  set: 'primary', reset: 'info', published: 'success', created: 'success', updated: 'primary', deleted: 'error', unchanged: 'neutral', conflict: 'warning'
}
const applied = computed(() => props.result.outcome === 'applied')
const how = computed(() => (props.result.by === 'live'
  ? 'applied by the plugin while this tab was open'
  : 'applied by /sb applyedits'))
const followUp = computed(() => (props.result.payloadRevision === null
  ? `The change set was ${how.value}, but the editor couldn't get the server's fresh snapshot: what you see may be out of date, and saving the same sections again will probably conflict. Run /sb editor for a new link to keep editing on current data.`
  : `The change set was ${how.value}. The editor has loaded the server's fresh snapshot: close this to keep editing.`))
</script>

<template>
  <div class="space-y-4">
    <UAlert
      v-if="applied"
      :color="result.summary.conflicts > 0 ? 'warning' : 'success'"
      variant="subtle"
      :icon="result.summary.conflicts > 0 ? 'i-lucide-triangle-alert' : 'i-lucide-circle-check'"
      :title="`${result.summary.applied} applied, ${result.summary.unchanged} unchanged, ${result.summary.conflicts} conflict${result.summary.conflicts === 1 ? '' : 's'}`"
      :description="followUp"
    />
    <UAlert
      v-else
      color="error"
      variant="subtle"
      icon="i-lucide-circle-x"
      title="Refused: nothing was changed"
      description="The plugin checked the whole change set and found problems. Your edits are still pending; the errors are shown next to the fields where they belong. Fix them and save again."
    />

    <UAlert
      v-if="applied && effect.conflicts.length > 0"
      color="warning"
      variant="outline"
      icon="i-lucide-git-merge"
      title="Conflicts"
      description="These weren't applied: they were changed on the server after your snapshot, or the server refused them. Your edits stay pending; after closing this you see them next to the server's current value, and can save again or undo them."
    />

    <div
      v-if="!applied && result.errors.length > 0"
    >
      <h3 class="mb-2 text-sm font-semibold text-highlighted">
        Errors
      </h3>
      <ul class="space-y-1 rounded-lg border border-default p-3 text-sm">
        <li
          v-for="error in result.errors"
          :key="error.path + error.message"
        >
          <code class="text-error">{{ error.path }}</code>: {{ error.message }}
        </li>
      </ul>
      <p
        v-if="effect.unmapped.length > 0"
        class="mt-1 text-xs text-muted"
      >
        {{ effect.unmapped.length }} of them don't point to a field this editor shows.
      </p>
    </div>

    <section
      v-for="group in groups"
      :key="group.section"
    >
      <h3 class="mb-2 text-sm font-semibold text-highlighted">
        {{ SECTION[group.section] }}
      </h3>
      <ul class="divide-y divide-default rounded-lg border border-default">
        <li
          v-for="(change, index) in group.changes"
          :key="index"
          class="flex flex-wrap items-start gap-2 p-3"
          :class="change.action === 'conflict' ? 'bg-warning/10' : ''"
        >
          <UBadge
            v-if="change.scope"
            :label="scopeLabel(change.scope)"
            color="neutral"
            variant="outline"
            size="sm"
          />
          <span class="min-w-0 flex-1 font-mono text-sm break-all text-highlighted">{{ change.target }}</span>
          <UBadge
            :label="change.action"
            :color="COLOR[change.action]"
            variant="subtle"
            size="sm"
          />
          <p
            v-if="change.detail"
            class="w-full font-mono text-xs break-all whitespace-pre-wrap text-muted"
          >
            {{ change.detail }}
          </p>
        </li>
      </ul>
    </section>
  </div>
</template>
