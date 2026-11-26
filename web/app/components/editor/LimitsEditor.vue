<script setup lang="ts">
// limits:groups (limits.yml): staff groups in order; the first whose permission a staff member
// has applies to them. Each group: permission, max duration per type, cooldown per type,
// require-template.
import { checkDuration } from '#shared/duration'
import { IDENTIFIER_HINT, isIdentifier } from '#shared/editor/limits'
import { LIMIT_COOLDOWN_TYPES, LIMIT_DURATION_TYPES } from '#shared/editor/settings'
import type { JsonValue } from '#shared/protocol/schema'

const props = defineProps<{
  modelValue: Record<string, JsonValue>
}>()
const emit = defineEmits<{
  'update:modelValue': [value: Record<string, JsonValue>]
}>()

interface Group {
  permission: string
  maxDuration: Record<string, string>
  cooldown: Record<string, string>
  requireTemplate: boolean
  extra: Record<string, JsonValue>
}

function text(value: JsonValue | undefined): string {
  return typeof value === 'string' ? value : ''
}

function toGroup(value: JsonValue): Group {
  const node = (typeof value === 'object' && value !== null && !Array.isArray(value) ? value : {}) as Record<string, JsonValue>
  const section = (key: string) => (typeof node[key] === 'object' && node[key] !== null && !Array.isArray(node[key]) ? node[key] : {}) as Record<string, JsonValue>
  const extra = Object.fromEntries(Object.entries(node).filter(([key]) => !['permission', 'max-duration', 'cooldown', 'require-template'].includes(key)))
  return {
    permission: text(node.permission),
    maxDuration: Object.fromEntries(LIMIT_DURATION_TYPES.map(type => [type, text(section('max-duration')[type])])),
    cooldown: Object.fromEntries(LIMIT_COOLDOWN_TYPES.map(type => [type, text(section('cooldown')[type])])),
    requireTemplate: node['require-template'] === true,
    extra
  }
}

function fromGroup(group: Group): JsonValue {
  const out: Record<string, JsonValue> = { permission: group.permission }
  const durations = Object.fromEntries(Object.entries(group.maxDuration).filter(([, value]) => value.trim() !== ''))
  if (Object.keys(durations).length > 0) {
    out['max-duration'] = durations
  }
  const cooldowns = Object.fromEntries(Object.entries(group.cooldown).filter(([, value]) => value.trim() !== ''))
  if (Object.keys(cooldowns).length > 0) {
    out.cooldown = cooldowns
  }
  if (group.requireTemplate) {
    out['require-template'] = true
  }
  return { ...out, ...group.extra }
}

const { rows, commit, add, remove, duplicates } = useMapRows<Group>(
  () => props.modelValue,
  value => emit('update:modelValue', value),
  toGroup,
  fromGroup
)

function move(index: number, to: number) {
  if (to < 0 || to >= rows.value.length) {
    return
  }
  const list = [...rows.value]
  const [row] = list.splice(index, 1)
  list.splice(to, 0, row!)
  rows.value = list
  commit()
}

function durationOk(value: string, permanent: boolean): boolean {
  return value.trim() === '' || checkDuration(value, { permanent }).ok
}
</script>

<template>
  <div class="space-y-3">
    <p class="text-xs text-muted">
      In order: the first group whose permission a staff member has applies to them. Staff in no group have no limits; the console never has. <code>sakurabans.limits.unlimited</code> ignores durations and required templates, <code>sakurabans.cooldown.bypass</code> ignores cooldowns.
    </p>
    <div
      v-for="(row, index) in rows"
      :key="row.id"
      class="space-y-2 rounded-md border border-default p-3"
    >
      <div class="flex flex-wrap items-center gap-2">
        <span class="w-6 text-sm text-muted">{{ index + 1 }}.</span>
        <UInput
          :model-value="row.key"
          placeholder="group"
          aria-label="Group name"
          class="w-40 font-mono"
          size="sm"
          :color="isIdentifier(row.key) && !duplicates.has(row.key) ? 'neutral' : 'error'"
          @update:model-value="row.key = String($event).toLowerCase(); commit()"
        />
        <div class="ms-auto flex gap-1">
          <UButton
            icon="i-lucide-arrow-up"
            color="neutral"
            variant="ghost"
            size="xs"
            :disabled="index === 0"
            :aria-label="`Move ${row.key} up`"
            @click="move(index, index - 1)"
          />
          <UButton
            icon="i-lucide-arrow-down"
            color="neutral"
            variant="ghost"
            size="xs"
            :disabled="index === rows.length - 1"
            :aria-label="`Move ${row.key} down`"
            @click="move(index, index + 1)"
          />
          <UButton
            icon="i-lucide-trash-2"
            color="neutral"
            variant="ghost"
            size="xs"
            :aria-label="`Remove ${row.key}`"
            @click="remove(row)"
          />
        </div>
      </div>
      <p
        v-if="!isIdentifier(row.key) || duplicates.has(row.key)"
        class="text-xs text-error"
      >
        {{ duplicates.has(row.key) ? 'This group is listed twice.' : `Names are ${IDENTIFIER_HINT}.` }}
      </p>
      <UFormField
        label="Permission"
        size="sm"
        :error="row.value.permission.trim() === '' ? 'A group needs a permission' : undefined"
      >
        <UInput
          :model-value="row.value.permission"
          placeholder="sakurabans.group.helper"
          class="w-full font-mono"
          size="sm"
          @update:model-value="row.value.permission = String($event); commit()"
        />
      </UFormField>
      <div class="grid gap-2 sm:grid-cols-2">
        <fieldset>
          <legend class="mb-1 text-xs font-medium">
            Longest punishment (empty or permanent: no limit)
          </legend>
          <div
            v-for="type in LIMIT_DURATION_TYPES"
            :key="type"
            class="mb-1 flex items-center gap-2"
          >
            <span class="w-12 text-xs text-muted">{{ type }}</span>
            <UInput
              :model-value="row.value.maxDuration[type]"
              placeholder="none"
              size="xs"
              class="w-28 font-mono"
              :aria-label="`Longest ${type}`"
              :color="durationOk(row.value.maxDuration[type] ?? '', true) ? 'neutral' : 'error'"
              @update:model-value="row.value.maxDuration[type] = String($event); commit()"
            />
          </div>
        </fieldset>
        <fieldset>
          <legend class="mb-1 text-xs font-medium">
            Cooldown between two punishments
          </legend>
          <div
            v-for="type in LIMIT_COOLDOWN_TYPES"
            :key="type"
            class="mb-1 flex items-center gap-2"
          >
            <span class="w-12 text-xs text-muted">{{ type }}</span>
            <UInput
              :model-value="row.value.cooldown[type]"
              placeholder="none"
              size="xs"
              class="w-28 font-mono"
              :aria-label="`Cooldown for ${type}`"
              :color="durationOk(row.value.cooldown[type] ?? '', false) ? 'neutral' : 'error'"
              @update:model-value="row.value.cooldown[type] = String($event); commit()"
            />
          </div>
        </fieldset>
      </div>
      <USwitch
        :model-value="row.value.requireTemplate"
        label="Punishments need a template"
        size="sm"
        @update:model-value="row.value.requireTemplate = $event; commit()"
      />
    </div>
    <UButton
      icon="i-lucide-plus"
      label="Add group"
      color="neutral"
      variant="outline"
      size="xs"
      @click="add(`group-${rows.length + 1}`, toGroup({ permission: '' }))"
    />
  </div>
</template>
