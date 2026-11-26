<script setup lang="ts">
// Any MAP setting without its own editor: key/value rows for texts, numbers, switches and text
// lists, JSON for nested values, and a whole-value JSON mode.
import type { JsonValue } from '#shared/protocol/schema'

const props = defineProps<{
  modelValue: Record<string, JsonValue>
}>()
const emit = defineEmits<{
  'update:modelValue': [value: Record<string, JsonValue>]
}>()

type Kind = 'text' | 'number' | 'boolean' | 'list' | 'json'

interface Cell {
  kind: Kind
  value: JsonValue
  json: string
  error: string | null
}

function kindOf(value: JsonValue): Kind {
  if (typeof value === 'string') {
    return 'text'
  }
  if (typeof value === 'number') {
    return 'number'
  }
  if (typeof value === 'boolean') {
    return 'boolean'
  }
  if (Array.isArray(value) && value.every(item => typeof item === 'string')) {
    return 'list'
  }
  return 'json'
}

const { rows, commit, add, remove, duplicates } = useMapRows<Cell>(
  () => props.modelValue,
  value => emit('update:modelValue', value),
  value => ({ kind: kindOf(value), value, json: JSON.stringify(value, null, 2), error: null }),
  cell => cell.value
)

function setJson(cell: Cell, text: string) {
  cell.json = text
  try {
    cell.value = JSON.parse(text) as JsonValue
    cell.error = null
    commit()
  } catch (error) {
    cell.error = (error as Error).message
  }
}

const jsonMode = ref(false)
const whole = ref('')
const wholeError = ref<string | null>(null)

watch(jsonMode, (on) => {
  if (on) {
    whole.value = JSON.stringify(props.modelValue, null, 2)
    wholeError.value = null
  }
})

function setWhole(text: string) {
  whole.value = text
  try {
    const parsed = JSON.parse(text) as unknown
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
      wholeError.value = 'Must be a JSON object ({ ... }).'
      return
    }
    wholeError.value = null
    emit('update:modelValue', parsed as Record<string, JsonValue>)
  } catch (error) {
    wholeError.value = (error as Error).message
  }
}
</script>

<template>
  <div class="space-y-2">
    <USwitch
      v-model="jsonMode"
      label="Edit as JSON"
      size="sm"
    />

    <template v-if="jsonMode">
      <UTextarea
        :model-value="whole"
        :rows="8"
        autoresize
        class="w-full font-mono"
        aria-label="Value as JSON"
        spellcheck="false"
        @update:model-value="setWhole(String($event))"
      />
      <p
        v-if="wholeError"
        class="text-xs text-error"
      >
        {{ wholeError }}
      </p>
    </template>

    <template v-else>
      <div
        v-for="row in rows"
        :key="row.id"
        class="grid gap-2 sm:grid-cols-[10rem_minmax(0,1fr)_auto]"
      >
        <UInput
          :model-value="row.key"
          placeholder="key"
          aria-label="Key"
          class="w-full font-mono"
          size="sm"
          :color="row.key && !duplicates.has(row.key) ? 'neutral' : 'error'"
          @update:model-value="row.key = String($event); commit()"
        />
        <div class="min-w-0">
          <UInput
            v-if="row.value.kind === 'text'"
            :model-value="String(row.value.value)"
            size="sm"
            class="w-full"
            :aria-label="`Value of ${row.key}`"
            @update:model-value="row.value.value = String($event); commit()"
          />
          <UInput
            v-else-if="row.value.kind === 'number'"
            :model-value="String(row.value.value)"
            type="number"
            size="sm"
            class="w-40"
            :aria-label="`Value of ${row.key}`"
            @update:model-value="row.value.value = Number($event); commit()"
          />
          <USwitch
            v-else-if="row.value.kind === 'boolean'"
            :model-value="row.value.value === true"
            :aria-label="`Value of ${row.key}`"
            @update:model-value="row.value.value = $event; commit()"
          />
          <UInputTags
            v-else-if="row.value.kind === 'list'"
            :model-value="row.value.value as string[]"
            size="sm"
            class="w-full"
            add-on-blur
            :aria-label="`Values of ${row.key}`"
            @update:model-value="row.value.value = [...$event]; commit()"
          />
          <template v-else>
            <UTextarea
              :model-value="row.value.json"
              :rows="3"
              autoresize
              size="sm"
              class="w-full font-mono"
              spellcheck="false"
              :aria-label="`Value of ${row.key} as JSON`"
              @update:model-value="setJson(row.value, String($event))"
            />
            <p
              v-if="row.value.error"
              class="text-xs text-error"
            >
              {{ row.value.error }}
            </p>
          </template>
        </div>
        <UButton
          icon="i-lucide-x"
          color="neutral"
          variant="ghost"
          size="sm"
          :aria-label="`Remove ${row.key}`"
          @click="remove(row)"
        />
      </div>
      <div class="flex flex-wrap gap-2">
        <UButton
          icon="i-lucide-plus"
          label="Text"
          color="neutral"
          variant="outline"
          size="xs"
          @click="add('', { kind: 'text', value: '', json: '\'\'', error: null })"
        />
        <UButton
          icon="i-lucide-plus"
          label="List"
          color="neutral"
          variant="outline"
          size="xs"
          @click="add('', { kind: 'list', value: [], json: '[]', error: null })"
        />
        <UButton
          icon="i-lucide-plus"
          label="JSON"
          color="neutral"
          variant="outline"
          size="xs"
          @click="add('', { kind: 'json', value: {}, json: '{}', error: null })"
        />
      </div>
    </template>
  </div>
</template>
