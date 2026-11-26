<script setup lang="ts">
import { effectiveCategories, uploadingServer } from '#shared/editor/layers'
import { MAX_GROUPS, MAX_REASON, MAX_WEIGHT } from '#shared/editor/limits'
import {
  hasMuteStep, permissionNode, stepType, summarizeTemplate, TEMPLATE_ACTION_PLACEHOLDERS, TEMPLATE_TYPES
} from '#shared/editor/templates'
import type { TemplateType } from '#shared/protocol/schema'

const props = defineProps<{
  index: number
}>()
const emit = defineEmits<{
  duplicate: []
  remove: []
}>()

const store = useEditor()
const payload = computed(() => store.payload.value)
const form = computed(() => store.draft.templates[props.index]!)

const issues = computed(() => store.fieldIssues.value.filter(issue =>
  issue.ref.section === 'templates' && issue.ref.name === form.value.name))

function issueFor(path: string): string | undefined {
  const prefix = `templates.${form.value.name || '(unnamed)'}.`
  const found = issues.value.filter(issue => issue.path.slice(prefix.length) === path)
  return found.length > 0 ? found.map(issue => issue.message).join('; ') : undefined
}

function issuesUnder(path: string): string[] {
  const prefix = `templates.${form.value.name || '(unnamed)'}.${path}`
  return issues.value.filter(issue => issue.path.startsWith(prefix)).map(issue => issue.message)
}

const renamed = computed(() => form.value.origin !== null && form.value.origin !== form.value.name)

function setName(value: string) {
  form.value.name = value.trim().toLowerCase()
}

/** Steps marked "(template)" take the new type; kicks lose their duration. */
function setType(type: TemplateType) {
  const before = form.value.type
  form.value.type = type
  form.value.steps.forEach((step) => {
    if (step.type === type) {
      step.type = null
    }
    if (step.type === null) {
      if (type === 'kick') {
        step.duration = ''
      } else if (before === 'kick' && step.duration === '') {
        step.duration = '1d'
      }
    }
  })
  if (!hasMuteStep(form.value)) {
    form.value.shadow = false
  }
}

// ---- Scope ----
type ScopeMode = 'default' | 'global' | 'server' | 'category'
const scopeMode = computed<ScopeMode>({
  get: () => {
    const scope = form.value.scope.trim()
    if (scope === '') {
      return 'default'
    }
    if (scope === 'global') {
      return 'global'
    }
    return scope.startsWith('category:') ? 'category' : 'server'
  },
  set: (mode) => {
    form.value.scope = mode === 'default' ? '' : mode === 'global' ? 'global' : `${mode}:${scopeName.value}`
  }
})
const scopeName = computed({
  get: () => {
    const scope = form.value.scope.trim()
    const colon = scope.indexOf(':')
    return colon < 0 ? '' : scope.slice(colon + 1)
  },
  set: (name: string) => {
    form.value.scope = `${scopeMode.value}:${name.trim().toLowerCase()}`
  }
})
const categoryNames = computed(() => {
  const names = new Set(payload.value.lookups.categories)
  Object.keys(effectiveCategories(store.overrides.value, uploadingServer(payload.value))).forEach(name => names.add(name))
  return [...names].sort()
})
const scopeItems = computed(() => (scopeMode.value === 'server' ? payload.value.lookups.servers : categoryNames.value))
const scopeOptions = [
  { label: 'scopes.default of the server', value: 'default' },
  { label: 'Global', value: 'global' },
  { label: 'One server', value: 'server' },
  { label: 'A category', value: 'category' }
]

// ---- Groups ----
const groupSuggestions = computed(() => {
  const names = new Set<string>()
  store.draft.templates.forEach(other => other.groups.forEach(group => names.add(group.name)))
  return [...names].sort()
})

function addGroup() {
  form.value.groups.push({ name: '', weight: 1 })
}

const summary = computed(() => summarizeTemplate(form.value, store.draft.templates))
const ids = { name: useId(), reason: useId(), expiry: useId(), scope: useId() }
</script>

