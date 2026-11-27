<script setup lang="ts">
// Settings, limits and webhooks per scope (protocol 5.1, 5.2). Global is the default view.
// A server view shows one server's effective configuration.
import { settingGroup } from '#shared/editor/settings'
import type { SettingField, SettingGroup } from '#shared/protocol/schema'
import { GLOBAL } from '#shared/protocol/schema'

const store = useEditor()
const fields = computed(() => store.payload.value.settings?.fields ?? [])

const GROUP_LABEL: Record<SettingGroup, { label: string, icon: string }> = {
  config: { label: 'Settings', icon: 'i-lucide-settings' },
  limits: { label: 'Limits', icon: 'i-lucide-gauge' },
  webhooks: { label: 'Webhooks', icon: 'i-lucide-webhook' }
}
const groups = computed(() => (['config', 'limits', 'webhooks'] as const).filter(group => fields.value.some(field => field.group === group)))
const group = ref<SettingGroup>(groups.value[0] ?? 'config')

const scope = ref<string>(GLOBAL)
const mode = ref<'scope' | 'server'>('scope')
const query = ref('')
const onlyOverridden = ref(false)
const publishOpen = ref(false)

function jump(target: string) {
  mode.value = 'scope'
  scope.value = target
}

function overriddenHere(field: SettingField): boolean {
  const edit = store.draft.settings[scope.value]?.[field.key]
  const stored = store.payload.value.settings?.scopes.find(item => item.scope === scope.value)?.overrides[field.key]
  return edit !== undefined || stored !== undefined
}

const visible = computed(() => {
  const needle = query.value.trim().toLowerCase()
  return fields.value.filter(field => field.group === group.value
    && (!needle || field.key.toLowerCase().includes(needle) || field.description.toLowerCase().includes(needle))
    && (!onlyOverridden.value || overriddenHere(field)))
})

const sections = computed(() => {
  const result = new Map<string, SettingField[]>()
  for (const field of visible.value) {
    const name = group.value === 'config' ? settingGroup(field.key) : GROUP_LABEL[group.value].label.toLowerCase()
    result.set(name, [...(result.get(name) ?? []), field])
  }
  return [...result.entries()]
})
</script>

<template>
  <div class="space-y-4">
    <UTabs
      v-if="groups.length > 1"
      v-model="group"
      :items="groups.map(item => ({ label: GROUP_LABEL[item].label, icon: GROUP_LABEL[item].icon, value: item }))"
      :content="false"
      size="sm"
    />

    <div class="flex flex-wrap items-end gap-3">
      <UFieldGroup>
        <UButton
          label="By scope"
          icon="i-lucide-layers"
          :variant="mode === 'scope' ? 'solid' : 'outline'"
          color="neutral"
          :aria-pressed="mode === 'scope'"
          @click="mode = 'scope'"
        />
        <UButton
          label="Server view"
          icon="i-lucide-server"
          :variant="mode === 'server' ? 'solid' : 'outline'"
          color="neutral"
          :aria-pressed="mode === 'server'"
          @click="mode = 'server'"
        />
      </UFieldGroup>
      <EditorScopeSwitcher
        v-if="mode === 'scope'"
        v-model="scope"
        section="settings"
      />
    </div>

    <template v-if="mode === 'server'">
      <EditorServerView
        :group="group"
        @jump="jump"
      />
    </template>

    <template v-else>
      <p class="text-sm text-muted">
        <template v-if="scope === 'global'">
          Global applies to every server unless a category or server overrides it. Without a global override, each server uses its own file.
        </template>
        <template v-else>
          Overrides here apply to {{ scope.startsWith('server:') ? `the server ${scope.slice(7)}` : `every server in the category ${scope.slice(9)}` }}. Keys without an override inherit, as shown.
        </template>
      </p>

      <div
        v-if="scope === 'global'"
        class="space-y-2"
      >
        <UButton
          :icon="publishOpen ? 'i-lucide-chevron-up' : 'i-lucide-upload'"
          :label="store.draft.publish ? `Publish ${store.draft.publish.server}'s file values (${store.draft.publish.keys.length} key${store.draft.publish.keys.length === 1 ? '' : 's'} ticked)` : 'Publish a server\'s file values'"
          color="neutral"
          variant="outline"
          size="sm"
          :aria-expanded="publishOpen"
          @click="publishOpen = !publishOpen"
        />
        <EditorPublishPanel
          v-if="publishOpen"
          :group="group"
        />
      </div>

      <div class="flex flex-wrap items-center gap-3">
        <UInput
          v-model="query"
          icon="i-lucide-search"
          placeholder="Filter keys"
          aria-label="Filter settings by key or description"
          class="w-full sm:w-72"
        />
        <USwitch
          v-model="onlyOverridden"
          label="Only overridden here"
        />
      </div>

      <EditorWebhooksPanel
        v-if="group === 'webhooks'"
        :scope="scope"
        :fields="visible"
        @jump="jump"
      />

      <template v-else>
        <p
          v-if="sections.length === 0"
          class="py-8 text-center text-muted"
        >
          No key matches.
        </p>
        <section
          v-for="[name, items] in sections"
          :key="name"
          :aria-labelledby="`group-${name}`"
        >
          <h2
            :id="`group-${name}`"
            class="mb-2 font-mono text-sm font-semibold tracking-wide text-muted uppercase"
          >
            {{ name }}
          </h2>
          <div class="divide-y divide-default overflow-hidden rounded-lg border border-default">
            <EditorScopedSettingRow
              v-for="field in items"
              :key="field.key"
              :field="field"
              :scope="scope"
              @jump="jump"
            />
          </div>
        </section>
      </template>
    </template>
  </div>
</template>
