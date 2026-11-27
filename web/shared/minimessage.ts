// A small MiniMessage parser and flattener for previews. It covers the tags SakuraBans' messages
// use; anything it doesn't know is shown literally, like MiniMessage does in lenient mode.
// The output is plain data (text runs with a style); the caller builds DOM nodes from it and
// never HTML strings.

export type MmNode
  = | { type: 'text', text: string }
    | { type: 'newline' }
    | { type: 'placeholder', name: string }
    | { type: 'tag', name: string, args: string[], negated: boolean, children: MmNode[] }

export interface MmStyle {
  color?: string
  bold?: boolean
  italic?: boolean
  underlined?: boolean
  strikethrough?: boolean
  obfuscated?: boolean
  /** "open_url: https://…" */
  click?: string
  /** Plain text of the hover. */
  hover?: string
}

export interface MmRun {
  text: string
  style: MmStyle
}

export type MmLine = MmRun[]

export const NAMED_COLORS: Readonly<Record<string, string>> = {
  black: '#000000',
  dark_blue: '#0000aa',
  dark_green: '#00aa00',
  dark_aqua: '#00aaaa',
  dark_red: '#aa0000',
  dark_purple: '#aa00aa',
  gold: '#ffaa00',
  gray: '#aaaaaa',
  grey: '#aaaaaa',
  dark_gray: '#555555',
  dark_grey: '#555555',
  blue: '#5555ff',
  green: '#55ff55',
  aqua: '#55ffff',
  red: '#ff5555',
  light_purple: '#ff55ff',
  yellow: '#ffff55',
  white: '#ffffff'
}

const DECORATIONS: Readonly<Record<string, keyof MmStyle>> = {
  bold: 'bold',
  b: 'bold',
  italic: 'italic',
  i: 'italic',
  em: 'italic',
  underlined: 'underlined',
  u: 'underlined',
  strikethrough: 'strikethrough',
  st: 'strikethrough',
  obfuscated: 'obfuscated',
  obf: 'obfuscated'
}

const COLOR_TAGS = new Set(['color', 'colour', 'c'])
const SELF_CLOSING = new Set(['newline', 'br', 'reset'])
// Known tags without a visible effect in a preview: shown as their content.
const TRANSPARENT = new Set(['insert', 'insertion', 'font', 'shadow'])
const WRAPPERS = new Set(['gradient', 'rainbow', 'transition', 'click', 'hover'])

const HEX = /^#[0-9a-f]{6}$/

export function resolveColor(text: string): string | undefined {
  const lower = text.toLowerCase()
  if (HEX.test(lower)) {
    return lower
  }
  return NAMED_COLORS[lower]
}

function isKnownTag(name: string): boolean {
  return resolveColor(name) !== undefined || COLOR_TAGS.has(name) || name in DECORATIONS
    || SELF_CLOSING.has(name) || TRANSPARENT.has(name) || WRAPPERS.has(name)
}

interface RawTag {
  closing: boolean
  selfClosing: boolean
  name: string
  negated: boolean
  args: string[]
  end: number
  source: string
}

