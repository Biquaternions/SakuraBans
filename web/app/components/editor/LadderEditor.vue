<script setup lang="ts">
// Ordered ladder steps (decision D36): each has a type (default: the template's) and a duration
// where the type has one. Drag and drop with the mouse, or the move buttons from the keyboard.
// Step actions and step messages belong to their step and move with it.
import { MAX_STEPS } from '#shared/editor/limits'
import { emptyStep, stepLabel, stepType, TEMPLATE_TYPES } from '#shared/editor/templates'
import type { TemplateType } from '#shared/protocol/schema'

const props = defineProps<{
  index: number
}>()

const store = useEditor()
const form = computed(() => store.draft.templates[props.index]!)

const dragging = ref<number | null>(null)
const over = ref<number | null>(null)
const open = ref<Set<number>>(new Set())

function issue(path: string): string | undefined {
  const prefix = `templates.${form.value.name || '(unnamed)'}.${path}`
  const found = store.fieldIssues.value.filter(item => item.ref.section === 'templates'
    && item.ref.name === form.value.name
    && (item.path === prefix || item.path.startsWith(`${prefix}[`) || item.path.startsWith(`${prefix}.`)))
  return found.length > 0 ? found.map(item => item.message).join('; ') : undefined
}

function move(from: number, to: number) {
  const steps = form.value.steps
  if (to < 0 || to >= steps.length || from === to) {
    return
  }
  const [step] = steps.splice(from, 1)
  steps.splice(to, 0, step!)
}

function onDrop(target: number) {
  if (dragging.value !== null) {
    move(dragging.value, target)
  }
  dragging.value = null
  over.value = null
}

function add() {
  const last = form.value.steps.at(-1)
  const step = emptyStep(last?.type ?? null, form.value.type)
  if (last && stepType(last, form.value.type) !== 'kick') {
    step.duration = last.duration
  }
  form.value.steps.push(step)
}

function remove(position: number) {
  form.value.steps.splice(position, 1)
}

function toggle(position: number) {
  const next = new Set(open.value)
  if (next.has(position)) {
    next.delete(position)
  } else {
    next.add(position)
  }
  open.value = next
}

/** The type select's value: TEMPLATE for "the template's type" (a select item can't be ""). */
const TEMPLATE = 'template'

function setType(position: number, value: string) {
  const step = form.value.steps[position]!
  const type = value === TEMPLATE || value === form.value.type ? null : value as TemplateType
  const before = stepType(step, form.value.type)
  step.type = type
  const after = stepType(step, form.value.type)
  if (after === 'kick') {
    step.duration = ''
  } else if (before === 'kick' && step.duration === '') {
    step.duration = '1d'
  }
}

const typeItems = computed(() => [
  { label: `${form.value.type} (template)`, value: TEMPLATE },
  ...TEMPLATE_TYPES.filter(type => type !== form.value.type).map(type => ({ label: type, value: type }))
])

function ordinal(n: number): string {
  const tens = n % 100
  if (tens >= 11 && tens <= 13) {
    return `${n}th`
  }
  return `${n}${['th', 'st', 'nd', 'rd'][n % 10] ?? 'th'}`
}

const CHIP_COLOR: Record<TemplateType, 'neutral' | 'warning' | 'info' | 'error'> = { kick: 'neutral', warn: 'info', mute: 'warning', ban: 'error' }
</script>

