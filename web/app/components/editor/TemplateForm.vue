<script setup lang="ts">
import { templatePath } from '#shared/editor/draft'
import { effectiveCategories, uploadingServer } from '#shared/editor/layers'
import { MAX_GROUPS, MAX_REASON, MAX_WEIGHT } from '#shared/editor/limits'
import {
  canShowDialog, changeKind, hasMuteStep, KIND_LABEL, kindOf, permissionNode, summarizeTemplate, TEMPLATE_ACTION_PLACEHOLDERS,
  TEMPLATE_KINDS, usageHint
} from '#shared/editor/templates'
import type { TemplateForm } from '#shared/editor/templates'
import { GLOBAL } from '#shared/protocol/schema'
import type { PunishmentType, TemplateKind } from '#shared/protocol/schema'

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
  issue.ref.section === 'templates' && issue.ref.scope === form.value.variant && issue.ref.name === form.value.name))

function issueFor(path: string): string | undefined {
  const prefix = `${templatePath(form.value.variant, form.value.name)}.`
  const found = issues.value.filter(issue => issue.path.slice(prefix.length) === path)
  return found.length > 0 ? found.map(issue => issue.message).join('; ') : undefined
}

function issuesUnder(path: string): string[] {
  const prefix = `${templatePath(form.value.variant, form.value.name)}.${path}`
  return issues.value.filter(issue => issue.path.startsWith(prefix)).map(issue => issue.message)
}

const renamed = computed(() => form.value.origin !== null && form.value.origin !== form.value.name)

// ---- Variant (S62) ----
const isVariant = computed(() => form.value.variant !== GLOBAL)
const global = computed(() => (isVariant.value
  ? store.draft.templates.find(other => other.variant === GLOBAL && other.name === form.value.name) ?? null
  : null))
/** A global template with variants keeps its name: they are found by it. */
const hasVariants = computed(() => !isVariant.value && store.draft.templates.some(other =>
  other.variant !== GLOBAL && other.name === (form.value.origin ?? form.value.name)))

function setName(value: string) {
  form.value.name = value.trim().toLowerCase()
}

// ---- Kind ----
// The plugin lets an update change the kind (it replaces the whole definition), so the editor
// does too. A ladder that becomes a preset loses its steps: the form before is kept for undo.
const beforeKindChange = ref<{ form: TemplateForm, kind: TemplateKind } | null>(null)

function setKind(kind: TemplateKind) {
  const current = form.value
  if (kindOf(current) === kind) {
    return
  }
  const lossy = current.kind === 'ladder' && kind !== 'ladder'
  beforeKindChange.value = lossy ? { form: JSON.parse(JSON.stringify(current)) as TemplateForm, kind: 'ladder' } : null
  store.draft.templates.splice(props.index, 1, { ...changeKind(current, kind), origin: current.origin, variant: current.variant })
}

function undoKindChange() {
  const saved = beforeKindChange.value
  if (saved) {
    store.draft.templates.splice(props.index, 1, { ...saved.form, origin: form.value.origin, name: form.value.name,
      variant: form.value.variant })
    beforeKindChange.value = null
  }
}

/** The commands whose -t takes this preset (S59: the temp commands only take open ones). */
const presetCommands = computed(() => {
  const current = form.value
  if (current.kind !== 'preset') {
    return ''
  }
  const type = current.type
  if (type !== 'ban' && type !== 'mute') {
    return `/${type}`
  }
  return current.duration.mode === 'open' ? `/${type}, /temp${type} and /ip${type}` : `/${type} and /ip${type}`
})

const kindItems = TEMPLATE_KINDS.map(kind => ({ label: KIND_LABEL[kind], value: kind }))

/** The type whose network message and placeholders the template's message is shown with. */
const messageType = computed<PunishmentType>(() => {
  const current = form.value
  if (current.kind === 'preset') {
    return current.type
  }
  return current.steps.find(step => step.type !== null)?.type ?? 'ban'
})

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

// Group mates are global definitions: a variant counts in its global template's groups.
const summary = computed(() => summarizeTemplate(isVariant.value && global.value ? { ...form.value, groups: global.value.groups } : form.value,
  store.draft.templates.filter(other => other.variant === GLOBAL)))
const ids = { name: useId(), reason: useId(), expiry: useId(), scope: useId() }
</script>

