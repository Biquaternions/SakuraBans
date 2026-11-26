// Discord markdown as DOM through VNodes: text nodes only, no innerHTML.
import type { PropType, VNode } from 'vue'
import { defineComponent, h } from 'vue'
import { parseDiscord } from '#shared/discord'
import type { DiscordRun } from '#shared/discord'

function run(item: DiscordRun, index: number): VNode {
  const { bold, italic, underline, strike, code, link } = item.style
  const classes = [
    bold ? 'font-bold' : '',
    italic ? 'italic' : '',
    underline || link ? 'underline' : '',
    strike ? 'line-through' : '',
    code ? 'rounded bg-black/30 px-1 font-mono text-[0.85em]' : '',
    link ? 'discord-link' : ''
  ].filter(Boolean)
  return h(code ? 'code' : 'span', { key: index, class: classes }, item.text)
}

export default defineComponent({
  name: 'DiscordText',
  props: {
    text: { type: String, required: true },
    tag: { type: String as PropType<string>, default: 'span' }
  },
  setup(props) {
    return () => h(props.tag, { class: 'whitespace-pre-wrap break-words' }, parseDiscord(props.text).map(run))
  }
})