<template>
  <div class="space-y-2">
    <div
      class="flex flex-wrap items-center gap-1"
      aria-label="Escalation"
    >
      <template
        v-for="(step, position) in form.steps"
        :key="position"
      >
        <UBadge
          :label="stepLabel(step, form.type)"
          :color="CHIP_COLOR[stepType(step, form.type)]"
          variant="subtle"
          size="sm"
        />
        <UIcon
          v-if="position < form.steps.length - 1"
          name="i-lucide-arrow-right"
          class="size-3 text-dimmed"
        />
      </template>
      <span class="ms-1 text-xs text-muted">(then the last step repeats)</span>
    </div>

    <ol class="space-y-2">
      <li
        v-for="(step, position) in form.steps"
        :key="position"
        class="rounded-md border p-2 transition-colors"
        :class="[
          over === position && dragging !== position ? 'border-primary bg-primary/5' : 'border-default',
          dragging === position ? 'opacity-50' : ''
        ]"
        @dragover.prevent="over = position"
        @dragleave="over = over === position ? null : over"
        @drop.prevent="onDrop(position)"
      >
        <div class="flex flex-wrap items-center gap-2">
          <span
            class="cursor-grab text-dimmed"
            draggable="true"
            title="Drag to reorder"
            @dragstart="dragging = position"
            @dragend="dragging = null; over = null"
          >
            <UIcon
              name="i-lucide-grip-vertical"
              class="size-4"
            />
          </span>
          <span class="w-10 text-sm text-muted">{{ ordinal(position + 1) }}</span>
          <USelect
            :model-value="step.type ?? TEMPLATE"
            :items="typeItems"
            size="sm"
            class="w-40"
            :aria-label="`Type of step ${position + 1}`"
            @update:model-value="setType(position, String($event))"
          />
          <EditorDurationInput
            v-if="stepType(step, form.type) !== 'kick'"
            v-model="step.duration"
            :rules="{ permanent: true }"
            placeholder="7d or permanent"
            size="sm"
          />
          <span
            v-else
            class="text-xs text-muted"
          >a kick has no duration</span>
          <div class="ms-auto flex items-center gap-1">
            <UButton
              :icon="open.has(position) ? 'i-lucide-chevron-up' : 'i-lucide-settings-2'"
              :label="[step.message ? 'message' : '', step.actions.length > 0 ? `${step.actions.length} action${step.actions.length === 1 ? '' : 's'}` : ''].filter(Boolean).join(', ') || 'Message, actions'"
              color="neutral"
              variant="ghost"
              size="xs"
              :aria-expanded="open.has(position)"
              @click="toggle(position)"
            />
            <UButton
              icon="i-lucide-arrow-up"
              color="neutral"
              variant="ghost"
              size="xs"
              :disabled="position === 0"
              :aria-label="`Move step ${position + 1} up`"
              @click="move(position, position - 1)"
            />
            <UButton
              icon="i-lucide-arrow-down"
              color="neutral"
              variant="ghost"
              size="xs"
              :disabled="position === form.steps.length - 1"
              :aria-label="`Move step ${position + 1} down`"
              @click="move(position, position + 1)"
            />
            <UButton
              icon="i-lucide-x"
              color="neutral"
              variant="ghost"
              size="xs"
              :disabled="form.steps.length <= 1"
              :aria-label="`Remove step ${position + 1}`"
              @click="remove(position)"
            />
          </div>
        </div>
        <p
          v-if="issue(`ladder[${position}]`)"
          class="mt-1 ms-16 text-xs text-error"
        >
          {{ issue(`ladder[${position}]`) }}
        </p>
        <div
          v-if="open.has(position)"
          class="mt-3 space-y-3 ms-4 sm:ms-16"
        >
          <EditorTemplateMessage
            v-model="step.message"
            :type="stepType(step, form.type)"
            :label="`Message for step ${position + 1}`"
            :fallback="form.message ? 'Empty: the template\'s message is used.' : undefined"
            :error="issue(`step-messages.${position + 1}`)"
          />
          <div>
            <p class="mb-1 text-xs text-muted">
              Console commands for this step only.
            </p>
            <EditorStringListEditor v-model="step.actions" />
            <p
              v-if="issue(`step-actions.${position + 1}`)"
              class="mt-1 text-xs text-error"
            >
              {{ issue(`step-actions.${position + 1}`) }}
            </p>
          </div>
        </div>
      </li>
    </ol>
    <div class="flex flex-wrap items-center gap-3">
      <UButton
        v-if="form.steps.length < MAX_STEPS"
        icon="i-lucide-plus"
        label="Add step"
        color="neutral"
        variant="outline"
        size="xs"
        @click="add"
      />
      <span class="text-xs text-muted">Steps marked "(template)" follow the template's type and change with it.</span>
    </div>
  </div>
</template>
