<script setup lang="ts">
useSeoMeta({
  title: 'Editor',
  description: 'Edit a SakuraBans network\'s settings, messages and templates in the browser.'
})

const steps = [
  {
    icon: 'i-lucide-terminal',
    title: '1. Run /sb editor',
    description: 'In game or in the console, on any server with SakuraBans. The plugin encrypts a snapshot of the network-wide settings, messages and templates you may edit, uploads it here and replies with a link.'
  },
  {
    icon: 'i-lucide-pencil',
    title: '2. Edit in the browser',
    description: 'The link holds the key in its last part (after #), which your browser never sends to this site. The page decrypts the snapshot locally; you review every change before saving.'
  },
  {
    icon: 'i-lucide-check',
    title: '3. Save',
    description: 'Saving uploads your changes, encrypted with the same key. While the server that opened the editor is connected, it applies them by itself and the page shows what changed. Otherwise you get /sb applyedits <code> to run in game; nothing changes on the network until then.'
  }
]
</script>

<template>
  <UContainer class="py-10 sm:py-16">
    <div class="mx-auto max-w-3xl">
      <h1 class="text-3xl font-bold text-highlighted sm:text-4xl">
        Configuration editor
      </h1>
      <p class="mt-3 text-lg text-muted">
        Edit the settings, messages and templates your SakuraBans network shares through its
        database. You open the editor from the game; this page alone has nothing to edit.
      </p>

      <div class="mt-10 grid gap-4">
        <UPageCard
          v-for="step in steps"
          :key="step.title"
          :icon="step.icon"
          :title="step.title"
          :description="step.description"
          variant="subtle"
        />
      </div>

      <div class="mt-10 flex flex-wrap gap-3">
        <UButton
          to="/editor/demo"
          icon="i-lucide-play"
          label="Try the demo"
          size="lg"
        />
        <UButton
          to="/docs/web-editor"
          label="How the editor works"
          color="neutral"
          variant="outline"
          size="lg"
        />
      </div>
      <p class="mt-3 text-sm text-muted">
        The demo opens a sample network in your browser. Nothing is uploaded: saving shows the
        change set the plugin would receive.
      </p>
    </div>
  </UContainer>
</template>
