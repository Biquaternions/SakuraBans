import type { ContentNavigationItem } from '@nuxt/content'

/** The docs live under /docs: show the children of that node, not the node itself. */
export function docsNavigation(items: ContentNavigationItem[]): ContentNavigationItem[] {
  const root = items.find(item => item.path === '/docs')
  return root?.children ?? items
}
