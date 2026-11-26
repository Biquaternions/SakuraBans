<script setup lang="ts">
import { WARNING_ACTION_PLACEHOLDERS } from '#shared/editor/settings'
import type { JsonValue } from '#shared/protocol/schema'

const props = defineProps<{
  modelValue: Record<string, JsonValue>
}>()
const emit = defineEmits<{
  'update:modelValue': [value: Record<string, JsonValue>]
}>()

const { rows, commit, add, remove, duplicates } = useMapRows<string[]>(
  () => props.modelValue,
  value => emit('update:modelValue', value),
  value => (Array.isArray(value) ? value.map(item => String(item)) : []),
  value => [...value]
)

function validCount(key: string): boolean {
  return /^\d+$/.test(key) && Number(key) >= 1
}

function nextCount(): string {
  const used = rows.value.map(row => Number(row.key)).filter(Number.isFinite)
  return String(used.length > 0 ? Math.max(...used) + 1 : 3)
}
</script>

<template>
  <div class="space-y-3">
    <p class="text-xs text-muted">
      Console commands run when a player reaches exactly this many active warnings, on the server that issued the warning.
    </p>
    <EditorPlaceholderList :names="WARNING_ACTION_PLACEHOLDERS" />
    <div
      v-for="row in [...rows].sort((a, b) => Number(a.key) - Number(b.key))"
      :key="row.id"
      class="rounded-md border border-default p-3"
    >
      <div class="mb-2 flex items-center gap-2">
        <span class="text-sm text-muted">At</span>
        <UInput
          :model-value="row.key"
          type="number"
          min="1"
          aria-label="Number of warnings"
          class="w-20"
          size="sm"
          :color="validCount(row.key) && !duplicates.has(row.key) ? 'neutral' : 'error'"
          @update:model-value="row.key = String($event); commit()"
        />
        <span class="text-sm text-muted">warnings</span>
        <UButton
          icon="i-lucide-trash-2"
          color="neutral"
          variant="ghost"
          size="sm"
          class="ms-auto"
          :aria-label="`Remove the commands at ${row.key} warnings`"
          @click="remove(row)"
        />
      </div>
      <p
        v-if="!validCount(row.key) || duplicates.has(row.key)"
        class="mb-2 text-xs text-error"
      >
        {{ duplicates.has(row.key) ? 'This number is listed twice.' : 'Must be a number of warnings (1 or more).' }}
      </p>
      <EditorStringListEditor
        :model-value="row.value"
        placeholder="tempmute {victim} 1h Three active warnings"
        @update:model-value="row.value = $event; commit()"
      />
    </div>
    <UButton
      icon="i-lucide-plus"
      label="Add a warning count"
      color="neutral"
      variant="outline"
      size="xs"
      @click="add(nextCount(), [])"
    />
  </div>
</template>
