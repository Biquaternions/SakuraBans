<script setup lang="ts">
import { knownLocales, messageEditMatters, messageOverride } from '#shared/editor/draft'
import { inheritedMessage } from '#shared/editor/layers'
import { settingGroup } from '#shared/editor/settings'
import { GLOBAL, LOCALE_PATTERN } from '#shared/protocol/schema'
import type { MessageField } from '#shared/protocol/schema'

const store = useEditor()
const payload = computed(() => store.payload.value)
const fields = computed(() => payload.value.messages?.fields ?? [])

const locales = computed(() => knownLocales(payload.value, store.draft))
const locale = ref<string>(locales.value.includes('en_US') ? 'en_US' : locales.value[0] ?? 'en_US')
const query = ref('')
const selected = ref<string | null>(fields.value[0]?.key ?? null)

const isAdded = computed(() => store.draft.addedLocales.includes(locale.value))
const scope = ref<string>(GLOBAL)

function state(field: MessageField): 'changed' | 'override' | null {
  const edit = store.draft.messages[scope.value]?.[locale.value]?.[field.key]
  if (edit && messageEditMatters(payload.value, scope.value, locale.value, field.key, edit)) {
    return 'changed'
  }
  return messageOverride(payload.value, scope.value, locale.value, field.key) !== undefined ? 'override' : null
}

function textHere(field: MessageField): string {
  const edit = store.draft.messages[scope.value]?.[locale.value]?.[field.key]
  if (edit?.kind === 'set') {
    return edit.text
  }
  return messageOverride(payload.value, scope.value, locale.value, field.key)
    ?? inheritedMessage(payload.value, store.messageOverrides.value, store.overrides.value, scope.value, locale.value, field.key).text
    ?? ''
}

const groups = computed(() => {
  const needle = query.value.trim().toLowerCase()
  const result = new Map<string, MessageField[]>()
  for (const field of fields.value) {
    if (needle) {
      const haystack = `${field.key} ${field.description} ${textHere(field)}`.toLowerCase()
      if (!haystack.includes(needle)) {
        continue
      }
    }
    const group = settingGroup(field.key)
    if (!result.has(group)) {
      result.set(group, [])
    }
    result.get(group)!.push(field)
  }
  return [...result.entries()]
})

const current = computed(() => fields.value.find(field => field.key === selected.value) ?? null)

const addOpen = ref(false)
const newLocale = ref('')
const newLocaleError = computed(() => {
  const text = newLocale.value.trim()
  if (text === '') {
    return null
  }
  if (!LOCALE_PATTERN.test(text)) {
    return 'Use the form xx_YY, for example de_DE or pt_BR.'
  }
  if (locales.value.includes(text)) {
    return 'This locale exists already.'
  }
  return null
})

function addLocale() {
  const text = newLocale.value.trim()
  if (text === '' || newLocaleError.value) {
    return
  }
  store.addLocale(text)
  locale.value = text
  newLocale.value = ''
  addOpen.value = false
}

function removeLocale() {
  const removed = locale.value
  store.removeAddedLocale(removed)
  locale.value = locales.value.includes('en_US') ? 'en_US' : locales.value[0] ?? 'en_US'
}
</script>

<template>
  <div class="space-y-4">
    <EditorScopeSwitcher
      v-model="scope"
      section="messages"
      :locale="locale"
    />
    <p class="text-sm text-muted">
      <template v-if="scope === 'global'">
        Global texts apply on every server unless a category or server overrides them; without one, each server uses its own language file.
      </template>
      <template v-else>
        Texts set here apply to {{ scope.startsWith('server:') ? `the server ${scope.slice(7)}` : `every server in the category ${scope.slice(9)}` }}; the others inherit.
      </template>
    </p>
    <div class="grid gap-4 lg:grid-cols-[20rem_minmax(0,1fr)]">
      <div class="min-w-0 space-y-3">
        <div class="flex items-end gap-2">
          <UFormField
            label="Locale"
            class="flex-1"
          >
            <USelect
              v-model="locale"
              :items="locales"
              class="w-full"
            />
          </UFormField>
          <UPopover v-model:open="addOpen">
            <UButton
              icon="i-lucide-plus"
              label="Add locale"
              color="neutral"
              variant="outline"
            />
            <template #content>
              <form
                class="w-72 space-y-2 p-3"
                @submit.prevent="addLocale"
              >
                <UFormField
                  label="New locale"
                  :error="newLocaleError ?? undefined"
                  help="Only the texts you change are stored; the rest falls back to another locale."
                >
                  <UInput
                    v-model="newLocale"
                    placeholder="de_DE"
                    class="w-full font-mono"
                    autofocus
                  />
                </UFormField>
                <UButton
                  type="submit"
                  label="Add"
                  size="sm"
                  :disabled="newLocale.trim() === '' || newLocaleError !== null"
                />
              </form>
            </template>
          </UPopover>
        </div>
        <UAlert
          v-if="isAdded"
          color="info"
          variant="subtle"
          icon="i-lucide-languages"
          :title="`${locale} is new`"
          description="It is created with the first text you change. Keys you leave alone fall back as the plugin decides (another file of the same language, display.default-locale, then English)."
          :actions="[{ label: 'Remove this locale', color: 'neutral', variant: 'outline', onClick: removeLocale }]"
        />
        <UInput
          v-model="query"
          icon="i-lucide-search"
          placeholder="Search keys and texts"
          aria-label="Search messages"
          class="w-full"
        />
        <nav
          class="max-h-[60vh] overflow-y-auto rounded-lg border border-default lg:max-h-[calc(100vh-22rem)]"
          aria-label="Message keys"
        >
          <p
            v-if="groups.length === 0"
            class="p-4 text-sm text-muted"
          >
            No message matches.
          </p>
          <div
            v-for="[group, items] in groups"
            :key="group"
          >
            <p class="sticky top-0 bg-elevated px-3 py-1 font-mono text-xs font-semibold text-muted uppercase">
              {{ group }}
            </p>
            <button
              v-for="field in items"
              :key="field.key"
              type="button"
              class="flex w-full items-center gap-2 px-3 py-1.5 text-start font-mono text-sm hover:bg-elevated focus-visible:outline-2 focus-visible:outline-primary"
              :class="selected === field.key ? 'bg-primary/10 text-highlighted' : 'text-default'"
              :aria-current="selected === field.key ? 'true' : undefined"
              @click="selected = field.key"
            >
              <span class="min-w-0 flex-1 truncate">{{ field.key }}</span>
              <span
                v-if="state(field) === 'changed'"
                class="size-2 shrink-0 rounded-full bg-warning"
                title="Changed"
              />
              <span
                v-else-if="state(field) === 'override'"
                class="size-2 shrink-0 rounded-full bg-primary"
                title="Overridden in the database"
              />
            </button>
          </div>
        </nav>
      </div>

      <div class="min-w-0">
        <EditorMessageEditor
          v-if="current"
          :key="`${scope}:${locale}:${current.key}`"
          :field="current"
          :locale="locale"
          :scope="scope"
          @jump="scope = $event"
        />
        <p
          v-else
          class="text-muted"
        >
          Pick a message on the left.
        </p>
      </div>
    </div>
  </div>
</template>