<template>
  <div class="space-y-5">
    <div class="flex flex-wrap items-start gap-3">
      <div class="min-w-0 flex-1">
        <h2 class="flex flex-wrap items-center gap-2 font-mono text-lg font-semibold break-all text-highlighted">
          {{ form.name || '(no name)' }}
          <UBadge
            :label="KIND_LABEL[kindOf(form)]"
            color="neutral"
            variant="outline"
            size="sm"
            class="font-sans"
          />
          <UBadge
            v-if="isVariant"
            :label="`variant for ${form.variant}`"
            color="primary"
            variant="subtle"
            size="sm"
            class="font-sans"
          />
        </h2>
        <p class="mt-1 text-sm text-muted">
          {{ summary || 'No steps yet.' }}
        </p>
        <p class="mt-1 text-xs text-muted">
          Use it with <code class="rounded bg-elevated px-1 py-0.5 text-default">{{ usageHint(form) }}</code>
          <span v-if="form.kind === 'preset'"> (only on {{ presetCommands }})</span>
          <span v-else> (needs sakurabans.punish, not the permission of the step's type)</span>.
          Default command names.
        </p>
      </div>
      <div class="flex gap-2">
        <UButton
          v-if="!isVariant"
          icon="i-lucide-copy"
          label="Duplicate"
          color="neutral"
          variant="outline"
          size="sm"
          @click="emit('duplicate')"
        />
        <UButton
          icon="i-lucide-trash-2"
          :label="isVariant ? 'Remove variant' : 'Delete'"
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
        :help="isVariant ? 'A variant has its global template\'s name, and shares its ladder progress.'
          : hasVariants && !renamed ? 'This template has variants, which are found by its name: delete them first to rename it.'
            : renamed ? `Renamed from ${form.origin}: saved as a delete of ${form.origin} and a create of the new name, so its uses start over.` : 'Lower case a-z, 0-9, _ and -. Unique across presets and ladders.'"
      >
        <UInput
          :id="ids.name"
          :model-value="form.name"
          :disabled="isVariant || (hasVariants && !renamed)"
          class="w-full font-mono"
          spellcheck="false"
          @update:model-value="setName(String($event))"
        />
      </UFormField>
      <UFormField
        label="Kind"
        :error="issueFor('type')"
      >
        <USelect
          :model-value="kindOf(form)"
          :items="kindItems"
          class="w-full"
          :disabled="isVariant"
          @update:model-value="setKind($event as TemplateKind)"
        />
        <template #help>
          <span v-if="isVariant">Fixed: a variant has its global template's kind.</span>
          <span v-else-if="hasVariants">A preset fills in one punishment of its type; a ladder escalates through its steps. Its variants keep their kind: remove them before saving a change of kind.</span>
          <span v-else>A preset fills in one punishment of its type; a ladder escalates through its steps.</span>
        </template>
      </UFormField>
    </div>

    <UAlert
      v-if="beforeKindChange && form.kind === 'preset'"
      color="warning"
      variant="subtle"
      icon="i-lucide-triangle-alert"
      title="The ladder's steps were dropped"
      description="A preset has no steps, expiry or address-shared. The duration was taken from the first step of this type, if there was one."
      :actions="[{ label: 'Undo', color: 'neutral', variant: 'outline', onClick: undoKindChange }]"
    />

    <UFormField
      label="Reason"
      :error="issueFor('reason')"
      :help="form.kind === 'ladder' ? 'Used when the command gives no reason and the step has none of its own; a reason given with -r replaces it.' : 'Used when the command gives no reason; a reason given with -r replaces it.'"
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

    <UFormField
      v-if="form.kind === 'preset'"
      label="Duration"
      :error="issueFor('duration')"
    >
      <EditorTemplateDurationInput
        v-if="form.type !== 'kick'"
        v-model="form.duration"
        :type="form.type"
      />
      <p
        v-else
        class="text-sm text-muted"
      >
        A kick has no duration.
      </p>
      <template #help>
        <span v-if="form.type === 'kick'" />
        <span v-else-if="form.duration.mode === 'open'">The command decides: its -d, else {{ form.type === 'warn' ? 'warnings.expire-after' : 'permanent' }}.<template v-if="form.type !== 'warn'"> Only a preset like this can be used with /temp{{ form.type }}.</template></span>
        <span v-else>Fixed by the template: a command that gives -d with it is refused.</span>
      </template>
    </UFormField>

    <div v-else>
      <div class="mb-1 flex items-center justify-between">
        <p class="text-sm font-medium">
          Steps
        </p>
        <p class="text-xs text-muted">
          The first use is step 1, the next step 2, …; after the last step it repeats.
        </p>
      </div>
      <p
        v-if="issueFor('steps')"
        class="mb-1 text-sm text-error"
      >
        {{ issueFor('steps') }}
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
        v-if="form.kind === 'ladder'"
        label="Expiry"
        :error="issueFor('expiry')"
        help="Earlier uses older than this don't count. Empty: all count."
      >
        <EditorDurationInput
          :id="ids.expiry"
          v-model="form.expiry"
          :rules="{ none: true }"
          placeholder="none"
        />
      </UFormField>
      <UFormField
        label="Applies to"
        :error="issueFor('scope')"
        help="Where its punishments apply (the template's scope key) when the command names no scope; a scope given with the command wins. Not where this definition is in force: that is chosen with &quot;Definitions for&quot; above."
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
        v-if="form.kind === 'ladder' || form.type === 'mute'"
        :model-value="form.shadow"
        :disabled="!hasMuteStep(form) && !form.shadow"
        label="Shadow mute"
        :description="form.kind === 'preset'
          ? 'The muted player\'s messages are shown to them only.'
          : hasMuteStep(form) ? 'On the mute steps, the muted player\'s messages are shown to them only.' : 'Needs a mute step.'"
        :color="issueFor('shadow') ? 'error' : undefined"
        @update:model-value="form.shadow = $event"
      />
      <USwitch
        v-if="form.kind === 'ladder'"
        v-model="form.addressShared"
        label="Count alts and addresses"
        description="Uses on the player's alts and addresses count towards the ladder."
      />
      <div>
        <USwitch
          v-model="form.permissionRequired"
          label="Needs a permission"
          description="Using this template, and revoking, editing, voiding or deleting its punishments, needs its own permission node."
        />
        <p
          v-if="form.permissionRequired"
          class="ms-12 mt-1 text-xs"
        >
          <code class="rounded bg-elevated px-1 py-0.5">{{ permissionNode(form.name || '<name>') }}</code>
        </p>
      </div>
      <USwitch
        v-model="form.silent"
        label="Silent"
        description="Its punishments are silent, as with -s: only staff who may see silent punishments are told. --hide and --quiet still hide them further."
      />
    </div>
    <p
      v-if="issueFor('shadow')"
      class="text-sm text-error"
    >
      Shadow mute: {{ issueFor('shadow') }}
    </p>

    <div>
      <EditorTemplateMessage
        v-model="form.message"
        :type="messageType"
        label="Message for the punished player"
        :error="issueFor('message')"
      />
      <p
        v-if="form.kind === 'ladder'"
        class="mt-1 text-xs text-muted"
      >
        Shown on every step with a punishment unless the step has its own message (open a step's details). The preview uses the {{ messageType }} message, the type of the first step with a punishment.
      </p>
    </div>

    <div v-if="form.kind === 'ladder' || form.type === 'mute' || form.type === 'warn' || form.display === 'dialog'">
      <USwitch
        :model-value="form.display === 'dialog'"
        :disabled="!canShowDialog(form) && form.display !== 'dialog'"
        label="Show as a dialog"
        :description="canShowDialog(form)
          ? `The ${form.kind === 'ladder' ? 'mute notice and warning of the mute and warn steps' : form.type === 'mute' ? 'mute notice' : 'warning'} opens as a dialog with a close button instead of a chat line. Where none can be shown (a client before 1.21.6, a Velocity proxy without packetevents) it is sent as chat. Refused chat and commands stay chat.`
          : form.kind === 'ladder' ? 'Needs a mute or warn step.' : form.type === 'mute' ? 'A shadow mute shows the player nothing.' : 'Only a mute or a warning is shown as a dialog.'"
        :color="issueFor('display') ? 'error' : undefined"
        @update:model-value="form.display = $event ? 'dialog' : 'chat'"
      />
      <p
        v-if="issueFor('display')"
        class="mt-1 text-sm text-error"
      >
        Dialog: {{ issueFor('display') }}
      </p>
    </div>

    <div>
      <p class="mb-1 text-sm font-medium">
        Actions every time
      </p>
      <p class="mb-2 text-xs text-muted">
        Console commands, run every time this template is used{{ form.kind === 'ladder' ? ', before the step\'s own' : '' }}.
      </p>
      <EditorStringListEditor v-model="form.actions" />
      <EditorPlaceholderList
        v-if="form.kind === 'preset'"
        class="mt-2"
        :names="TEMPLATE_ACTION_PLACEHOLDERS.filter(name => name !== 'step')"
        label="Actions may use:"
      />
      <p
        v-for="message in issuesUnder('actions')"
        :key="message"
        class="mt-1 text-sm text-error"
      >
        {{ message }}
      </p>
    </div>

    <div v-if="isVariant && form.groups.length === 0">
      <p class="mb-1 text-sm font-medium">
        Groups
      </p>
      <p class="text-xs text-muted">
        A variant counts in its global template's groups{{ global && global.groups.length > 0 ? `: ${global.groups.map(group => `${group.name} (weight ${group.weight})`).join(', ')}` : ' (none)' }}. Change them on the global definition.
      </p>
    </div>
    <div v-else>
      <p class="mb-1 text-sm font-medium">
        Groups
      </p>
      <p class="mb-2 text-xs text-muted">
        Templates in a group share progress: every use of a group mate, a preset's too, counts as its weight on the ladders of the group. A template in no group counts only towards itself.
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
    <p
      v-for="key in Object.keys(form.extra).filter(key => issueFor(key))"
      :key="key"
      class="text-sm text-error"
    >
      {{ key }}: {{ issueFor(key) }}
    </p>
  </div>
</template>
