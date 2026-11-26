<script setup lang="ts">
import type { TemplateDraft } from '#shared/editor/draft'
import { formToDefinition, emptyTemplate, sameDefinition } from '#shared/editor/templates'
import { exportTemplatesYaml, importTemplatesYaml } from '#shared/editor/templatesYaml'

const store = useEditor()
const payload = computed(() => store.payload.value)
const toast = useToast()

const query = ref('')
const selectedIndex = ref(0)

const original = computed(() => new Map((payload.value.templates?.items ?? []).map(item => [item.name, item])))

function status(form: TemplateDraft): 'new' | 'renamed' | 'changed' | null {
  const before = original.value.get(form.name)
  if (!before) {
    return form.origin !== null && form.origin !== form.name ? 'renamed' : 'new'
  }
  return sameDefinition(before.definition, formToDefinition(form)) ? null : 'changed'
}

const list = computed(() => {
  const needle = query.value.trim().toLowerCase()
  return store.draft.templates
    .map((form, index) => ({ form, index }))
    .filter(({ form }) => !needle || form.name.includes(needle) || form.reason.toLowerCase().includes(needle))
    .sort((a, b) => a.form.name.localeCompare(b.form.name))
})

const deleted = computed(() => (payload.value.templates?.items ?? [])
  .filter(item => !store.draft.templates.some(form => form.name === item.name || form.origin === item.name)))

const selected = computed(() => store.draft.templates[selectedIndex.value] ?? null)

// Selection follows the template by name, so a fresh snapshot (which rebuilds the list) keeps it.
const selectedName = ref<string | null>(store.draft.templates[0]?.name ?? null)
watch(() => selected.value?.name, (name) => {
  if (name !== undefined) {
    selectedName.value = name
  }
})
watch(() => store.payload.value, () => {
  const index = store.draft.templates.findIndex(form => form.name === selectedName.value)
  if (index >= 0) {
    selectedIndex.value = index
  }
})

function hasIssues(form: TemplateDraft): boolean {
  return store.fieldIssues.value.some(issue => issue.ref.section === 'templates' && issue.ref.name === form.name)
}

function uniqueName(base: string): string {
  const names = new Set(store.draft.templates.map(form => form.name))
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

function create() {
  store.addTemplate({ ...emptyTemplate(uniqueName('new-template')), origin: null })
  selectedIndex.value = store.draft.templates.length - 1
}

function duplicate(form: TemplateDraft) {
  const copy = JSON.parse(JSON.stringify(form)) as TemplateDraft
  copy.name = uniqueName(`${form.name}-copy`)
  copy.origin = null
  store.addTemplate(copy)
  selectedIndex.value = store.draft.templates.length - 1
}

function remove(form: TemplateDraft) {
  store.removeTemplate(form)
  selectedIndex.value = Math.max(0, Math.min(selectedIndex.value, store.draft.templates.length - 1))
}

function restore(name: string) {
  store.revert({ section: 'templates', name })
  const index = store.draft.templates.findIndex(form => form.name === name)
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
    const index = store.draft.templates.findIndex(other => other.name === form.name)
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
    description: `${created} new, ${updated} replaced. Other templates are kept. Review before saving.`
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
  <div class="grid gap-4 lg:grid-cols-[18rem_minmax(0,1fr)]">
    <div class="min-w-0 space-y-3">
      <div class="flex flex-wrap gap-2">
        <UButton
          icon="i-lucide-plus"
          label="New"
          size="sm"
          @click="create"
        />
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
          No templates.
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
            :label="form.type"
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
            Deleted (until you save)
          </p>
          <div
            v-for="item in deleted"
            :key="item.name"
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
</template>
