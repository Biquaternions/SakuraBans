<script setup lang="ts">
import { NETWORK_COUNT_TAGS, messageTagsKnown } from '#shared/editor/countTags'
import { messageEditMatters, messageOverride } from '#shared/editor/draft'
import { fileText, inheritedMessage, layerText, scopeKind } from '#shared/editor/layers'
import { MAX_MESSAGE } from '#shared/editor/limits'
import { unknownTags } from '#shared/minimessage'
import { messageStyles, STYLE_TAGS } from '#shared/editor/styles'
import type { MessageField } from '#shared/protocol/schema'
import { GLOBAL } from '#shared/protocol/schema'
import { sampleFor } from '~/utils/samples'

const props = defineProps<{
  field: MessageField
  locale: string
  scope: string
}>()
const emit = defineEmits<{
  jump: [scope: string]
}>()

const store = useEditor()
const payload = computed(() => store.payload.value)
// Replaced when a fresh snapshot arrives; the editor (and its focus) stays mounted.
const field = toRef(props, 'field')

const stored = computed(() => messageOverride(payload.value, props.scope, props.locale, field.value.key))
const edit = computed(() => store.draft.messages[props.scope]?.[props.locale]?.[field.value.key])
const changed = computed(() => edit.value !== undefined
  && messageEditMatters(payload.value, props.scope, props.locale, field.value.key, edit.value))
const overriding = computed(() => (edit.value ? edit.value.kind === 'set' : stored.value !== undefined))
const inherited = computed(() => inheritedMessage(payload.value, store.messageOverrides.value, store.overrides.value,
  props.scope, props.locale, field.value.key))
/** The text this scope shows: its override, else what it inherits (default when only a server file knows it). */
const shownText = computed(() => {
  if (edit.value?.kind === 'set') {
    return edit.value.text
  }
  if (edit.value?.kind !== 'reset' && stored.value !== undefined) {
    return stored.value
  }
  return inherited.value.text ?? field.value.default
})
const text = computed({
  get: () => shownText.value,
  set: next => store.setMessage(props.scope, props.locale, field.value.key, next)
})
const issues = computed(() => store.fieldIssues.value.filter(issue =>
  issue.ref.section === 'messages' && issue.ref.scope === props.scope && issue.ref.locale === props.locale
  && issue.ref.key === field.value.key))

function toggle(on: boolean) {
  if (on) {
    store.setMessage(props.scope, props.locale, field.value.key, inherited.value.text ?? field.value.default)
  } else {
    store.removeMessageOverride(props.scope, props.locale, field.value.key)
  }
}

function layerScope(): string | null {
  const layer = inherited.value.layer
  return layer.kind === 'server' || layer.kind === 'category' ? layer.scope : layer.kind === 'global' ? GLOBAL : null
}

const inheritedFrom = computed(() => {
  const layer = inherited.value.layer
  if (layer.kind !== 'file') {
    return layerText(layer)
  }
  if (scopeKind(props.scope) === 'server' && inherited.value.text === undefined) {
    return 'that server\'s language file (its text isn\'t sent to the editor; the default is shown)'
  }
  return props.scope === GLOBAL ? `each server's language file (shown: ${payload.value.meta.server || 'the uploading server'}'s)` : `${layer.server || 'the server'}'s language file`
})

const samples = reactive<Record<string, string>>(Object.fromEntries(messageTagsKnown(props.field.placeholders).map(name => [name, sampleFor(name)])))

const prefixField = computed(() => payload.value.messages?.fields.find(item => item.key === 'prefix'))
/** `<prefix>`, `<plugin>` and the palette as this scope shows them, drafts included (S74). */
const styles = computed(() => messageStyles((key) => {
  if (key === field.value.key) {
    return text.value
  }
  if (!payload.value.messages?.fields.some(item => item.key === key)) {
    return undefined
  }
  const edit = store.draft.messages[props.scope]?.[props.locale]?.[key]
  return edit?.kind === 'set' ? edit.text : messageOverride(payload.value, props.scope, props.locale, key) ?? fileText(payload.value, props.locale, key)
}, field.value.key))
const components = computed(() => styles.value.components)
const colors = computed(() => styles.value.colors)
// A palette entry is a colour, not a message: shown in that colour.
const previewText = computed(() => {
  const tag = field.value.key.startsWith('palette.') ? field.value.key.slice('palette.'.length) : undefined
  return tag && tag in colors.value ? `<${tag}>${text.value.replace(/</g, '\\<')}</${tag}>` : text.value
})

const multiline = computed(() => text.value.includes('\n') || field.value.key.startsWith('screen.'))
const view = ref<'auto' | 'chat' | 'screen'>('auto')
const previewMode = computed(() => (view.value === 'auto' ? (multiline.value ? 'screen' : 'chat') : view.value))
const unknown = computed(() => unknownTags(text.value, [...messageTagsKnown(field.value.placeholders), ...STYLE_TAGS]))

const textarea = useTemplateRef<{ textareaRef?: HTMLTextAreaElement | null }>('textarea')

function insert(name: string) {
  const element = textarea.value?.textareaRef
  const tag = `<${name}>`
  if (!element) {
    text.value = text.value + tag
    return
  }
  const start = element.selectionStart ?? text.value.length
  const end = element.selectionEnd ?? start
  text.value = text.value.slice(0, start) + tag + text.value.slice(end)
  nextTick(() => {
    element.focus()
    element.setSelectionRange(start + tag.length, start + tag.length)
  })
}

const textareaId = useId()
</script>

