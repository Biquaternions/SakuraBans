<script setup lang="ts">
const { links } = useAppConfig()

const footerLinks = computed(() => [
  { label: 'Download', to: links.download, icon: 'i-lucide-download' },
  { label: 'Source', to: links.repository, icon: 'i-lucide-git-branch' },
  { label: 'Issues', to: links.issues, icon: 'i-lucide-bug' },
  { label: 'Community', to: links.community, icon: 'i-lucide-messages-square' }
].filter(link => link.to))
</script>

<template>
  <UFooter>
    <template #left>
      SakuraBans · MIT License
    </template>

    <template #right>
      <UButton
        v-for="link in footerLinks"
        :key="link.label"
        :to="link.to"
        :icon="link.icon"
        :label="link.label"
        target="_blank"
        rel="noopener noreferrer"
        color="neutral"
        variant="ghost"
        size="sm"
      />
    </template>
  </UFooter>
</template>