<template>
  <div class="space-y-5">
    <div class="flex flex-wrap items-start gap-3">
      <div class="min-w-0 flex-1">
        <h2 class="font-mono text-lg font-semibold break-all text-highlighted">
          {{ form.name || '(no name)' }}
        </h2>
        <p class="mt-1 text-sm text-muted">
          {{ summary || 'No steps yet.' }}
        </p>
      </div>
      <div class="flex gap-2">
        <UButton
          icon="i-lucide-copy"
          label="Duplicate"
          color="neutral"
          variant="outline"
          size="sm"
          @click="emit('duplicate')"
        />
        <UButton
          icon="i-lucide-trash-2"
          label="Delete"
          color="error"
          variant="outline"
          size="sm"
          @click="emit('remove')"
        />
      </div>
    </div>

    <div class="grid gap-4 sm:grid-cols-2">
      <UFormField
        label="Name"
        :name="ids.name"
        :error="issueFor('name')"
        :help="renamed ? `Renamed from ${form.origin}: saved as a delete of ${form.origin} and a create of the new name, so its ladder starts over.` : 'Lower case a-z, 0-9, _ and -.'"
      >
        <UInput
          :id="ids.name"
          :model-value="form.name"
          class="w-full font-mono"
          spellcheck="false"
          @update:model-value="setName(String($event))"
        />
      </UFormField>
      <UFormField label="Type">
        <USelect
          :model-value="form.type"
          :items="TEMPLATE_TYPES.map(type => ({ label: type, value: type }))"
          class="w-full"
          @update:model-value="setType($event as TemplateType)"
        />
        <template #help>
          The type of every step that doesn't name its own: changing it re-labels those steps.
        </template>
      </UFormField>
    </div>

    <UFormField
      label="Reason"
      :error="issueFor('reason')"
      help="Used when the command gives no reason; a reason given with -r replaces it."
    >
      <UTextarea
        :id="ids.reason"
        v-model="form.reason"
        :rows="1"
        autoresize
        :maxlength="MAX_REASON"
        class="w-full"
      />
    </UFormField>

    <div>
      <div class="mb-1 flex items-center justify-between">
        <p class="text-sm font-medium">
          Ladder
        </p>
        <p class="text-xs text-muted">
          The first punishment uses step 1, the next step 2, …; after the last step it repeats.
        </p>
      </div>
      <p
        v-if="issueFor('ladder')"
        class="mb-1 text-sm text-error"
      >
        {{ issueFor('ladder') }}
      </p>
      <EditorLadderEditor :index="index" />
      <EditorPlaceholderList
        class="mt-2"
        :names="TEMPLATE_ACTION_PLACEHOLDERS"
        label="Step and template actions may use:"
      />
    </div>

    <div class="grid gap-4 sm:grid-cols-2">
      <UFormField
        label="Ladder expiry"
        :error="issueFor('ladder-expiry')"
        help="Earlier punishments older than this don't count. Empty: all count."
      >
        <EditorDurationInput
          :id="ids.expiry"
          v-model="form.ladderExpiry"
          :rules="{ none: true }"
          placeholder="none"
        />
      </UFormField>
      <UFormField
        label="Scope"
        :error="issueFor('scope')"
        help="Where it applies when the command names no scope; a scope given with the command wins."
      >
        <div class="flex flex-wrap gap-2">
          <USelect
            :id="ids.scope"
            :model-value="scopeMode"
            :items="scopeOptions"
            class="w-56"
            @update:model-value="scopeMode = $event as ScopeMode"
          />
          <UInputMenu
            v-if="scopeMode === 'server' || scopeMode === 'category'"
            :model-value="scopeName"
            :items="scopeItems"
            create-item
            :placeholder="scopeMode === 'server' ? 'server name' : 'category name'"
            :aria-label="scopeMode === 'server' ? 'Server name' : 'Category name'"
            class="w-48 font-mono"
            @update:model-value="scopeName = String($event ?? '')"
            @create="scopeName = $event"
          />
        </div>
      </UFormField>
    </div>

    <div class="grid gap-3 sm:grid-cols-2">
      <USwitch
        v-model="form.ip"
        label="Punish the address"
        description="The player's current address instead of the player, like /ipban and /ipmute."
      />
      <USwitch
        :model-value="form.shadow"
        :disabled="!hasMuteStep(form) && !form.shadow"
        label="Shadow mute"
        :description="hasMuteStep(form) ? 'On the mute steps, the muted player\'s messages are shown to them only.' : 'Needs a mute step in the ladder.'"
        @update:model-value="form.shadow = $event"
      />
      <USwitch
        v-model="form.addressShared"
        label="Count alts and addresses"
        description="Punishments of the player's alts and addresses count towards the ladder."
      />
      <div>
        <USwitch
          v-model="form.permissionRequired"
          label="Needs a permission"
          description="Using this template needs its own permission node."
        />
        <p
          v-if="form.permissionRequired"
          class="ms-12 mt-1 text-xs"
        >
          <code class="rounded bg-elevated px-1 py-0.5">{{ permissionNode(form.name || '<name>') }}</code>
        </p>
      </div>
    </div>

    <div>
      <EditorTemplateMessage
        v-model="form.message"
        :type="form.steps.length === 1 ? stepType(form.steps[0]!, form.type) : form.type"
        label="Message for the punished player"
        :error="issueFor('message')"
      />
      <p class="mt-1 text-xs text-muted">
        Shown on every step unless the step has its own message (open a step's "Message, actions"). The preview uses the template's type.
      </p>
    </div>

    <div>
      <p class="mb-1 text-sm font-medium">
        Actions after every punishment
      </p>
      <p class="mb-2 text-xs text-muted">
        Console commands, run after each punishment from this template.
      </p>
      <EditorStringListEditor v-model="form.actions" />
      <p
        v-for="message in issuesUnder('actions')"
        :key="message"
        class="mt-1 text-sm text-error"
      >
        {{ message }}
      </p>
    </div>

    <div>
      <p class="mb-1 text-sm font-medium">
        Groups
      </p>
      <p class="mb-2 text-xs text-muted">
        Templates in a group share progress: a punishment from a group mate counts as its weight in that group. A template in no group counts only towards itself.
      </p>
      <div class="space-y-2">
        <div
          v-for="(group, groupIndex) in form.groups"
          :key="groupIndex"
          class="flex flex-wrap items-center gap-2"
        >
          <UInputMenu
            :model-value="group.name"
            :items="groupSuggestions"
            create-item
            placeholder="group"
            :aria-label="`Group ${groupIndex + 1}`"
            class="w-48 font-mono"
            @update:model-value="group.name = String($event ?? '').trim().toLowerCase()"
            @create="group.name = $event.trim().toLowerCase()"
          />
          <span class="text-sm text-muted">weight</span>
          <UInputNumber
            v-model="group.weight"
            :min="1"
            :max="MAX_WEIGHT"
            :step="1"
            :aria-label="`Weight in ${group.name || 'the group'}`"
            class="w-32"
          />
          <UButton
            icon="i-lucide-x"
            color="neutral"
            variant="ghost"
            size="sm"
            :aria-label="`Remove group ${group.name}`"
            @click="form.groups.splice(groupIndex, 1)"
          />
        </div>
        <UButton
          v-if="form.groups.length < MAX_GROUPS"
          icon="i-lucide-plus"
          label="Add group"
          color="neutral"
          variant="outline"
          size="xs"
          @click="addGroup"
        />
        <p
          v-for="message in issuesUnder('groups')"
          :key="message"
          class="text-sm text-error"
        >
          {{ message }}
        </p>
      </div>
    </div>

    <UAlert
      v-if="Object.keys(form.extra).length > 0"
      color="neutral"
      variant="subtle"
      icon="i-lucide-info"
      title="Kept as they are"
      :description="`This template has keys this site doesn't edit: ${Object.keys(form.extra).join(', ')}. They are sent back unchanged.`"
    />
  </div>
</template>
