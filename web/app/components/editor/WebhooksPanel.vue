<script setup lang="ts">
// webhooks.yml in a scope: the general keys, then one card per route (event) with its keys and
// a Discord-like preview of the embed. Events are discovered from the field keys.
import { fillPlaceholders, webhookPlaceholders } from '#shared/discord'
import { inheritedValue } from '#shared/editor/layers'
import type { JsonValue, SettingField } from '#shared/protocol/schema'
import { WEBHOOK_SAMPLES } from '~/utils/webhookSamples'

const props = defineProps<{
  scope: string
  fields: SettingField[]
}>()
const emit = defineEmits<{
  jump: [scope: string]
}>()

const store = useEditor()
const ROUTE = /^webhooks:routes\.([^.]+)(?:\.(.+))?$/

const general = computed(() => props.fields.filter(field => !ROUTE.test(field.key)))
const routes = computed(() => {
  const byEvent = new Map<string, SettingField[]>()
  for (const field of props.fields) {
    const match = ROUTE.exec(field.key)
    if (match) {
      const list = byEvent.get(match[1]!) ?? []
      list.push(field)
      byEvent.set(match[1]!, list)
    }
  }
  return [...byEvent.entries()].map(([event, list]) => ({ event, fields: list }))
})

/** The value that would apply in this scope: pending, else the override here, else inherited. */
function valueHere(key: string): JsonValue | undefined {
  const field = store.payload.value.settings?.fields.find(item => item.key === key)
  if (!field) {
    return undefined
  }
  const edit = store.draft.settings[props.scope]?.[key]
  if (edit?.kind === 'set') {
    return field.secret ? edit.value !== '' : edit.value
  }
  const own = store.payload.value.settings?.scopes.find(item => item.scope === props.scope)?.overrides[key]
  if (own !== undefined && edit?.kind !== 'reset') {
    return own
  }
  return inheritedValue(store.payload.value, field, props.scope, store.overrides.value).value
}

function text(value: JsonValue | undefined): string {
  return typeof value === 'string' ? value : ''
}

function embed(event: string) {
  const prefix = `webhooks:routes.${event}`
  const whole = valueHere(prefix)
  const read = (prop: string): JsonValue | undefined => {
    if (whole && typeof whole === 'object' && !Array.isArray(whole)) {
      return (whole as Record<string, JsonValue>)[prop]
    }
    return valueHere(`${prefix}.${prop}`)
  }
  const samples = Object.fromEntries(webhookPlaceholders(event).map(name => [name, WEBHOOK_SAMPLES[name] ?? name]))
  const fields = read('fields')
  const color = text(read('color'))
  return {
    enabled: read('enabled') !== false,
    title: fillPlaceholders(text(read('title')), samples),
    color: /^#[0-9a-fA-F]{6}$/.test(color) ? color : '#4f545c',
    description: fillPlaceholders(text(read('description')), samples),
    fields: fields && typeof fields === 'object' && !Array.isArray(fields)
      ? Object.entries(fields as Record<string, JsonValue>)
          .map(([name, value]) => ({ name, value: fillPlaceholders(text(value), samples) }))
          .filter(field => field.value.trim() !== '')
      : []
  }
}

const username = computed(() => text(valueHere('webhooks:username')) || 'SakuraBans')
const open = ref<Set<string>>(new Set())

function toggle(event: string) {
  const next = new Set(open.value)
  if (next.has(event)) {
    next.delete(event)
  } else {
    next.add(event)
  }
  open.value = next
}
</script>

<template>
  <div class="space-y-6">
    <div class="divide-y divide-default overflow-hidden rounded-lg border border-default">
      <EditorScopedSettingRow
        v-for="field in general"
        :key="field.key"
        :field="field"
        :scope="scope"
        @jump="emit('jump', $event)"
      />
    </div>

    <section
      v-for="route in routes"
      :key="route.event"
      class="rounded-lg border border-default"
      :aria-labelledby="`route-${route.event}`"
    >
      <button
        :id="`route-${route.event}`"
        type="button"
        class="flex w-full items-center gap-3 p-3 text-start focus-visible:outline-2 focus-visible:outline-primary"
        :aria-expanded="open.has(route.event)"
        @click="toggle(route.event)"
      >
        <span
          class="h-6 w-1 rounded"
          :style="{ backgroundColor: embed(route.event).color }"
        />
        <span class="font-mono font-semibold text-highlighted">{{ route.event }}</span>
        <UBadge
          :label="embed(route.event).enabled ? 'posted' : 'not posted'"
          :color="embed(route.event).enabled ? 'success' : 'neutral'"
          variant="subtle"
          size="sm"
        />
        <span class="truncate text-sm text-muted">{{ embed(route.event).title }}</span>
        <UIcon
          :name="open.has(route.event) ? 'i-lucide-chevron-up' : 'i-lucide-chevron-down'"
          class="ms-auto size-4"
        />
      </button>
      <div
        v-if="open.has(route.event)"
        class="grid gap-4 border-t border-default p-3 lg:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)]"
      >
        <div class="divide-y divide-default overflow-hidden rounded-md border border-default">
          <EditorScopedSettingRow
            v-for="field in route.fields"
            :key="field.key"
            :field="field"
            :scope="scope"
            @jump="emit('jump', $event)"
          />
        </div>
        <div>
          <p class="mb-1 text-sm font-medium">
            Preview
          </p>
          <div
            class="discord-chat rounded-md p-4 text-sm"
            role="img"
            :aria-label="`Preview of the ${route.event} embed`"
          >
            <p class="mb-1 font-semibold text-white">
              {{ username }} <span class="discord-badge ms-1 rounded px-1 text-[10px] font-semibold uppercase">App</span>
            </p>
            <div
              class="discord-embed max-w-md rounded border-s-4 p-3"
              :style="{ borderInlineStartColor: embed(route.event).color }"
            >
              <EditorDiscordText
                v-if="embed(route.event).title"
                :text="embed(route.event).title"
                tag="p"
                class="mb-1 font-semibold text-white"
              />
              <EditorDiscordText
                v-if="embed(route.event).description"
                :text="embed(route.event).description"
                tag="p"
              />
              <div
                v-if="embed(route.event).fields.length > 0"
                class="mt-2 grid grid-cols-2 gap-2"
              >
                <div
                  v-for="field in embed(route.event).fields"
                  :key="field.name"
                >
                  <EditorDiscordText
                    :text="field.name"
                    tag="p"
                    class="text-xs font-semibold text-white"
                  />
                  <EditorDiscordText
                    :text="field.value"
                    tag="p"
                    class="text-xs"
                  />
                </div>
              </div>
            </div>
          </div>
          <EditorPlaceholderList
            class="mt-2"
            :names="webhookPlaceholders(route.event)"
            label="This event knows:"
          />
        </div>
      </div>
    </section>
    <p
      v-if="routes.length === 0"
      class="text-sm text-muted"
    >
      The plugin sent no webhook routes.
    </p>
  </div>
</template>
