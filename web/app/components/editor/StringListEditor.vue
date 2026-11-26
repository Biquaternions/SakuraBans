<script setup lang="ts">
// A list of one-line texts (console commands): each its own input, since commands may contain
// commas and spaces that a tag input would split.
import { actionProblem } from '#shared/editor/limits'

const props = withDefaults(defineProps<{
  modelValue: string[]
  placeholder?: string
  addLabel?: string
  label?: string
  max?: number
}>(), {
  placeholder: 'say {victim} was punished',
  addLabel: 'Add command',
  label: 'Command',
  max: 16
})

const emit = defineEmits<{
  'update:modelValue': [value: string[]]
}>()

function update(index: number, text: string) {
  const next = [...props.modelValue]
  next[index] = text
  emit('update:modelValue', next)
}

function remove(index: number) {
  emit('update:modelValue', props.modelValue.filter((_, other) => other !== index))
}

function add() {
  emit('update:modelValue', [...props.modelValue, ''])
}
</script>

<template>
  <div class="space-y-2">
    <div
      v-for="(item, index) in modelValue"
      :key="index"
      class="flex items-start gap-2"
    >
      <div class="min-w-0 flex-1">
        <UInput
          :model-value="item"
          :placeholder="placeholder"
          :aria-label="`${label} ${index + 1}`"
          :color="actionProblem(item) ? 'error' : 'neutral'"
          :highlight="actionProblem(item) !== null"
          class="w-full font-mono"
          size="sm"
          spellcheck="false"
          @update:model-value="update(index, String($event))"
        />
        <p
          v-if="actionProblem(item)"
          class="mt-0.5 text-xs text-error"
        >
          {{ label }} {{ actionProblem(item) }}
        </p>
      </div>
      <UButton
        icon="i-lucide-x"
        color="neutral"
        variant="ghost"
        size="sm"
        :aria-label="`Remove ${label.toLowerCase()} ${index + 1}`"
        @click="remove(index)"
      />
    </div>
    <UButton
      v-if="modelValue.length < max"
      icon="i-lucide-plus"
      :label="addLabel"
      color="neutral"
      variant="outline"
      size="xs"
      @click="add"
    />
  </div>
</template>
