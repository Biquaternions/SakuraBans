<script setup lang="ts">
import { IDENTIFIER_HINT, isIdentifier } from '#shared/editor/limits'
import type { JsonValue } from '#shared/protocol/schema'

const props = defineProps<{
  modelValue: Record<string, JsonValue>
}>()
const emit = defineEmits<{
  'update:modelValue': [value: Record<string, JsonValue>]
}>()

const store = useEditor()

const { rows, commit, add, remove, duplicates } = useMapRows<string[]>(
  () => props.modelValue,
  value => emit('update:modelValue', value),
  value => (Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : []),
  value => [...value]
)

const serverItems = computed(() => {
  const names = new Set(store.payload.value.lookups.servers)
  for (const row of rows.value) {
    row.value.forEach(server => names.add(server))
  }
  return [...names].sort()
})

const suggestedCategories = computed(() =>
  store.payload.value.lookups.categories.filter(name => !rows.value.some(row => row.key === name)))

function addCategory(name = '') {
  add(name, [])
}

function setServers(row: { value: string[] }, servers: string[]) {
  row.value = [...new Set(servers.map(server => server.trim().toLowerCase()).filter(Boolean))]
  commit()
}

function createServer(row: { value: string[] }, name: string) {
  setServers(row, [...row.value, name])
}
</script>

<template>
  <div class="space-y-3">
    <p
      v-if="rows.length === 0"
      class="text-sm text-muted"
    >
      No categories. A category groups servers, so a <code>category:&lt;name&gt;</code> punishment applies on all of them.
    </p>
    <div
      v-for="row in rows"
      :key="row.id"
      class="rounded-md border border-default p-3"
    >
      <div class="flex items-center gap-2">
        <UInput
          :model-value="row.key"
          placeholder="category"
          aria-label="Category name"
          class="w-48 font-mono"
          size="sm"
          :color="isIdentifier(row.key) && !duplicates.has(row.key) ? 'neutral' : 'error'"
          @update:model-value="row.key = String($event).toLowerCase(); commit()"
        />
        <UButton
          icon="i-lucide-trash-2"
          color="neutral"
          variant="ghost"
          size="sm"
          :aria-label="`Remove category ${row.key}`"
          class="ms-auto"
          @click="remove(row)"
        />
      </div>
      <p
        v-if="!isIdentifier(row.key) || duplicates.has(row.key)"
        class="mt-1 text-xs text-error"
      >
        {{ duplicates.has(row.key) ? 'This category is listed twice.' : `Names are ${IDENTIFIER_HINT}.` }}
      </p>
      <UInputMenu
        :model-value="row.value"
        :items="serverItems"
        multiple
        create-item
        placeholder="Servers in this category"
        :aria-label="`Servers in ${row.key || 'the category'}`"
        class="mt-2 w-full"
        @update:model-value="setServers(row, $event as string[])"
        @create="createServer(row, $event)"
      />
    </div>
    <div class="flex flex-wrap items-center gap-2">
      <UButton
        icon="i-lucide-plus"
        label="Add category"
        color="neutral"
        variant="outline"
        size="xs"
        @click="addCategory()"
      />
      <UButton
        v-for="name in suggestedCategories"
        :key="name"
        :label="name"
        icon="i-lucide-plus"
        color="neutral"
        variant="ghost"
        size="xs"
        @click="addCategory(name)"
      />
    </div>
  </div>
</template>