<template>
  <div class="space-y-4">
    <div>
      <div class="flex flex-wrap items-center gap-2">
        <h2 class="font-mono text-base font-semibold break-all text-highlighted">
          {{ field.key }}
        </h2>
        <UBadge
          v-if="overriding"
          label="overridden here"
          color="primary"
          variant="subtle"
          size="sm"
        />
        <UBadge
          v-if="changed"
          :label="edit?.kind === 'reset' ? 'override removed' : 'changed'"
          color="warning"
          variant="subtle"
          size="sm"
        />
        <UBadge
          :label="locale"
          color="neutral"
          variant="outline"
          size="sm"
        />
      </div>
      <p
        v-if="field.description && field.description !== field.key"
        class="mt-1 text-sm text-muted"
      >
        {{ field.description }}
      </p>
      <p class="mt-1 text-xs text-muted">
        {{ overriding ? 'Would inherit from' : 'Inherits from' }}
        <button
          v-if="layerScope()"
          type="button"
          class="underline decoration-dotted"
          @click="emit('jump', layerScope()!)"
        >
          {{ inheritedFrom }}
        </button>
        <span v-else>{{ inheritedFrom }}</span>
        <template v-if="inherited.shadowed.length > 0">
          (also set by {{ inherited.shadowed.join(', ') }}, later in the category order)
        </template>
      </p>
      <USwitch
        :model-value="overriding"
        :label="overriding ? 'Override here' : 'Override here (off: inherits)'"
        size="sm"
        class="mt-2"
        @update:model-value="toggle"
      />
    </div>

    <div>
      <label
        :for="textareaId"
        class="mb-1 block text-sm font-medium"
      >Text (MiniMessage)</label>
      <UTextarea
        :id="textareaId"
        ref="textarea"
        v-model="text"
        :disabled="!overriding"
        :rows="multiline ? 6 : 2"
        autoresize
        :maxrows="16"
        :maxlength="MAX_MESSAGE"
        class="w-full font-mono"
        spellcheck="false"
        :color="issues.length > 0 ? 'error' : 'neutral'"
        :highlight="issues.length > 0"
      />
      <p
        v-for="issue in issues"
        :key="issue.message"
        class="mt-1 text-sm text-error"
        role="alert"
      >
        {{ issue.message }}
      </p>
      <div
        v-if="field.placeholders.length > 0 || prefixField"
        class="mt-2 flex flex-wrap items-center gap-1"
      >
        <span class="text-xs text-muted">Insert:</span>
        <UButton
          v-for="name in [...field.placeholders, ...(prefixField ? ['prefix', 'plugin'].filter(tag => tag !== field.key) : [])]"
          :key="name"
          :label="`<${name}>`"
          size="xs"
          color="neutral"
          variant="soft"
          class="font-mono"
          @click="insert(name)"
        />
      </div>
      <div class="mt-1 flex flex-wrap items-center gap-1">
        <span class="text-xs text-muted">Network counts (any message):</span>
        <UButton
          v-for="name in NETWORK_COUNT_TAGS"
          :key="name"
          :label="`<${name}>`"
          size="xs"
          color="neutral"
          variant="ghost"
          class="font-mono"
          @click="insert(name)"
        />
      </div>
      <div class="mt-2 flex flex-wrap gap-2">
        <UButton
          v-if="changed"
          size="xs"
          color="neutral"
          variant="ghost"
          icon="i-lucide-undo-2"
          label="Undo"
          @click="store.undoMessage(scope, locale, field.key)"
        />
      </div>
    </div>

    <div>
      <div class="mb-1 flex items-center justify-between gap-2">
        <p class="text-sm font-medium">
          Preview
        </p>
        <UFieldGroup size="xs">
          <UButton
            label="Chat"
            :variant="previewMode === 'chat' ? 'solid' : 'outline'"
            color="neutral"
            :aria-pressed="previewMode === 'chat'"
            @click="view = 'chat'"
          />
          <UButton
            label="Screen"
            :variant="previewMode === 'screen' ? 'solid' : 'outline'"
            color="neutral"
            :aria-pressed="previewMode === 'screen'"
            @click="view = 'screen'"
          />
        </UFieldGroup>
      </div>
      <EditorMiniMessagePreview
        :text="previewText"
        :placeholders="samples"
        :components="components"
        :colors="colors"
        :mode="previewMode"
      />
      <p
        v-if="unknown.length > 0"
        class="mt-1 text-xs text-muted"
      >
        Shown literally here (not previewed, or unknown): {{ unknown.map(name => `<${name}>`).join(', ') }}. The plugin decides how they render.
      </p>
    </div>

    <UCollapsible v-if="field.placeholders.length > 0">
      <UButton
        label="Sample values"
        color="neutral"
        variant="ghost"
        size="sm"
        trailing-icon="i-lucide-chevron-down"
      />
      <template #content>
        <div class="grid gap-2 pt-2 sm:grid-cols-2">
          <UFormField
            v-for="name in field.placeholders"
            :key="name"
            :label="`<${name}>`"
            :ui="{ label: 'font-mono text-xs' }"
          >
            <UInput
              v-model="samples[name]"
              size="sm"
              class="w-full"
            />
          </UFormField>
        </div>
      </template>
    </UCollapsible>

    <div class="grid gap-3 md:grid-cols-2">
      <div>
        <p class="mb-1 text-xs font-medium text-muted">
          {{ store.payload.value.meta.server || 'Uploading server' }}'s file text ({{ locale }})
        </p>
        <pre class="max-h-48 overflow-auto rounded-md bg-elevated p-2 font-mono text-xs whitespace-pre-wrap">{{ fileText(store.payload.value, locale, field.key) }}</pre>
      </div>
      <div>
        <p class="mb-1 text-xs font-medium text-muted">
          Shipped default (en_US)
        </p>
        <pre class="max-h-48 overflow-auto rounded-md bg-elevated p-2 font-mono text-xs whitespace-pre-wrap">{{ field.default }}</pre>
      </div>
    </div>
  </div>
</template>
