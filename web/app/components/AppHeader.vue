<script setup lang="ts">
import type { ContentNavigationItem } from '@nuxt/content'
import type { NavigationMenuItem } from '@nuxt/ui'

const navigation = inject<Ref<ContentNavigationItem[] | null | undefined>>('navigation')
const { links } = useAppConfig()
const route = useRoute()

const items = computed<NavigationMenuItem[]>(() => [
  { label: 'Docs', to: '/docs', active: route.path.startsWith('/docs') },
  { label: 'Editor', to: '/editor', active: route.path.startsWith('/editor') }
])
</script>

<template>
  <UHeader
    :ui="{ center: 'flex-1 justify-center' }"
    to="/"
  >
    <template #title>
      <AppLogo />
    </template>

    <UNavigationMenu
      :items="items"
      variant="link"
    />

    <template #right>
      <UContentSearchButton
        :collapsed="true"
        aria-label="Search the docs"
      />

      <UColorModeButton />

      <UButton
        v-if="links.repository"
        :to="links.repository"
        target="_blank"
        rel="noopener noreferrer"
        icon="i-lucide-git-branch"
        color="neutral"
        variant="ghost"
        aria-label="Source repository"
      />
    </template>

    <template #body>
      <UNavigationMenu
        :items="items"
        orientation="vertical"
        class="-mx-2.5 mb-4"
      />
      <UContentNavigation
        v-if="navigation"
        highlight
        :navigation="navigation"
      />
    </template>
  </UHeader>
</template>
