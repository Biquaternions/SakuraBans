// Renders MiniMessage as DOM through VNodes: text is only ever a text node, colours come from
// the parser's validated hex values. No innerHTML anywhere.
import type { PropType, VNode } from 'vue'
import { defineComponent, h } from 'vue'
import { renderMiniMessage } from '#shared/minimessage'
import type { MmRun } from '#shared/minimessage'

function runNode(run: MmRun, index: number): VNode {
  const style: Record<string, string> = {}
  const { color, bold, italic, underlined, strikethrough } = run.style
  if (color) {
    style.color = color
  }
  if (bold) {
    style.fontWeight = '700'
  }
  if (italic) {
    style.fontStyle = 'italic'
  }
  const lines = [underlined ? 'underline' : '', strikethrough ? 'line-through' : ''].filter(Boolean)
  if (lines.length > 0) {
    style.textDecoration = lines.join(' ')
  }
  const titles = [run.style.hover ? `Hover: ${run.style.hover}` : '', run.style.click ? `Click: ${run.style.click}` : '']
    .filter(Boolean)
  return h('span', {
    key: index,
    style,
    class: [run.style.obfuscated ? 'mc-obfuscated' : '', titles.length > 0 ? 'cursor-help underline decoration-dotted decoration-1' : ''],
    title: titles.length > 0 ? titles.join('\n') : undefined
  }, run.text)
}

export default defineComponent({
  name: 'MiniMessagePreview',
  props: {
    text: { type: String, required: true },
    placeholders: { type: Object as PropType<Record<string, string>>, default: () => ({}) },
    components: { type: Object as PropType<Record<string, string>>, default: () => ({}) },
    /** The palette's colour tags (`main` → `#f591ff`). */
    colors: { type: Object as PropType<Record<string, string>>, default: () => ({}) },
    /** "chat": left-aligned lines; "screen": centred, like a kick or ban screen. */
    mode: { type: String as PropType<'chat' | 'screen'>, default: 'chat' }
  },
  setup(props) {
    return () => {
      const lines = renderMiniMessage(props.text, { placeholders: props.placeholders, components: props.components, colors: props.colors })
      return h('div', {
        'class': [
          props.mode === 'screen' ? 'mc-screen text-center px-6 py-8' : 'mc-surface px-3 py-2',
          'rounded-md text-sm leading-relaxed whitespace-pre-wrap break-words'
        ],
        'role': 'img',
        'aria-label': lines.map(line => line.map(run => run.text).join('')).join('\n')
      }, lines.map((line, index) => h('div', { key: index, class: 'min-h-[1.5em]' }, line.map(runNode))))
    }
  }
})
