<script setup lang="ts">
// Templates per definition scope: `global` holds every template's global definition; a
// server or category holds the variants in force there, whole definitions of the same kind.
import type { TemplateDraft } from '#shared/editor/draft'
import { templateKey } from '#shared/editor/draft'
import { scopeName } from '#shared/editor/layers'
import { emptyTemplate, formToDefinition, KIND_LABEL, kindOf, sameDefinition, TEMPLATE_KINDS } from '#shared/editor/templates'
import { GLOBAL } from '#shared/protocol/schema'
import type { TemplateKind } from '#shared/protocol/schema'
import { exportTemplatesYaml, importTemplatesYaml } from '#shared/editor/templatesYaml'

const store = useEditor()
const payload = computed(() => store.payload.value)
const toast = useToast()

const variant = ref<string>(GLOBAL)
const isGlobal = computed(() => variant.value === GLOBAL)
const query = ref('')
const selectedIndex = ref(-1)

const original = computed(() => new Map((payload.value.templates?.items ?? [])
  .map(item => [templateKey(item.scope, item.name), item])))

function status(form: TemplateDraft): 'new' | 'renamed' | 'changed' | null {
  const before = original.value.get(templateKey(form.variant, form.name))
  if (!before) {
    return form.origin !== null && form.origin !== form.name ? 'renamed' : 'new'
  }
  return sameDefinition(before.definition, formToDefinition(form)) ? null : 'changed'
}

const list = computed(() => {
  const needle = query.value.trim().toLowerCase()
  return store.draft.templates
    .map((form, index) => ({ form, index }))
    .filter(({ form }) => form.variant === variant.value)
    .filter(({ form }) => !needle || form.name.includes(needle) || form.reason.toLowerCase().includes(needle))
    .sort((a, b) => a.form.name.localeCompare(b.form.name))
})

const deleted = computed(() => (payload.value.templates?.items ?? [])
  .filter(item => item.scope === variant.value)
  .filter(item => !store.draft.templates.some(form => form.variant === item.scope
    && (form.name === item.name || (item.scope === GLOBAL && form.origin === item.name)))))

/** Global templates without a variant here, for "create variant from global". */
const variantSources = computed(() => isGlobal.value
  ? []
  : store.draft.templates.filter(form => form.variant === GLOBAL
    && !store.draft.templates.some(other => other.variant === variant.value && other.name === form.name))
      .sort((a, b) => a.name.localeCompare(b.name)))

const selected = computed(() => {
  const form = store.draft.templates[selectedIndex.value]
  return form && form.variant === variant.value ? form : null
})

// Selection follows the definition by scope and name, so a fresh snapshot (which rebuilds the
// list) or a scope switch keeps it.
const selectedKey = ref<string | null>(null)
watch(() => (selected.value ? templateKey(selected.value.variant, selected.value.name) : null), (key) => {
  if (key !== null) {
    selectedKey.value = key
  }
})
function reselect() {
  const index = store.draft.templates.findIndex(form => templateKey(form.variant, form.name) === selectedKey.value)
  selectedIndex.value = index >= 0 && store.draft.templates[index]!.variant === variant.value
    ? index
    : list.value[0]?.index ?? -1
}
watch(() => store.payload.value, reselect)
watch(variant, reselect)
onMounted(reselect)

function hasIssues(form: TemplateDraft): boolean {
  return store.fieldIssues.value.some(issue => issue.ref.section === 'templates'
    && issue.ref.scope === form.variant && issue.ref.name === form.name)
}

function uniqueName(base: string): string {
  const names = new Set(store.draft.templates.filter(form => form.variant === GLOBAL).map(form => form.name))
  if (!names.has(base)) {
    return base
  }
  for (let i = 2; ; i++) {
    const candidate = `${base}-${i}`.slice(0, 64)
    if (!names.has(candidate)) {
      return candidate
    }
  }
}

function create(kind: TemplateKind) {
  store.addTemplate({ ...emptyTemplate(uniqueName(kind === 'ladder' ? 'new-ladder' : `new-${kind}`), kind), origin: null, variant: GLOBAL })
  selectedIndex.value = store.draft.templates.length - 1
}

// A template's kind is picked when it is created (it can still be changed in the form).
const createItems = TEMPLATE_KINDS.map(kind => ({
  label: KIND_LABEL[kind],
  description: kind === 'ladder' ? 'Escalates step by step; used with /punish' : `One ${kind}; used with /${kind} -t`,
  onSelect: () => create(kind)
}))

