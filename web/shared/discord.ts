// A small Discord markdown parser for webhook embed previews: **bold**, *italic* and _italic_,
// __underline__, ~~strikethrough~~, `inline code`, [links](url) shown as their text. Returns
// runs of text with a style; the caller builds DOM from them, never HTML strings.

export interface DiscordStyle {
  bold?: boolean
  italic?: boolean
  underline?: boolean
  strike?: boolean
  code?: boolean
  link?: boolean
}

export interface DiscordRun {
  text: string
  style: DiscordStyle
}

const MARKERS: ReadonlyArray<[string, keyof DiscordStyle]> = [
  ['**', 'bold'], ['__', 'underline'], ['~~', 'strike'], ['*', 'italic'], ['_', 'italic']
]

function push(out: DiscordRun[], text: string, style: DiscordStyle) {
  if (text === '') {
    return
  }
  const last = out.at(-1)
  if (last && JSON.stringify(last.style) === JSON.stringify(style)) {
    last.text += text
  } else {
    out.push({ text, style: { ...style } })
  }
}

/** The closing marker: not escaped, not inside a longer run of the same character. */
function findClose(input: string, token: string, from: number): number {
  let index = input.indexOf(token, from)
  while (index >= 0) {
    const escaped = input[index - 1] === '\\'
    const spaced = token.length === 1 && /\s/.test(input[index - 1] ?? '')
    const longer = input[index + token.length] === token[0]
    if (!escaped && !spaced && !longer) {
      return index
    }
    index = input.indexOf(token, index + 1)
  }
  return -1
}

function parse(input: string, style: DiscordStyle, out: DiscordRun[]) {
  let i = 0
  let plain = ''
  while (i < input.length) {
    const rest = input.slice(i)
    if (rest.startsWith('\\') && rest.length > 1) {
      plain += rest[1]
      i += 2
      continue
    }
    if (rest.startsWith('`')) {
      const end = input.indexOf('`', i + 1)
      if (end > i + 1) {
        push(out, plain, style)
        plain = ''
        push(out, input.slice(i + 1, end), { ...style, code: true })
        i = end + 1
        continue
      }
    }
    const link = /^\[([^\]\n]+)\]\(([^)\s]+)\)/.exec(rest)
    if (link) {
      push(out, plain, style)
      plain = ''
      parse(link[1]!, { ...style, link: true }, out)
      i += link[0].length
      continue
    }
    const marker = MARKERS.find(([token]) => rest.startsWith(token))
    if (marker && !(marker[0].length === 1 && /\s/.test(rest[1] ?? ' '))) {
      const [token, key] = marker
      const end = findClose(input, token, i + token.length)
      if (end > i + token.length) {
        push(out, plain, style)
        plain = ''
        parse(input.slice(i + token.length, end), { ...style, [key]: true }, out)
        i = end + token.length
        continue
      }
    }
    plain += input[i]
    i++
  }
  push(out, plain, style)
}

export function parseDiscord(input: string): DiscordRun[] {
  const out: DiscordRun[] = []
  parse(input, {}, out)
  return out
}

/** Replaces `{name}` placeholders with sample values; unknown ones stay as written. */
export function fillPlaceholders(text: string, values: Readonly<Record<string, string>>): string {
  return text.replace(/\{([a-z_]+)\}/g, (whole, name: string) => values[name] ?? whole)
}

/** Webhook placeholders (header of core/resources/defaults/webhooks.yml). */
export const WEBHOOK_PLACEHOLDERS = ['victim', 'uuid', 'address', 'id', 'display_id', 'operator',
  'reason', 'duration', 'expires', 'type', 'scope', 'server', 'revoker', 'revoke_reason', 'edit',
  'count'] as const

/** The placeholders an event knows: rollback only four, edit adds {edit}. */
export function webhookPlaceholders(event: string): readonly string[] {
  if (event === 'rollback') {
    return ['victim', 'revoker', 'revoke_reason', 'count']
  }
  return WEBHOOK_PLACEHOLDERS.filter(name => name !== 'edit' || event === 'edit')
}
