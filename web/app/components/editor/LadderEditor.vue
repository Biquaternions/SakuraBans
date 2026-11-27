<script setup lang="ts">
// A ladder's ordered steps: each names its own type, or "No punishment" (actions
// only), with a duration where the type has one and an optional reason, message and actions.
// Drag and drop with the mouse, or the move buttons from the keyboard; a step's reason, message
// and actions belong to it and move with it.
import { MAX_REASON, MAX_STEPS } from '#shared/editor/limits'
import { emptyStep, setStepType, stepLabel } from '#shared/editor/templates'
import type { LadderForm, TemplateStepForm } from '#shared/editor/templates'
import type { PunishmentType } from '#shared/protocol/schema'

const props = defineProps<{
  index: number
}>()

const store = useEditor()
const form = computed(() => store.draft.templates[props.index] as LadderForm)

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

/** Issues of the step's type and duration, and of keys this site doesn't edit. */
function stepIssue(position: number): string | undefined {
  const prefix = `templates.${form.value.name || '(unnamed)'}.steps[${position}]`
  const details = ['reason', 'message', 'actions'].map(key => `${prefix}.${key}`)
  const found = store.fieldIssues.value.filter(item => item.ref.section === 'templates'
    && item.ref.name === form.value.name
    && (item.path === prefix || item.path.startsWith(`${prefix}.`))
    && !details.some(detail => item.path === detail || item.path.startsWith(`${detail}[`)))
  return found.length > 0 ? found.map(item => item.message).join('; ') : undefined
}

/** Issues of the step's details (reason, message, actions): they open the step. */
function detailIssue(position: number): boolean {
  return ['reason', 'message', 'actions'].some(key => issue(`steps[${position}].${key}`) !== undefined)
}

function move(from: number, to: number) {
  const steps = form.value.steps
  if (to < 0 || to >= steps.length || from === to) {
    return
  }
  // The open details follow their step.
  const flags = steps.map((_, position) => open.value.has(position))
  const [step] = steps.splice(from, 1)
  steps.splice(to, 0, step!)
  const [flag] = flags.splice(from, 1)
  flags.splice(to, 0, flag!)
  open.value = new Set(flags.flatMap((value, position) => (value ? [position] : [])))
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
  const step = emptyStep(last?.type === null ? 'ban' : last?.type ?? 'ban')
  if (last && last.type !== null) {
    step.duration = { ...last.duration }
  }
  form.value.steps.push(step)
}

function remove(position: number) {
  form.value.steps.splice(position, 1)
  open.value = new Set([...open.value].filter(item => item !== position).map(item => (item > position ? item - 1 : item)))
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

/** The type select's value for "no punishment" (a select item can't be null). */
const NONE = 'none'

function setType(position: number, value: string) {
  const step = form.value.steps[position]!
  setStepType(step, value === NONE ? null : value as PunishmentType)
  if (step.type === null) {
    // A step without a punishment needs an action: show where it goes.
    open.value = new Set([...open.value, position])
  }
  if (!form.value.steps.some(other => other.type === 'mute')) {
    form.value.shadow = false
  }
}

const typeItems = [
  { label: 'Ban', value: 'ban' },
  { label: 'Mute', value: 'mute' },
  { label: 'Warn', value: 'warn' },
  { label: 'Kick', value: 'kick' },
  { label: 'No punishment', value: NONE }
]

function ordinal(n: number): string {
  const tens = n % 100
  if (tens >= 11 && tens <= 13) {
    return `${n}th`
  }
  return `${n}${['th', 'st', 'nd', 'rd'][n % 10] ?? 'th'}`
}

function detailsLabel(step: TemplateStepForm): string {
  const parts = [
    step.reason.trim() ? 'reason' : '',
    step.message.trim() ? 'message' : '',
    step.actions.length > 0 ? `${step.actions.length} action${step.actions.length === 1 ? '' : 's'}` : ''
  ].filter(Boolean)
  if (parts.length > 0) {
    return parts.join(', ')
  }
  return step.type === null ? 'Actions' : 'Reason, message, actions'
}

const CHIP_COLOR: Record<PunishmentType, 'neutral' | 'warning' | 'info' | 'error'> = { kick: 'neutral', warn: 'info', mute: 'warning', ban: 'error' }
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
          :label="stepLabel(step)"
          :color="step.type === null ? 'neutral' : CHIP_COLOR[step.type]"
          :variant="step.type === null ? 'outline' : 'subtle'"
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
            :model-value="step.type ?? NONE"
            :items="typeItems"
            size="sm"
            class="w-40"
            :aria-label="`Type of step ${position + 1}`"
            @update:model-value="setType(position, String($event))"
          />
          <EditorTemplateDurationInput
            v-if="step.type !== null && step.type !== 'kick'"
            v-model="step.duration"
            :type="step.type"
            step
            :label="`Duration of step ${position + 1}`"
          />
          <span
            v-else
            class="text-xs text-muted"
          >{{ step.type === null ? 'only the actions run' : 'a kick has no duration' }}</span>
          <div class="ms-auto flex items-center gap-1">
            <UButton
              :icon="open.has(position) ? 'i-lucide-chevron-up' : 'i-lucide-settings-2'"
              :label="detailsLabel(step)"
              :color="detailIssue(position) ? 'error' : 'neutral'"
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
          v-if="stepIssue(position)"
          class="mt-1 ms-16 text-xs text-error"
        >
          {{ stepIssue(position) }}
        </p>
        <div
          v-if="open.has(position) || detailIssue(position)"
          class="mt-3 space-y-3 ms-4 sm:ms-16"
        >
          <UFormField
            :label="`Reason for step ${position + 1}`"
            :error="issue(`steps[${position}].reason`)"
            :help="form.reason.trim() ? `Empty: the template's reason (${form.reason.trim()}). A reason given with -r wins over both.` : 'Empty: the template\'s reason. A reason given with -r wins over both.'"
          >
            <UInput
              v-model="step.reason"
              :maxlength="MAX_REASON"
              placeholder="the template's reason"
              class="w-full"
            />
          </UFormField>
          <EditorTemplateMessage
            v-if="step.type !== null"
            v-model="step.message"
            :type="step.type"
            :label="`Message for step ${position + 1}`"
            :fallback="form.message ? 'Empty: the template\'s message is used.' : undefined"
            :error="issue(`steps[${position}].message`)"
          />
          <p
            v-else-if="issue(`steps[${position}].message`)"
            class="text-xs text-error"
          >
            {{ issue(`steps[${position}].message`) }}
          </p>
          <div>
            <p class="mb-1 text-xs text-muted">
              {{ step.type === null
                ? 'Console commands for this step. A step without a punishment needs at least one; {id} and {duration} are empty here.'
                : 'Console commands for this step only, run after the template\'s.' }}
            </p>
            <EditorStringListEditor v-model="step.actions" />
            <p
              v-if="issue(`steps[${position}].actions`)"
              class="mt-1 text-xs text-error"
            >
              {{ issue(`steps[${position}].actions`) }}
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
      <span class="text-xs text-muted">The first use is step 1, the next step 2, and so on.</span>
    </div>
  </div>
</template>