/** A variant starts as a copy of the global definition, without groups (they stay global's). */
function createVariant(source: TemplateDraft) {
  const copy = JSON.parse(JSON.stringify(source)) as TemplateDraft
  copy.groups = []
  copy.origin = null
  copy.variant = variant.value
  store.addTemplate(copy)
  selectedIndex.value = store.draft.templates.length - 1
}

const variantItems = computed(() => variantSources.value.map(form => ({
  label: form.name,
  description: KIND_LABEL[kindOf(form)],
  onSelect: () => createVariant(form)
})))

function duplicate(form: TemplateDraft) {
  const copy = JSON.parse(JSON.stringify(form)) as TemplateDraft
  copy.name = uniqueName(`${form.name}-copy`)
  copy.origin = null
  store.addTemplate(copy)
  selectedIndex.value = store.draft.templates.length - 1
}

// Deleting a global template deletes its variants too (the plugin does the same); the
// confirmation lists them.
const confirmDelete = ref<{ form: TemplateDraft, variants: TemplateDraft[] } | null>(null)
const confirmOpen = computed({
  get: () => confirmDelete.value !== null,
  set: (open: boolean) => {
    if (!open) {
      confirmDelete.value = null
    }
  }
})

function variantsOf(form: TemplateDraft): TemplateDraft[] {
  return store.draft.templates.filter(other => other.variant !== GLOBAL && other.name === form.name)
}

function remove(form: TemplateDraft) {
  const variants = form.variant === GLOBAL ? variantsOf(form) : []
  if (variants.length > 0) {
    confirmDelete.value = { form, variants }
    return
  }
  store.removeTemplate(form)
  reselect()
}

function confirmRemove() {
  const pending = confirmDelete.value
  if (pending) {
    pending.variants.forEach(other => store.removeTemplate(other))
    store.removeTemplate(pending.form)
  }
  confirmDelete.value = null
  reselect()
}

function restore(name: string) {
  store.revert({ section: 'templates', scope: variant.value, name })
  const index = store.draft.templates.findIndex(form => form.variant === variant.value && form.name === name)
  if (index >= 0) {
    selectedIndex.value = index
  }
}

// ---- templates.yml ----
const fileInput = useTemplateRef<HTMLInputElement>('file')

async function importFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) {
    return
  }
  if (file.size > 1024 * 1024) {
    toast.add({ title: 'That file is too large for a templates.yml.', color: 'error' })
    return
  }
  const result = importTemplatesYaml(await file.text())
  let created = 0
  let updated = 0
  for (const form of result.templates) {
    const index = store.draft.templates.findIndex(other => other.variant === form.variant && other.name === form.name)
    if (index >= 0) {
      store.draft.templates.splice(index, 1, { ...form, origin: store.draft.templates[index]!.origin })
      updated++
    } else {
      store.addTemplate({ ...form, origin: null })
      created++
    }
  }
  toast.add({
    title: `Imported ${created + updated} template${created + updated === 1 ? '' : 's'}`,
    description: `${created} new, ${updated} replaced (variants included). Other templates are kept. Review before saving.`
      + (result.errors.length > 0 ? ` Skipped: ${result.errors.join('; ')}` : ''),
    color: result.errors.length > 0 ? 'warning' : 'success',
    duration: result.errors.length > 0 ? 0 : 6000
  })
}