/** Reads `<...>` at `start`; null when it isn't tag syntax (then `<` is literal). */
function readTag(input: string, start: number): RawTag | null {
  let i = start + 1
  let quote: string | null = null
  const parts: string[] = []
  let current = ''
  while (i < input.length) {
    const char = input[i]!
    if (quote !== null) {
      if (char === '\\' && i + 1 < input.length && (input[i + 1] === quote || input[i + 1] === '\\')) {
        current += input[i + 1]
        i += 2
        continue
      }
      if (char === quote) {
        quote = null
      } else {
        current += char
      }
      i++
      continue
    }
    if (char === '\'' || char === '"') {
      quote = char
      i++
      continue
    }
    if (char === '>') {
      parts.push(current)
      break
    }
    if (char === '<' || char === '\n') {
      return null
    }
    if (char === ':') {
      parts.push(current)
      current = ''
      i++
      continue
    }
    current += char
    i++
  }
  if (i >= input.length || quote !== null) {
    return null
  }
  let head = parts[0] ?? ''
  const closing = head.startsWith('/')
  if (closing) {
    head = head.slice(1)
  }
  let selfClosing = false
  const last = parts.length - 1
  if (parts[last]!.endsWith('/')) {
    selfClosing = true
    if (last === 0) {
      head = head.slice(0, -1)
    } else {
      parts[last] = parts[last]!.slice(0, -1)
    }
  }
  const negated = head.startsWith('!')
  if (negated) {
    head = head.slice(1)
  }
  const name = head.toLowerCase()
  if (!/^[a-z0-9_\-.#]+$/.test(name)) {
    return null
  }
  return {
    closing,
    selfClosing,
    name,
    negated,
    args: parts.slice(1),
    end: i + 1,
    source: input.slice(start, i + 1)
  }
}

function closesTag(closing: string, open: Extract<MmNode, { type: 'tag' }>): boolean {
  if (closing === open.name) {
    return true
  }
  if (COLOR_TAGS.has(closing) && COLOR_TAGS.has(open.name)) {
    return true
  }
  const a = DECORATIONS[closing]
  return a !== undefined && a === DECORATIONS[open.name]
}

export interface ParseOptions {
  /** Names substituted as placeholders (`<reason>`). */
  placeholders?: ReadonlySet<string> | readonly string[]
  /** Names of colour tags defined by the messages themselves (the palette: `<main>`). */
  styles?: ReadonlySet<string> | readonly string[]
}

/** Parses MiniMessage into a tree. Never throws: malformed input becomes literal text. */
export function parseMiniMessage(input: string, options: ParseOptions = {}): MmNode[] {
  const placeholders = new Set(options.placeholders ?? [])
  const styles = new Set(options.styles ?? [])
  const root: MmNode[] = []
  const stack: Array<Extract<MmNode, { type: 'tag' }>> = []
  const children = () => (stack.length > 0 ? stack.at(-1)!.children : root)
  let text = ''
  const flush = () => {
    if (text.length > 0) {
      children().push({ type: 'text', text })
      text = ''
    }
  }

  let i = 0
  while (i < input.length) {
    const char = input[i]!
    if (char === '\\' && (input[i + 1] === '<' || input[i + 1] === '\\')) {
      text += input[i + 1]
      i += 2
      continue
    }
    if (char === '\r' && input[i + 1] === '\n') {
      i++
      continue
    }
    if (char === '\n') {
      flush()
      children().push({ type: 'newline' })
      i++
      continue
    }
    if (char === '<') {
      const tag = readTag(input, i)
      if (tag !== null) {
        const known = isKnownTag(tag.name) || styles.has(tag.name)
        const placeholder = !known && placeholders.has(tag.name)
        if (tag.closing) {
          if (known) {
            const index = stack.findLastIndex(open => closesTag(tag.name, open))
            if (index >= 0) {
              flush()
              stack.length = index
            }
            // An unmatched closing tag of a known kind is dropped, as MiniMessage does.
            i = tag.end
            continue
          }
          if (placeholder) {
            i = tag.end
            continue
          }
        } else if (placeholder) {
          flush()
          children().push({ type: 'placeholder', name: tag.name })
          i = tag.end
          continue
        } else if (known) {
          flush()
          if (tag.name === 'newline' || tag.name === 'br') {
            children().push({ type: 'newline' })
          } else if (tag.name === 'reset') {
            stack.length = 0
          } else {
            const node: MmNode = { type: 'tag', name: tag.name, args: tag.args, negated: tag.negated, children: [] }
            children().push(node)
            if (!tag.selfClosing) {
              stack.push(node)
            }
          }
          i = tag.end
          continue
        }
        // Unknown: literal, including its brackets.
        text += tag.source
        i = tag.end
        continue
      }
    }
    text += char
    i++
  }
  flush()
  return root
}

interface FlatRun {
  text: string
  style: MmStyle
  /** Colour still to be given by an enclosing gradient. */
  auto?: boolean
}

type FlatItem = FlatRun | 'newline'

export interface RenderOptions {
  /** Values for placeholders, inserted as plain text (as Placeholder.unparsed does). */
  placeholders?: Readonly<Record<string, string>>
  /** Placeholders whose value is MiniMessage itself (like `<prefix>`). */
  components?: Readonly<Record<string, string>>
  /** Colour tags and their validated hex colour (the palette: `main` → `#f591ff`). */
  colors?: Readonly<Record<string, string>>
}

function hexToRgb(hex: string): [number, number, number] {
  return [1, 3, 5].map(offset => Number.parseInt(hex.slice(offset, offset + 2), 16)) as [number, number, number]
}

function rgbToHex([r, g, b]: [number, number, number]): string {
  return `#${[r, g, b].map(value => Math.round(Math.min(255, Math.max(0, value))).toString(16).padStart(2, '0')).join('')}`
}

function mix(a: string, b: string, t: number): string {
  const [ar, ag, ab] = hexToRgb(a)
  const [br, bg, bb] = hexToRgb(b)
  return rgbToHex([ar + (br - ar) * t, ag + (bg - ag) * t, ab + (bb - ab) * t])
}

function hsvToHex(h: number, s: number, v: number): string {
  const i = Math.floor(h * 6)
  const f = h * 6 - i
  const p = v * (1 - s)
  const q = v * (1 - f * s)
  const t = v * (1 - (1 - f) * s)
  const table: Array<[number, number, number]> = [[v, t, p], [q, v, p], [p, v, t], [p, q, v], [t, p, v], [v, p, q]]
  const [r, g, b] = table[((i % 6) + 6) % 6]!
  return rgbToHex([r * 255, g * 255, b * 255])
}

function plainText(items: FlatItem[]): string {
  return items.map(item => (item === 'newline' ? '\n' : item.text)).join('')
}

function colorize(items: FlatItem[], colorAt: (index: number, count: number) => string): FlatItem[] {
  let count = 0
  for (const item of items) {
    if (item !== 'newline') {
      count += [...item.text].length
    }
  }
  const out: FlatItem[] = []
  let index = 0
  for (const item of items) {
    if (item === 'newline') {
      out.push(item)
      continue
    }
    for (const char of item.text) {
      if (item.auto) {
        out.push({ text: char, style: { ...item.style, color: colorAt(index, count) } })
      } else {
        out.push({ text: char, style: item.style })
      }
      index++
    }
  }
  return out
}

function flatten(nodes: MmNode[], style: MmStyle, auto: boolean, options: RenderOptions,
  depth: number): FlatItem[] {
  const out: FlatItem[] = []
  for (const node of nodes) {
    switch (node.type) {
      case 'text':
        out.push({ text: node.text, style, auto })
        break
      case 'newline':
        out.push('newline')
        break
      case 'placeholder': {
        const component = options.components?.[node.name]
        if (component !== undefined && depth < 3) {
          const names = [...Object.keys(options.placeholders ?? {}), ...Object.keys(options.components ?? {})]
          out.push(...flatten(parseMiniMessage(component, { placeholders: names, styles: Object.keys(options.colors ?? {}) }), style, auto, options, depth + 1))
        } else {
          out.push({ text: options.placeholders?.[node.name] ?? `<${node.name}>`, style, auto })
        }
        break
      }
      case 'tag':
        out.push(...flattenTag(node, style, auto, options, depth))
        break
    }
  }
  return out
}

function flattenTag(node: Extract<MmNode, { type: 'tag' }>, style: MmStyle, auto: boolean,
  options: RenderOptions, depth: number): FlatItem[] {
  const styled = options.colors?.[node.name]
  if (styled !== undefined) {
    return flatten(node.children, { ...style, color: styled }, false, options, depth)
  }
  const direct = resolveColor(node.name)
  if (direct !== undefined) {
    return flatten(node.children, { ...style, color: direct }, false, options, depth)
  }
  if (COLOR_TAGS.has(node.name)) {
    const color = resolveColor(node.args[0] ?? '')
    return color === undefined
      ? flatten(node.children, style, auto, options, depth)
      : flatten(node.children, { ...style, color }, false, options, depth)
  }
  const decoration = DECORATIONS[node.name]
  if (decoration !== undefined) {
    return flatten(node.children, { ...style, [decoration]: !node.negated }, auto, options, depth)
  }
  switch (node.name) {
    case 'gradient': {
      const colors = node.args.map(resolveColor).filter((color): color is string => color !== undefined)
      const stops = colors.length === 0 ? ['#ffffff', '#000000'] : colors.length === 1 ? [colors[0]!, colors[0]!] : colors
      const inner = flatten(node.children, style, true, options, depth)
      return colorize(inner, (index, count) => {
        const t = count <= 1 ? 0 : index / (count - 1)
        const scaled = t * (stops.length - 1)
        const segment = Math.min(stops.length - 2, Math.floor(scaled))
        return mix(stops[segment]!, stops[segment + 1]!, scaled - segment)
      })
    }
    case 'rainbow': {
      const arg = node.args[0] ?? ''
      const reverse = arg.startsWith('!')
      const phase = Number.parseInt(arg.replace('!', ''), 10)
      const offset = Number.isFinite(phase) ? phase / 10 : 0
      const inner = flatten(node.children, style, true, options, depth)
      return colorize(inner, (index, count) => {
        const position = count === 0 ? 0 : index / count
        const hue = ((reverse ? 1 - position : position) + offset) % 1
        return hsvToHex(hue < 0 ? hue + 1 : hue, 1, 1)
      })
    }
    case 'transition': {
      const color = resolveColor(node.args[0] ?? '')
      return flatten(node.children, color ? { ...style, color } : style, color ? false : auto, options, depth)
    }
    case 'click': {
      const action = node.args[0] ?? 'click'
      const value = node.args.slice(1).join(':')
      return flatten(node.children, { ...style, click: `${action}: ${value}` }, auto, options, depth)
    }
    case 'hover': {
      const names = [...Object.keys(options.placeholders ?? {}), ...Object.keys(options.components ?? {})]
      const hoverNodes = parseMiniMessage(node.args.slice(1).join(':'), { placeholders: names, styles: Object.keys(options.colors ?? {}) })
      const hover = plainText(flatten(hoverNodes, {}, false, options, depth + 1))
      return flatten(node.children, { ...style, hover }, auto, options, depth)
    }
    default:
      return flatten(node.children, style, auto, options, depth)
  }
}

function sameStyle(a: MmStyle, b: MmStyle): boolean {
  return a.color === b.color && a.bold === b.bold && a.italic === b.italic
    && a.underlined === b.underlined && a.strikethrough === b.strikethrough
    && a.obfuscated === b.obfuscated && a.click === b.click && a.hover === b.hover
}

/** Renders MiniMessage to lines of styled text runs. */
export function renderMiniMessage(input: string, options: RenderOptions = {}): MmLine[] {
  const names = [...Object.keys(options.placeholders ?? {}), ...Object.keys(options.components ?? {})]
  const items = flatten(parseMiniMessage(input, { placeholders: names, styles: Object.keys(options.colors ?? {}) }), {}, false, options, 0)
  const lines: MmLine[] = [[]]
  for (const item of items) {
    if (item === 'newline') {
      lines.push([])
      continue
    }
    if (item.text.length === 0) {
      continue
    }
    const line = lines.at(-1)!
    const previous = line.at(-1)
    const run = { text: item.text, style: item.style }
    if (previous && sameStyle(previous.style, run.style)) {
      previous.text += run.text
    } else {
      line.push(run)
    }
  }
  return lines
}

/** Tags in a text that this renderer shows literally (for a hint under the preview). */
export function unknownTags(input: string, placeholders: readonly string[]): string[] {
  const known = new Set(placeholders)
  const found = new Set<string>()
  const pattern = /<\/?!?([a-z0-9_\-.#]+)(?::[^>]*)?\/?>/gi
  for (const match of input.matchAll(pattern)) {
    const name = match[1]!.toLowerCase()
    if (match.index > 0 && input[match.index - 1] === '\\') {
      continue
    }
    if (!isKnownTag(name) && !known.has(name)) {
      found.add(name)
    }
  }
  return [...found]
}
