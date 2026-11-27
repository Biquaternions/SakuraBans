<script setup lang="ts">
// A preset's or a ladder step's duration (decision D37): open, permanent or a fixed length, as
// far as the type allows. The length is kept while another mode is picked.
import { durationModes } from '#shared/editor/templates'
import type { DurationForm, DurationMode } from '#shared/editor/templates'
import type { PunishmentType } from '#shared/protocol/schema'

const props = withDefaults(defineProps<{
  type: PunishmentType | null
  /** A ladder step: ban and mute steps need a duration, and open means the type's default. */
  step?: boolean
  size?: 'xs' | 'sm' | 'md'
  label?: string
}>(), {
  step: false,
  size: 'sm',
  label: 'Duration'
})

const duration = defineModel<DurationForm>({ required: true })

const modes = computed(() => durationModes(props.type, props.step))

const OPEN_LABEL = computed(() => {
  if (!props.step) {
    return 'Command decides'
  }
  return props.type === 'warn' ? 'Default expiry' : 'None'
})

const labels = computed<Record<DurationMode, string>>(() => ({
  open: OPEN_LABEL.value,
  permanent: 'Permanent',
  fixed: 'Fixed'
}))

function pick(mode: DurationMode) {
  duration.value = { mode, length: mode === 'fixed' && duration.value.length.trim() === '' ? '1d' : duration.value.length }
}
</script>

<template>
  <div class="flex flex-wrap items-center gap-2">
    <UFieldGroup
      v-if="modes.length > 1"
      :size="size"
      role="group"
      :aria-label="label"
    >
      <UButton
        v-for="mode in modes"
        :key="mode"
        :label="labels[mode]"
        :variant="duration.mode === mode ? 'solid' : 'outline'"
        color="neutral"
        :aria-pressed="duration.mode === mode"
        @click="pick(mode)"
      />
    </UFieldGroup>
    <EditorDurationInput
      v-if="duration.mode === 'fixed' && modes.includes('fixed')"
      :model-value="duration.length"
      placeholder="7d"
      :size="size"
      @update:model-value="duration = { mode: 'fixed', length: $event }"
    />
  </div>
</template>
