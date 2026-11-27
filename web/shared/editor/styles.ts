// The tags every message can use besides its placeholders (S74): `<prefix>` and `<plugin>`,
// whose values are messages themselves, and the palette's colour tags, whose colours are the
// `palette.*` messages. Previews resolve them from the texts in effect.
import { resolveColor } from '../minimessage'

/** Tags whose value is the message of the same key, in MiniMessage. */
export const STYLE_COMPONENTS = ['prefix', 'plugin'] as const

/** The palette's tags; each one's colour is the message `palette.<tag>`. */
export const PALETTE_TAGS = ['main', 'accent', 'highlight', 'danger'] as const

/** Every tag a message may use without it being one of its placeholders. */
export const STYLE_TAGS: readonly string[] = [...STYLE_COMPONENTS, ...PALETTE_TAGS]

/** The shipped colours, for a palette entry that isn't a colour (as the plugin does). */
const BUILT_IN: Readonly<Record<string, string>> = {
  main: '#f591ff',
  accent: '#b891ff',
  highlight: '#ff91bf',
  danger: '#ff5555'
}

export interface MessageStyles {
  components: Record<string, string>
  colors: Record<string, string>
}

/**
 * The preview's style tags. `text` gives a key's text in effect (draft, override, file or
 * default); `own` is the key being edited, which can't insert itself.
 */
export function messageStyles(text: (key: string) => string | undefined, own?: string): MessageStyles {
  const components: Record<string, string> = {}
  for (const key of STYLE_COMPONENTS) {
    const value = text(key)
    if (key !== own && value !== undefined) {
      components[key] = value
    }
  }
  const colors: Record<string, string> = {}
  for (const tag of PALETTE_TAGS) {
    colors[tag] = resolveColor(text(`palette.${tag}`)?.trim() ?? '') ?? BUILT_IN[tag]!
  }
  return { components, colors }
}
