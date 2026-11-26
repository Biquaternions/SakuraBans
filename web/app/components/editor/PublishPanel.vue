<script setup lang="ts">
// Publish a server's file values as the global overrides (protocol 6, `publish`): a diff of the
// server's file against the current global values, with the keys to publish ticked.
import { displayValue } from '#shared/editor/draft'
import { servers, uploadingServer } from '#shared/editor/layers'
import type { SettingGroup } from '#shared/protocol/schema'
import { GLOBAL } from '#shared/protocol/schema'

const props = defineProps<{
  group: SettingGroup
}>()

const store = useEditor()
const list = computed(() => servers(store.payload.value).filter(item => item.name !== ''))
const server = ref<string>(store.draft.publish?.server ?? uploadingServer(store.payload.value)?.name ?? list.value[0]?.name ?? '')
const info = computed(() => list.value.find(item => item.name === server.value) ?? null)

/** The global value without this panel's own publishing, so the diff stays put while ticking. */
function globalValue(key: string) {
  const edit = store.draft.settings[GLOBAL]?.[key]
  if (edit) {
    return edit.kind === 'set' ? edit.value : undefined
  }
  return store.payload.value.settings?.scopes.find(item => item.scope === GLOBAL)?.overrides[key]
}

const diff = computed(() => {
  if (!info.value) {
    return []
  }
  return (store.payload.value.settings?.fields ?? [])
    .filter(field => field.group === props.group && field.key in info.value!.fileValues)
    .map(field => ({ field, file: info.value!.fileValues[field.key], global: globalValue(field.key) }))
    .filter(row => differs(row.file, row.global))
})

function differs(file: unknown, global: unknown): boolean {
  return global === undefined || JSON.stringify(file) !== JSON.stringify(global)
}

const selected = computed(() => (store.draft.publish?.server === server.value ? store.draft.publish.keys : []))

function toggle(key: string, on: boolean) {
  const keys = new Set(selected.value)
  if (on) {
    keys.add(key)
  } else {
    keys.delete(key)
  }
  // Other groups' keys published from the same server stay ticked.
  const others = store.draft.publish?.server === server.value
    ? store.draft.publish.keys.filter(other => !diff.value.some(row => row.field.key === other))
    : []
  store.setPublish(server.value, [...others, ...diff.value.map(row => row.field.key).filter(item => keys.has(item))])
}

function changeServer(name: string) {
  if (store.draft.publish && store.draft.publish.server !== name) {
    store.setPublish(name, [])
  }
  server.value = name
}
</script>

<template>
  <div class="space-y-3 rounded-lg border border-default p-4">
    <div class="flex flex-wrap items-end gap-3">
      <UFormField
        label="Publish the file values of"
        help="They become the global overrides. Taken from the server's report, so secrets publish without this site seeing them."
      >
        <USelect
          :model-value="server"
          :items="list.map(item => ({ label: item.name, value: item.name }))"
          class="w-64"
          @update:model-value="changeServer(String($event))"
        />
      </UFormField>
    </div>
    <p
      v-if="diff.length === 0"
      class="text-sm text-muted"
    >
      Nothing to publish here: global already has every file value of {{ server || 'this server' }}.
    </p>
    <div
      v-else
      class="divide-y divide-default rounded-md border border-default"
    >
      <label
        v-for="row in diff"
        :key="row.field.key"
        class="grid cursor-pointer gap-2 p-2 text-sm md:grid-cols-[auto_minmax(0,1fr)_minmax(0,1fr)_minmax(0,1fr)]"
      >
        <UCheckbox
          :model-value="selected.includes(row.field.key)"
          :aria-label="`Publish ${row.field.key}`"
          @update:model-value="toggle(row.field.key, $event === true)"
        />
        <span class="font-mono break-all">{{ row.field.key }}</span>
        <span class="text-muted">global: <code class="break-all">{{ displayValue(row.global, row.field.secret) }}</code></span>
        <span>file: <code class="break-all">{{ displayValue(row.file, row.field.secret) }}</code></span>
      </label>
    </div>
  </div>
</template>
