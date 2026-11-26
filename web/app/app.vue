<script setup lang="ts">
const route = useRoute()

const { data: navigation } = await useAsyncData('navigation', async () =>
  docsNavigation(await queryCollectionNavigation('docs')))
const { data: files } = useLazyAsyncData('search', () => queryCollectionSearchSections('docs'), {
  server: false
})

useHead({
  meta: [
    { name: 'viewport', content: 'width=device-width, initial-scale=1' }
  ],
  link: [
    { rel: 'icon', href: '/favicon.svg', type: 'image/svg+xml' }
  ],
  htmlAttrs: {
    lang: 'en'
  }
})

useSeoMeta({
  titleTemplate: title => (title ? `${title} - SakuraBans` : 'SakuraBans')
})

provide('navigation', navigation)

const isEditor = computed(() => route.path.startsWith('/editor/'))
</script>

<template>
  <UApp>
    <NuxtLoadingIndicator color="var(--ui-primary)" />

    <AppHeader />

    <UMain>
      <NuxtLayout>
        <NuxtPage />
      </NuxtLayout>
    </UMain>

    <AppFooter v-if="!isEditor" />

    <ClientOnly>
      <LazyUContentSearch
        :files="files"
        :navigation="navigation"
      />
    </ClientOnly>
  </UApp>
</template>
