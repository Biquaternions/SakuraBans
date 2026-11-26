export default defineAppConfig({
  ui: {
    colors: {
      primary: 'sakura',
      neutral: 'stone'
    },
    footer: {
      slots: {
        root: 'border-t border-default',
        left: 'text-sm text-muted'
      }
    }
  },

  // Links the owner fills in. Each is hidden while it is empty.
  links: {
    // Source repository, e.g. https://github.com/<owner>/SakuraBans
    repository: '',
    // Where to download the plugin (a releases page, Modrinth, Hangar, ...)
    download: '',
    // Issue tracker
    issues: '',
    // Community chat (Discord, Matrix, ...)
    community: ''
  },

  toc: {
    title: 'On this page'
  }
})
