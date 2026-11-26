<script setup lang="ts">
import type { NuxtError } from '#app'

const props = defineProps<{
  error: NuxtError
}>()

useHead({
  htmlAttrs: {
    lang: 'en'
  }
})

useSeoMeta({
  title: props.error.statusCode === 404 ? 'Page not found' : 'Error'
})

const { data: navigation } = await useAsyncData('navigation', async () =>
  docsNavigation(await queryCollectionNavigation('docs')))
provide('navigation', navigation)
</script>

<template>
  <UApp>
    <AppHeader />

    <UError :error="error" />

    <AppFooter />
  </UApp>
</template>
