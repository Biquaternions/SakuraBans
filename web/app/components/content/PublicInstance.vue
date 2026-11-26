<script setup lang="ts">
// Names the public editor instance in the docs when the site is configured with one
// (NUXT_PUBLIC_PUBLIC_INSTANCE_URL); otherwise points to self-hosting.
const { public: { publicInstanceUrl } } = useRuntimeConfig()
const url = computed(() => String(publicInstanceUrl || '').replace(/\/+$/, ''))
</script>

<template>
  <p v-if="url">
    For example <code>{{ url }}</code>, a public instance run by the author, with no uptime
    guarantee, or <NuxtLink to="/docs/web-editor/self-hosting">one you host yourself</NuxtLink>.
  </p>
  <p v-else>
    Set <code>web.url</code> to the address of an editor site, for example
    <NuxtLink to="/docs/web-editor/self-hosting">one you host yourself</NuxtLink>.
  </p>
</template>
