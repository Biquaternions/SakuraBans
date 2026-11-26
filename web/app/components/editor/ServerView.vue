<script setup lang="ts">
// A server's effective configuration: every key with the layer it comes from (protocol 5.1).
import { displayValue } from '#shared/editor/draft'
import { categoriesOf, layerText, resolveSetting, servers } from '#shared/editor/layers'
import type { Layer } from '#shared/editor/layers'
import type { SettingGroup } from '#shared/protocol/schema'
import { GLOBAL } from '#shared/protocol/schema'

const props = defineProps<{
  group: SettingGroup
}>()
const emit = defineEmits<{
  jump: [scope: string]
}>()

const store = useEditor()
const list = computed(() => servers(store.payload.value))
const selected = ref<string>(list.value[0]?.id ?? '')
const server = computed(() => list.value.find(item => item.id === selected.value) ?? list.value[0] ?? null)
const categories = computed(() => (server.value ? categoriesOf(server.value, store.overrides.value) : []))

const rows = computed(() => {
  if (!server.value) {
    return []
  }
  return (store.payload.value.settings?.fields ?? [])
    .filter(field => field.group === props.group)
    .map(field => ({ field, resolved: resolveSetting(field, server.value!, store.overrides.value) }))
})

function scopeOfLayer(layer: Layer): string | null {
  return layer.kind === 'server' || layer.kind === 'category' ? layer.scope : layer.kind === 'global' ? GLOBAL : null
}

const COLOR = { server: 'primary', category: 'info', global: 'success', file: 'neutral', default: 'neutral' } as const
const seen = (at: number) => new Date(at).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
</script>

<template>
  <div class="space-y-3">
    <p
      v-if="list.length === 0"
      class="text-muted"
    >
      The plugin sent no servers (the instance registry is empty).
    </p>
    <template v-else>
      <div class="flex flex-wrap items-end gap-3">
        <UFormField label="Server">
          <USelect
            v-model="selected"
            :items="list.map(item => ({ label: item.name || `(unnamed, ${item.id})`, value: item.id }))"
            class="w-64"
          />
        </UFormField>
        <p
          v-if="server"
          class="text-sm text-muted"
        >
          {{ server.platform }} · SakuraBans {{ server.pluginVersion }} · last seen {{ seen(server.lastSeenAt) }}
          <br>
          Categories, in the order they apply: {{ categories.length > 0 ? categories.join(' → ') : 'none' }}
        </p>
      </div>
      <p class="text-xs text-muted">
        Each key comes from the first layer that sets it: this server's scope, its categories in the order above, global, its own file, the default. Pending edits are included.
      </p>
      <div class="divide-y divide-default overflow-hidden rounded-lg border border-default">
        <div
          v-for="{ field, resolved } in rows"
          :key="field.key"
          class="grid gap-2 p-3 md:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto]"
        >
          <span class="font-mono text-sm break-all text-highlighted">{{ field.key }}</span>
          <code class="text-sm break-all">{{ resolved.value === undefined ? '(from its file: not known here)' : displayValue(resolved.value, field.secret) }}</code>
          <div class="text-end">
            <UButton
              v-if="scopeOfLayer(resolved.layer)"
              :label="layerText(resolved.layer)"
              :color="COLOR[resolved.layer.kind]"
              variant="subtle"
              size="xs"
              trailing-icon="i-lucide-arrow-right"
              @click="emit('jump', scopeOfLayer(resolved.layer)!)"
            />
            <UBadge
              v-else
              :label="layerText(resolved.layer)"
              color="neutral"
              variant="outline"
              size="sm"
            />
          </div>
          <p
            v-if="resolved.shadowed.length > 0"
            class="text-xs text-warning md:col-span-3"
          >
            Also set by {{ resolved.shadowed.map(name => `category ${name}`).join(', ') }}. {{ layerText(resolved.layer) }} wins because it comes first: categories apply in the order scopes.categories declares them, then the server's own.
          </p>
        </div>
      </div>
    </template>
  </div>
</template>
