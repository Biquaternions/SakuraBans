<script setup lang="ts">
// A template's or a step's message (decision D36): what the punished player sees instead of the
// network's message for that type, with that message's placeholders and a preview.
import { MAX_MESSAGE } from '#shared/editor/limits'
import { FALLBACK_MESSAGE_PLACEHOLDERS, NETWORK_MESSAGE_KEYS } from '#shared/editor/templates'
import type { TemplateType } from '#shared/protocol/schema'
import { sampleFor } from '~/utils/samples'

const props = defineProps<{
  type: TemplateType
  label: string
  /** What an empty field means, when not the network's message. */
  fallback?: string
  error?: string
}>()

const message = defineModel<string>({ required: true })
const store = useEditor()
const id = useId()

const networkKey = computed(() => NETWORK_MESSAGE_KEYS[props.type])
const networkField = computed(() => store.payload.value.messages?.fields.find(field => field.key === networkKey.value))
const placeholders = computed(() => networkField.value?.placeholders ?? [...FALLBACK_MESSAGE_PLACEHOLDERS[props.type]])
const samples = computed(() => Object.fromEntries(placeholders.value.map(name => [name, sampleFor(name)])))
const screen = computed(() => props.type === 'ban' || props.type === 'kick')

const textarea = useTemplateRef<{ textareaRef?: HTMLTextAreaElement | null }>('textarea')

function insert(name: string) {
  const element = textarea.value?.textareaRef
  const tag = `<${name}>`
  if (!element) {
    message.value += tag
    return
  }
  const start = element.selectionStart ?? message.value.length
  const end = element.selectionEnd ?? start
  message.value = message.value.slice(0, start) + tag + message.value.slice(end)
  nextTick(() => {
    element.focus()
    element.setSelectionRange(start + tag.length, start + tag.length)
  })
}
</script>

<template>
  <div class="space-y-2">
    <label
      :for="id"
      class="block text-sm font-medium"
    >{{ label }} <span class="font-normal text-muted">({{ screen ? `${type} screen` : `${type} notice in chat` }})</span></label>
    <UTextarea
      :id="id"
      ref="textarea"
      v-model="message"
      :rows="screen ? 3 : 1"
      autoresize
      :maxrows="10"
      :maxlength="MAX_MESSAGE"
      :placeholder="`Empty: the network's ${networkKey} message`"
      class="w-full font-mono"
      spellcheck="false"
      :color="error ? 'error' : 'neutral'"
    />
    <p
      v-if="error"
      class="text-xs text-error"
    >
      {{ error }}
    </p>
    <p class="text-xs text-muted">
      {{ fallback ?? `Empty: the network's message (${networkKey}) is used.` }} One text for every locale.
    </p>
    <div class="flex flex-wrap items-center gap-1">
      <span class="text-xs text-muted">Insert:</span>
      <UButton
        v-for="name in placeholders"
        :key="name"
        :label="`<${name}>`"
        size="xs"
        color="neutral"
        variant="soft"
        class="font-mono"
        @click="insert(name)"
      />
    </div>
    <EditorMiniMessagePreview
      v-if="message.trim() !== ''"
      :text="message"
      :placeholders="samples"
      :mode="screen ? 'screen' : 'chat'"
    />
  </div>
</template>
