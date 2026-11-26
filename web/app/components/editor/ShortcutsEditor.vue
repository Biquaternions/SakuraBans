<script setup lang="ts">
import { IDENTIFIER_HINT, isIdentifier, MAX_REASON } from '#shared/editor/limits'
import type { JsonValue } from '#shared/protocol/schema'

const props = defineProps<{
  modelValue: Record<string, JsonValue>
}>()
const emit = defineEmits<{
  'update:modelValue': [value: Record<string, JsonValue>]
}>()

const { rows, commit, add, remove, duplicates } = useMapRows<string>(
  () => props.modelValue,
  value => emit('update:modelValue', value),
  value => (typeof value === 'string' ? value : String(value)),
  value => value
)
</script>

<template>
  <div class="space-y-2">
    <p class="text-xs text-muted">
      With <code>griefing: Griefing</code>, <code>/ban Steve --template griefing</code> uses "Griefing" as the reason. A template with the same name wins.
    </p>
    <div
      v-for="row in rows"
      :key="row.id"
      class="grid gap-2 sm:grid-cols-[10rem_minmax(0,1fr)_auto]"
    >
      <div>
        <UInput
          :model-value="row.key"
          placeholder="name"
          aria-label="Shortcut name"
          class="w-full font-mono"
          size="sm"
          :color="isIdentifier(row.key) && !duplicates.has(row.key) ? 'neutral' : 'error'"
          @update:model-value="row.key = String($event).toLowerCase(); commit()"
        />
        <p
          v-if="!isIdentifier(row.key) || duplicates.has(row.key)"
          class="mt-0.5 text-xs text-error"
        >
          {{ duplicates.has(row.key) ? 'Listed twice.' : IDENTIFIER_HINT }}
        </p>
      </div>
      <div>
        <UInput
          :model-value="row.value"
          placeholder="Reason text"
          :aria-label="`Reason for ${row.key || 'the shortcut'}`"
          class="w-full"
          size="sm"
          :maxlength="MAX_REASON"
          :color="row.value.trim() ? 'neutral' : 'error'"
          @update:model-value="row.value = String($event); commit()"
        />
      </div>
      <UButton
        icon="i-lucide-x"
        color="neutral"
        variant="ghost"
        size="sm"
        :aria-label="`Remove shortcut ${row.key}`"
        @click="remove(row)"
      />
    </div>
    <UButton
      icon="i-lucide-plus"
      label="Add shortcut"
      color="neutral"
      variant="outline"
      size="xs"
      @click="add('', '')"
    />
  </div>
</template>
