<script setup lang="ts">
// A webhook route's embed fields: an ordered name → text list, at most 25 (webhooks.yml).
import { MAX_EMBED_FIELDS } from '#shared/editor/settings'
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

function move(index: number, to: number) {
  if (to < 0 || to >= rows.value.length) {
    return
  }
  const list = [...rows.value]
  const [row] = list.splice(index, 1)
  list.splice(to, 0, row!)
  rows.value = list
  commit()
}
</script>

<template>
  <div class="space-y-2">
    <div
      v-for="(row, index) in rows"
      :key="row.id"
      class="grid gap-2 sm:grid-cols-[9rem_minmax(0,1fr)_auto]"
    >
      <UInput
        :model-value="row.key"
        placeholder="Name"
        aria-label="Field name"
        size="sm"
        :color="row.key.trim() && !duplicates.has(row.key) ? 'neutral' : 'error'"
        @update:model-value="row.key = String($event); commit()"
      />
      <UInput
        :model-value="row.value"
        placeholder="{reason}"
        :aria-label="`Text of ${row.key || 'the field'}`"
        size="sm"
        class="font-mono"
        @update:model-value="row.value = String($event); commit()"
      />
      <div class="flex gap-1">
        <UButton
          icon="i-lucide-arrow-up"
          color="neutral"
          variant="ghost"
          size="xs"
          :disabled="index === 0"
          :aria-label="`Move ${row.key} up`"
          @click="move(index, index - 1)"
        />
        <UButton
          icon="i-lucide-x"
          color="neutral"
          variant="ghost"
          size="xs"
          :aria-label="`Remove ${row.key}`"
          @click="remove(row)"
        />
      </div>
    </div>
    <UButton
      v-if="rows.length < MAX_EMBED_FIELDS"
      icon="i-lucide-plus"
      label="Add field"
      color="neutral"
      variant="outline"
      size="xs"
      @click="add(`Field ${rows.length + 1}`, '')"
    />
    <p class="text-xs text-muted">
      Shown in this order; a field whose text comes out empty is left out. At most {{ MAX_EMBED_FIELDS }}.
    </p>
  </div>
</template>
