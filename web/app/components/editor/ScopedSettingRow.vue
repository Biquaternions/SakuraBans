<script setup lang="ts">
// One key in one scope: what the scope inherits and from where, and the override here.
import { displayValue, settingEditMatters, settingOverride } from '#shared/editor/draft'
import { inheritedValue, layerText } from '#shared/editor/layers'
import type { Layer } from '#shared/editor/layers'
import type { JsonValue, SettingField } from '#shared/protocol/schema'
import { GLOBAL } from '#shared/protocol/schema'

const props = defineProps<{
  field: SettingField
  scope: string
}>()
const emit = defineEmits<{
  jump: [scope: string]
}>()

const store = useEditor()
// The field object is replaced when a fresh snapshot arrives; the row (and its focus) stays.
const field = toRef(props, 'field')
const inputId = computed(() => `setting-${props.scope.replace(/[^a-z0-9]/gi, '-')}-${props.field.key.replace(/[^a-z0-9]/gi, '-')}`)

const edit = computed(() => store.draft.settings[props.scope]?.[field.value.key])
const stored = computed(() => settingOverride(store.payload.value, props.scope, field.value.key))
const changed = computed(() => edit.value !== undefined && settingEditMatters(store.payload.value, props.scope, field.value.key, edit.value))
const overriding = computed(() => edit.value ? edit.value.kind === 'set' : stored.value !== undefined)
const readOnly = computed(() => field.value.level === 'GLOBAL' && props.scope !== GLOBAL)
const inherited = computed(() => inheritedValue(store.payload.value, field.value, props.scope, store.overrides.value))
const issues = computed(() => store.fieldIssues.value.filter(issue => issue.ref.section === 'settings'
  && issue.ref.scope === props.scope && issue.ref.key === field.value.key))
const stale = computed(() => changed.value && store.isStale('settings'))
const showDiffering = ref(false)

/** The value the control edits: the pending one, else the stored override (never a secret). */
const value = computed<JsonValue>({
  get: () => {
    if (edit.value?.kind === 'set') {
      return edit.value.value
    }
    return field.value.secret ? '' : (stored.value ?? inherited.value.value ?? field.value.default)
  },
  set: next => store.setSetting(props.scope, field.value, next)
})

/** A stored secret with no new value typed: only "set" or "empty" is known. */
const secretUntouched = computed(() => field.value.secret && edit.value?.kind !== 'set' && stored.value !== undefined)

function toggle(on: boolean) {
  if (on) {
    store.overrideHere(props.scope, field.value, inherited.value.value)
  } else {
    store.removeOverride(props.scope, field.value)
  }
}

function layerScope(layer: Layer): string | null {
  return layer.kind === 'server' || layer.kind === 'category' ? layer.scope : layer.kind === 'global' ? GLOBAL : null
}

const inheritedText = computed(() => {
  const value = inherited.value.value
  return value === undefined ? 'not known here (only that server\'s file has it)' : displayValue(value, field.value.secret)
})
</script>

<template>
  <div
    class="grid gap-3 p-4 md:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)]"
    :class="changed ? 'bg-primary/5' : ''"
  >
    <div class="min-w-0">
      <div class="flex flex-wrap items-center gap-1.5">
        <label
          :for="inputId"
          class="font-mono text-sm font-medium break-all text-highlighted"
        >{{ field.key }}</label>
        <UBadge
          v-if="overriding"
          label="overridden here"
          color="primary"
          variant="subtle"
          size="sm"
        />
        <UBadge
          v-if="field.level === 'GLOBAL'"
          label="global only"
          color="neutral"
          variant="outline"
          size="sm"
        />
        <UBadge
          v-if="field.secret"
          label="secret"
          color="neutral"
          variant="outline"
          size="sm"
          icon="i-lucide-lock"
        />
        <UBadge
          v-if="changed"
          :label="edit?.kind === 'reset' ? 'override removed' : 'changed'"
          color="warning"
          variant="subtle"
          size="sm"
        />
        <UBadge
          v-if="stale"
          label="snapshot out of date"
          color="error"
          variant="subtle"
          size="sm"
        />
      </div>
      <p class="mt-1 text-sm text-muted">
        {{ field.description }}
      </p>
      <p class="mt-1 text-xs text-muted">
        {{ overriding ? 'Would inherit' : 'Inherits' }}
        <code class="break-all">{{ inheritedText }}</code>
        from
        <button
          v-if="layerScope(inherited.layer)"
          type="button"
          class="underline decoration-dotted hover:text-highlighted focus-visible:outline-2 focus-visible:outline-primary"
          @click="emit('jump', layerScope(inherited.layer)!)"
        >
          {{ layerText(inherited.layer) }}
        </button>
        <span v-else>{{ scope === 'global' ? `each server's own file (shown: ${layerText(inherited.layer)})` : layerText(inherited.layer) }}</span><template v-if="inherited.shadowed.length > 0">
          (also set by {{ inherited.shadowed.join(', ') }}, which come later in the category order)
        </template>
      </p>
      <p
        v-if="scope === 'global' && inherited.differing.length > 0"
        class="mt-1 text-xs text-warning"
      >
        The file value differs on {{ inherited.differing.length }} server{{ inherited.differing.length === 1 ? '' : 's' }}.
        <button
          type="button"
          class="underline"
          :aria-expanded="showDiffering"
          @click="showDiffering = !showDiffering"
        >
          {{ showDiffering ? 'Hide' : 'Show' }}
        </button>
        <span
          v-if="showDiffering"
          class="block font-mono"
        >
          <span
            v-for="name in inherited.differing"
            :key="name"
            class="block"
          >{{ name }}: {{ displayValue(store.payload.value.settings?.servers.find(item => (item.name || item.id) === name)?.fileValues[field.key], field.secret) }}</span>
        </span>
      </p>
    </div>

    <div class="min-w-0">
      <p
        v-if="readOnly"
        class="text-sm text-muted"
      >
        Only the global scope can set this key: it must be the same on every server.
        <button
          type="button"
          class="underline"
          @click="emit('jump', 'global')"
        >
          Go to global
        </button>
      </p>
      <template v-else>
        <USwitch
          :model-value="overriding"
          :label="overriding ? 'Override here' : 'Override here (off: inherits)'"
          size="sm"
          class="mb-2"
          @update:model-value="toggle"
        />
        <div v-if="overriding">
          <div
            v-if="secretUntouched"
            class="flex flex-wrap items-center gap-2 text-sm"
          >
            <UBadge
              :label="stored === true ? 'A value is set' : 'Set to empty'"
              color="neutral"
              variant="subtle"
            />
            <UButton
              size="xs"
              color="neutral"
              variant="outline"
              icon="i-lucide-key-round"
              label="Replace value"
              @click="store.setSetting(scope, field, '')"
            />
          </div>
          <EditorSettingControl
            v-else
            :id="inputId"
            v-model="value"
            :field="field"
          />
        </div>
      </template>

      <ul
        v-if="issues.length > 0"
        class="mt-2 space-y-0.5 text-sm text-error"
        role="alert"
      >
        <li
          v-for="issue in issues"
          :key="issue.path + issue.message"
        >
          {{ issue.message }}
        </li>
      </ul>

      <UButton
        v-if="changed"
        class="mt-2"
        size="xs"
        color="neutral"
        variant="ghost"
        icon="i-lucide-undo-2"
        label="Undo"
        @click="store.undoSetting(scope, field.key)"
      />
    </div>
  </div>
</template>
