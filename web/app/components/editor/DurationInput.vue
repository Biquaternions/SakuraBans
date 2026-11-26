<script setup lang="ts">
import { checkDuration, describeDuration } from '#shared/duration'
import type { DurationRules } from '#shared/duration'

const props = withDefaults(defineProps<{
  modelValue: string
  rules?: DurationRules
  id?: string
  placeholder?: string
  disabled?: boolean
  size?: 'xs' | 'sm' | 'md'
}>(), {
  rules: () => ({}),
  id: undefined,
  placeholder: '30m, 7d, 1mo',
  disabled: false,
  size: 'md'
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const hintId = useId()
const check = computed(() => checkDuration(props.modelValue, props.rules))
const hint = computed(() => {
  const result = check.value
  if (!result.ok) {
    return result.message
  }
  switch (result.kind) {
    case 'duration':
      return describeDuration(result.millis)
    case 'zero':
      return 'off'
    case 'none':
      return 'none'
    default:
      return result.kind
  }
})
</script>

<template>
  <div class="flex flex-wrap items-center gap-x-3 gap-y-1">
    <UInput
      :id="id"
      :model-value="modelValue"
      :placeholder="placeholder"
      :disabled="disabled"
      :size="size"
      :color="check.ok ? 'neutral' : 'error'"
      :highlight="!check.ok"
      :aria-invalid="!check.ok"
      :aria-describedby="hintId"
      class="w-36 font-mono"
      spellcheck="false"
      autocomplete="off"
      @update:model-value="emit('update:modelValue', String($event))"
    />
    <span
      :id="hintId"
      class="text-xs"
      :class="check.ok ? 'text-muted' : 'text-error'"
    >{{ hint }}</span>
  </div>
</template>
