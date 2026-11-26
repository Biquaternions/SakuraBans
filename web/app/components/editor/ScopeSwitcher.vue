<script setup lang="ts">
// Global, each category and each server the network knows, with how many overrides (and
// pending changes) each has; a scope can be added by typing a server or category name.
import { changesPerScope } from '#shared/editor/draft'
import { effectiveCategories, scopeKind, scopeName, uploadingServer } from '#shared/editor/layers'
import { IDENTIFIER_HINT, isIdentifier } from '#shared/editor/limits'
import { GLOBAL } from '#shared/protocol/schema'

const props = defineProps<{
  section: 'settings' | 'messages'
  /** Messages: overrides are counted for this locale. */
  locale?: string
}>()

const scope = defineModel<string>({ required: true })

const store = useEditor()

const known = computed(() => {
  const p = store.payload.value
  const categories = new Set<string>(p.lookups.categories)
  const servers = new Set<string>(p.lookups.servers)
  for (const name of Object.keys(effectiveCategories(store.overrides.value, uploadingServer(p)))) {
    categories.add(name)
  }
  for (const server of p.settings?.servers ?? []) {
    if (server.name) {
      servers.add(server.name)
    }
    server.categories.forEach(category => categories.add(category))
  }
  const listed = [
    ...(p.settings?.scopes ?? []).map(item => item.scope),
    ...(p.messages?.scopes ?? []).map(item => item.scope),
    ...Object.keys(store.draft.settings),
    ...Object.keys(store.draft.messages),
    ...store.addedScopes.value
  ]
  for (const item of listed) {
    if (item === GLOBAL) {
      continue
    }
    (scopeKind(item) === 'server' ? servers : categories).add(scopeName(item))
  }
  return { categories: [...categories].sort(), servers: [...servers].sort() }
})

function overrideCount(item: string): number {
  const p = store.payload.value
  if (props.section === 'settings') {
    return Object.keys(p.settings?.scopes.find(entry => entry.scope === item)?.overrides ?? {}).length
  }
  return Object.keys(p.messages?.scopes.find(entry => entry.scope === item && entry.locale === props.locale)?.overrides ?? {}).length
}

const pending = computed(() => changesPerScope(store.changes.value, props.section))

function label(item: string): string {
  const base = item === GLOBAL ? 'Global' : scopeName(item)
  const count = overrideCount(item)
  const changes = pending.value.get(item) ?? 0
  const parts = [count > 0 ? `${count} override${count === 1 ? '' : 's'}` : '', changes > 0 ? `${changes} change${changes === 1 ? '' : 's'}` : '']
    .filter(Boolean)
  return parts.length > 0 ? `${base} · ${parts.join(', ')}` : base
}

const items = computed(() => [
  [{ label: label(GLOBAL), value: GLOBAL, icon: 'i-lucide-globe' }],
  known.value.categories.length > 0
    ? [{ type: 'label' as const, label: 'Categories', value: '', icon: '' }, ...known.value.categories.map(name => ({ label: label(`category:${name}`), value: `category:${name}`, icon: 'i-lucide-folder' }))]
    : [],
  known.value.servers.length > 0
    ? [{ type: 'label' as const, label: 'Servers', value: '', icon: '' }, ...known.value.servers.map(name => ({ label: label(`server:${name}`), value: `server:${name}`, icon: 'i-lucide-server' }))]
    : []
].filter(group => group.length > 0))

const addOpen = ref(false)
const addKind = ref<'server' | 'category'>('server')
const addName = ref('')
const addError = computed(() => (addName.value === '' || isIdentifier(addName.value) ? null : `Names are ${IDENTIFIER_HINT}.`))

function add() {
  if (addName.value === '' || addError.value) {
    return
  }
  const next = `${addKind.value}:${addName.value}`
  store.addScope(next)
  scope.value = next
  addName.value = ''
  addOpen.value = false
}
</script>

<template>
  <div class="flex flex-wrap items-end gap-2">
    <UFormField
      label="Scope"
      class="min-w-64 flex-1 sm:flex-none"
    >
      <USelectMenu
        v-model="scope"
        :items="items"
        value-key="value"
        :search-input="{ placeholder: 'Find a server or category' }"
        class="w-full sm:w-80"
      />
    </UFormField>
    <UPopover v-model:open="addOpen">
      <UButton
        icon="i-lucide-plus"
        label="Add scope"
        color="neutral"
        variant="outline"
      />
      <template #content>
        <form
          class="w-72 space-y-2 p-3"
          @submit.prevent="add"
        >
          <UFormField label="Kind">
            <USelect
              v-model="addKind"
              :items="[{ label: 'Server', value: 'server' }, { label: 'Category', value: 'category' }]"
              class="w-full"
            />
          </UFormField>
          <UFormField
            label="Name"
            :error="addError ?? undefined"
          >
            <UInput
              v-model="addName"
              placeholder="bedwars-2"
              class="w-full font-mono"
              autofocus
              @update:model-value="addName = String($event).toLowerCase()"
            />
          </UFormField>
          <UButton
            type="submit"
            label="Add"
            size="sm"
            :disabled="addName === '' || addError !== null"
          />
        </form>
      </template>
    </UPopover>
  </div>
</template>