function exportFile() {
  const yaml = exportTemplatesYaml(store.draft.templates)
  const url = URL.createObjectURL(new Blob([yaml], { type: 'application/yaml' }))
  const link = document.createElement('a')
  link.href = url
  link.download = 'templates.yml'
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
</script>

<template>
  <div class="space-y-4">
    <div class="space-y-1">
      <EditorScopeSwitcher
        v-model="variant"
        section="templates"
        label="Definitions for"
      />
      <p class="text-xs text-muted">
        <template v-if="isGlobal">
          Global definitions apply everywhere a server or category has no variant of its own.
        </template>
        <template v-else>
          Variants for {{ scopeName(variant) }}: whole definitions of a global template, of the same kind, in force for uses issued
          {{ variant.startsWith('server:') ? 'on that server' : 'on the servers of that category' }} (or with that scope named). A server's own variant wins over its categories'.
        </template>
      </p>
    </div>
    <div class="grid gap-4 lg:grid-cols-[18rem_minmax(0,1fr)]">
      <div class="min-w-0 space-y-3">
        <div class="flex flex-wrap gap-2">
          <UDropdownMenu
            v-if="!isGlobal"
            :items="variantItems"
            :content="{ align: 'start' }"
          >
            <UButton
              icon="i-lucide-copy-plus"
              label="Create variant from global"
              trailing-icon="i-lucide-chevron-down"
              size="sm"
              :disabled="variantItems.length === 0"
            />
          </UDropdownMenu>
          <UDropdownMenu
            v-if="isGlobal"
            :items="createItems"
            :content="{ align: 'start' }"
          >
            <UButton
              icon="i-lucide-plus"
              label="New"
              trailing-icon="i-lucide-chevron-down"
              size="sm"
            />
          </UDropdownMenu>
          <UButton
            icon="i-lucide-upload"
            label="Import YAML"
            color="neutral"
            variant="outline"
            size="sm"
            @click="fileInput?.click()"
          />
          <UButton
            icon="i-lucide-download"
            label="Export YAML"
            color="neutral"
            variant="outline"
            size="sm"
            @click="exportFile"
          />
          <input
            ref="file"
            type="file"
            accept=".yml,.yaml,text/yaml,application/yaml"
            class="hidden"
            aria-label="templates.yml to import"
            @change="importFile"
          >
        </div>
        <UInput
          v-model="query"
          icon="i-lucide-search"
          placeholder="Search templates"
          aria-label="Search templates"
          class="w-full"
        />
        <nav
          class="max-h-[60vh] overflow-y-auto rounded-lg border border-default lg:max-h-[calc(100vh-22rem)]"
          aria-label="Templates"
        >
          <p
            v-if="list.length === 0"
            class="p-4 text-sm text-muted"
          >
            {{ isGlobal ? 'No templates.' : 'No variants here: the global definitions apply.' }}
          </p>
          <button
            v-for="{ form, index } in list"
            :key="index"
            type="button"
            class="flex w-full items-center gap-2 px-3 py-2 text-start hover:bg-elevated focus-visible:outline-2 focus-visible:outline-primary"
            :class="selectedIndex === index ? 'bg-primary/10' : ''"
            :aria-current="selectedIndex === index ? 'true' : undefined"
            @click="selectedIndex = index"
          >
            <span class="min-w-0 flex-1">
              <span class="block truncate font-mono text-sm text-highlighted">{{ form.name || '(no name)' }}</span>
              <span class="block truncate text-xs text-muted">{{ form.reason || 'No reason' }}</span>
            </span>
            <UBadge
              :label="kindOf(form)"
              color="neutral"
              variant="outline"
              size="sm"
            />
            <UIcon
              v-if="hasIssues(form)"
              name="i-lucide-circle-alert"
              class="size-4 text-error"
              aria-label="Has errors"
            />
            <UBadge
              v-else-if="status(form)"
              :label="status(form)!"
              color="warning"
              variant="subtle"
              size="sm"
            />
          </button>
          <div
            v-if="deleted.length > 0"
            class="border-t border-default p-2"
          >
            <p class="px-1 pb-1 text-xs text-muted">
              {{ isGlobal ? 'Deleted' : 'Removed' }} (until you save)
            </p>
            <div
              v-for="item in deleted"
              :key="`${item.scope} ${item.name}`"
              class="flex items-center gap-2 px-1 py-1"
            >
              <span class="flex-1 truncate font-mono text-sm text-muted line-through">{{ item.name }}</span>
              <UButton
                label="Restore"
                size="xs"
                color="neutral"
                variant="ghost"
                icon="i-lucide-undo-2"
                @click="restore(item.name)"
              />
            </div>
          </div>
        </nav>
      </div>

      <div class="min-w-0">
        <EditorTemplateForm
          v-if="selected"
          :key="selectedIndex"
          :index="selectedIndex"
          @duplicate="duplicate(selected)"
          @remove="remove(selected)"
        />
        <p
          v-else
          class="text-muted"
        >
          No template selected.
        </p>
      </div>
    </div>
    <UModal
      v-model:open="confirmOpen"
      title="Delete the template and its variants?"
      :description="confirmDelete ? `Deleting ${confirmDelete.form.name} also removes its variants (a variant can't exist without its global template):` : undefined"
    >
      <template #body>
        <ul
          v-if="confirmDelete"
          class="list-inside list-disc space-y-1 text-sm"
        >
          <li
            v-for="other in confirmDelete.variants"
            :key="other.variant"
            class="font-mono"
          >
            {{ other.variant }}
          </li>
        </ul>
      </template>
      <template #footer>
        <div class="flex w-full justify-end gap-2">
          <UButton
            label="Cancel"
            color="neutral"
            variant="outline"
            @click="confirmDelete = null"
          />
          <UButton
            label="Delete all"
            color="error"
            icon="i-lucide-trash-2"
            @click="confirmRemove"
          />
        </div>
      </template>
    </UModal>
  </div>
</template>
