<script setup lang="ts">
useSeoMeta({
  title: '',
  description: 'SakuraBans is a punishment plugin for Paper and Velocity: bans, mutes, warnings and kicks with scopes, templates, alt detection and network sync.'
})

const { links } = useAppConfig()

const features = [
  {
    icon: 'i-lucide-gavel',
    title: 'Punishments with scopes',
    description: 'Ban, mute, warn and kick players, addresses or address ranges. Each punishment applies everywhere, on one server, or on a category of servers.',
    to: '/docs/guides/scopes'
  },
  {
    icon: 'i-lucide-list-ordered',
    title: 'Templates with ladders',
    description: 'Named rules such as "hacking" escalate step by step (7 days, 30 days, permanent). Templates in a group share progress; each step can run console commands.',
    to: '/docs/guides/templates'
  },
  {
    icon: 'i-lucide-users',
    title: 'Alt detection',
    description: 'Names and addresses are recorded on join. Address strictness (lenient, normal, stern, strict) decides how far an address ban reaches, and staff hear about joining alts.',
    to: '/docs/guides/alts'
  },
  {
    icon: 'i-lucide-network',
    title: 'Network sync',
    description: 'Backends that share one MariaDB, MySQL or PostgreSQL database share their punishments; SQL sync tells the others within seconds.',
    to: '/docs/guides/networks'
  },
  {
    icon: 'i-lucide-gauge',
    title: 'Staff limits',
    description: 'Groups of staff get maximum durations, cooldowns between punishments, or must use templates.',
    to: '/docs/configuration/limits'
  },
  {
    icon: 'i-lucide-shield',
    title: 'Exemption',
    description: 'Protect players by permission, or by LuckPerms group weight so staff cannot punish someone above them.',
    to: '/docs/configuration/config#exemption'
  },
  {
    icon: 'i-lucide-webhook',
    title: 'Discord webhooks',
    description: 'One route per event (ban, mute, unban, edit, rollback, ...) with its own embed. Only the server where it happens posts.',
    to: '/docs/configuration/webhooks'
  },
  {
    icon: 'i-lucide-import',
    title: 'Importers',
    description: 'Bring punishments over from LiteBans, LibertyBans, AdvancedBan, BanManager, the vanilla ban lists, or another SakuraBans database.',
    to: '/docs/guides/importing'
  },
  {
    icon: 'i-lucide-sliders-horizontal',
    title: 'Web editor',
    description: 'Edit network-wide settings, messages and templates in the browser. The snapshot is encrypted end to end; nothing changes until you run the command it gives you.',
    to: '/docs/web-editor'
  }
]
</script>

<template>
  <div>
    <UPageHero
      title="Punishments for Paper and Velocity networks"
      description="SakuraBans is a punishment plugin for Minecraft servers: bans, mutes, warnings and kicks, with scopes, templates, alt detection and network sync. It is for administrators of single servers and of networks."
      :links="[
        { label: 'Get started', to: '/docs/getting-started/installation', trailingIcon: 'i-lucide-arrow-right', size: 'xl' },
        { label: 'Open the editor', to: '/editor', color: 'neutral', variant: 'subtle', icon: 'i-lucide-sliders-horizontal', size: 'xl' },
        ...(links.download ? [{ label: 'Download', to: links.download, target: '_blank', color: 'neutral' as const, variant: 'outline' as const, icon: 'i-lucide-download', size: 'xl' as const }] : [])
      ]"
    >
      <template #headline>
        <UBadge
          color="primary"
          variant="subtle"
          label="Paper 26.3+ · Velocity 4.3+"
        />
      </template>
    </UPageHero>

    <UPageSection
      title="What it does"
      description="Every feature below is in the plugin today and documented from its configuration files."
    >
      <UPageGrid>
        <UPageCard
          v-for="feature in features"
          :key="feature.title"
          :icon="feature.icon"
          :title="feature.title"
          :description="feature.description"
          :to="feature.to"
          variant="subtle"
        />
      </UPageGrid>
    </UPageSection>

    <UPageSection
      title="Where it runs"
      description="Install SakuraBans on the proxy or on the backends: never on both."
    >
      <div class="grid gap-4 md:grid-cols-2">
        <UPageCard
          icon="i-lucide-server"
          title="On every Paper backend"
          description="Paper 26.3 or newer. Backends share one MariaDB, MySQL or PostgreSQL database so they share punishments; each one enforces bans and mutes itself, including scopes for its own server."
          to="/docs/getting-started/installation#on-the-backends"
          variant="outline"
        />
        <UPageCard
          icon="i-lucide-route"
          title="On the Velocity proxy"
          description="Velocity 4.3 or newer. One instance for the whole network. Cancelling signed chat for mutes needs the SignedVelocity plugin on the proxy and the backends."
          to="/docs/getting-started/installation#on-the-proxy"
          variant="outline"
        />
      </div>
    </UPageSection>

    <UPageSection>
      <UPageCTA
        title="Read the documentation"
        description="Installing, every configuration file, every command and permission, and how the web editor keeps your configuration private."
        variant="subtle"
        :links="[
          { label: 'Documentation', to: '/docs', trailingIcon: 'i-lucide-arrow-right' },
          { label: 'How the editor works', to: '/docs/web-editor', color: 'neutral', variant: 'outline' }
        ]"
      />
    </UPageSection>
  </div>
</template>
