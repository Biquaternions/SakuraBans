<script setup lang="ts">
const props = defineProps<{
  names: readonly string[]
  /** "{name}" for console commands, "<name>" for MiniMessage. */
  style?: 'braces' | 'tags'
  label?: string
}>()

function format(name: string): string {
  return props.style === 'tags' ? `<${name}>` : `{${name}}`
}
</script>

<template>
  <p class="flex flex-wrap items-center gap-1 text-xs text-muted">
    <span>{{ label ?? 'Placeholders:' }}</span>
    <code
      v-for="name in names"
      :key="name"
      class="rounded bg-elevated px-1 py-0.5 text-default"
    >{{ format(name) }}</code>
  </p>
</template>
