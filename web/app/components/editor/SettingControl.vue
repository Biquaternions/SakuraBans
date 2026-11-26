<script setup lang="ts">
// One input for a setting value, chosen by type and key. Secrets get a masked input that never
// shows a stored value (the payload only says whether one is set).
import { isDurationOrNever } from '#shared/editor/settings'
import type { JsonValue, SettingField } from '#shared/protocol/schema'

const props = defineProps<{
  field: SettingField
  modelValue: JsonValue
  id?: string
}>()
const emit = defineEmits<{
  'update:modelValue': [value: JsonValue]
}>()

const value = computed({
  get: () => props.modelValue,
  set: (next: JsonValue) => emit('update:modelValue', next)
})

const durationRules = computed(() => (props.field.type === 'DURATION' ? { zero: true } : { never: true }))
const stringIsDuration = computed(() => isDurationOrNever(props.field))
const isColor = computed(() => props.field.type === 'STRING' && props.field.key.endsWith('.color'))
const isEmbedFields = computed(() => props.field.type === 'MAP' && /^webhooks:routes\.[^.]+\.fields$/.test(props.field.key))
const isLongText = computed(() => props.field.type === 'STRING' && /\.description$/.test(props.field.key))

function onNumber(next: number | null | undefined) {
  if (typeof next === 'number' && Number.isFinite(next)) {
    value.value = next
  }
}

const reveal = ref(false)
const secretText = computed({
  get: () => (typeof props.modelValue === 'string' ? props.modelValue : ''),
  set: (next: string) => emit('update:modelValue', next)
})
</script>

<template>
  <div class="min-w-0">
    <template v-if="field.secret">
      <div class="flex items-center gap-2">
        <!-- A native input bound by property (v-model), so the value never becomes a DOM attribute. -->
        <input
          :id="id"
          v-model="secretText"
          :type="reveal ? 'text' : 'password'"
          placeholder="Type a new value (empty clears it)"
          autocomplete="off"
          spellcheck="false"
          class="w-full rounded-md bg-default px-2.5 py-1.5 font-mono text-sm text-highlighted ring ring-accented ring-inset placeholder:text-dimmed focus-visible:ring-2 focus-visible:ring-primary focus-visible:outline-none"
        >
        <UButton
          :icon="reveal ? 'i-lucide-eye-off' : 'i-lucide-eye'"
          color="neutral"
          variant="ghost"
          size="sm"
          :aria-label="reveal ? 'Hide what you typed' : 'Show what you typed'"
          @click="reveal = !reveal"
        />
      </div>
      <p class="mt-1 text-xs text-muted">
        Secret: never shown or stored by this site. Leave it empty to clear the value in this scope. Kept only in this tab: re-enter it after a reload.
      </p>
    </template>
    <USwitch
      v-else-if="field.type === 'BOOLEAN'"
      :id="id"
      :model-value="value === true"
      :label="value === true ? 'On' : 'Off'"
      @update:model-value="value = $event"
    />
    <UInputNumber
      v-else-if="field.type === 'INTEGER' || field.type === 'DECIMAL'"
      :id="id"
      :model-value="typeof value === 'number' ? value : undefined"
      :min="field.min ?? undefined"
      :max="field.max ?? undefined"
      :step="field.type === 'INTEGER' ? 1 : 0.1"
      :format-options="field.type === 'INTEGER' ? { maximumFractionDigits: 0, useGrouping: false } : { useGrouping: false }"
      class="w-48"
      @update:model-value="onNumber"
    />
    <USelect
      v-else-if="field.type === 'ENUM'"
      :id="id"
      :model-value="String(value)"
      :items="field.options"
      class="w-full sm:w-64"
      @update:model-value="value = $event as string"
    />
    <EditorDurationInput
      v-else-if="field.type === 'DURATION' || stringIsDuration"
      :id="id"
      :model-value="String(value)"
      :rules="durationRules"
      @update:model-value="value = $event"
    />
    <div
      v-else-if="isColor"
      class="flex items-center gap-2"
    >
      <UPopover>
        <UButton
          color="neutral"
          variant="outline"
          :aria-label="`Pick a colour (${value})`"
        >
          <span
            class="size-5 rounded border border-default"
            :style="{ backgroundColor: /^#[0-9a-fA-F]{6}$/.test(String(value)) ? String(value) : 'transparent' }"
          />
        </UButton>
        <template #content>
          <UColorPicker
            :model-value="/^#[0-9a-fA-F]{6}$/.test(String(value)) ? String(value) : '#000000'"
            class="p-2"
            @update:model-value="value = String($event).toLowerCase()"
          />
        </template>
      </UPopover>
      <UInput
        :id="id"
        :model-value="String(value)"
        class="w-32 font-mono"
        placeholder="#e74c3c"
        @update:model-value="value = String($event)"
      />
    </div>
    <UTextarea
      v-else-if="isLongText"
      :id="id"
      :model-value="String(value)"
      :rows="2"
      autoresize
      class="w-full"
      @update:model-value="value = String($event)"
    />
    <UInput
      v-else-if="field.type === 'STRING'"
      :id="id"
      :model-value="String(value)"
      class="w-full"
      @update:model-value="value = String($event)"
    />
    <UInputTags
      v-else-if="field.type === 'STRING_LIST'"
      :id="id"
      :model-value="Array.isArray(value) ? (value as string[]) : []"
      placeholder="Add and press Enter"
      add-on-blur
      class="w-full"
      @update:model-value="value = [...$event]"
    />
    <template v-else-if="field.type === 'MAP'">
      <EditorCategoriesEditor
        v-if="field.key === 'scopes.categories'"
        :model-value="value as Record<string, JsonValue>"
        @update:model-value="value = $event"
      />
      <EditorShortcutsEditor
        v-else-if="field.key === 'reasons.shortcuts'"
        :model-value="value as Record<string, JsonValue>"
        @update:model-value="value = $event"
      />
      <EditorWarningActionsEditor
        v-else-if="field.key === 'warnings.actions'"
        :model-value="value as Record<string, JsonValue>"
        @update:model-value="value = $event"
      />
      <EditorLimitsEditor
        v-else-if="field.key === 'limits:groups'"
        :model-value="value as Record<string, JsonValue>"
        @update:model-value="value = $event"
      />
      <EditorEmbedFieldsEditor
        v-else-if="isEmbedFields"
        :model-value="value as Record<string, JsonValue>"
        @update:model-value="value = $event"
      />
      <EditorGenericMapEditor
        v-else
        :model-value="value as Record<string, JsonValue>"
        @update:model-value="value = $event"
      />
    </template>
  </div>
</template>
