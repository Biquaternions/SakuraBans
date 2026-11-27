import { describe, expect, it } from 'vitest'
import { parseMiniMessage, renderMiniMessage, unknownTags } from '../shared/minimessage'

function text(lines: ReturnType<typeof renderMiniMessage>): string {
  return lines.map(line => line.map(run => run.text).join('')).join('\n')
}

describe('parseMiniMessage', () => {
  it('builds a tree of nested tags', () => {
    expect(parseMiniMessage('<red>a<bold>b</bold></red>c')).toEqual([
      { type: 'tag', name: 'red', args: [], negated: false, children: [
        { type: 'text', text: 'a' },
        { type: 'tag', name: 'bold', args: [], negated: false, children: [{ type: 'text', text: 'b' }] }
      ] },
      { type: 'text', text: 'c' }
    ])
  })

  it('reads arguments, quotes and negation', () => {
    const [node] = parseMiniMessage('<hover:show_text:\'<red>hi: there\'>x</hover>')
    expect(node).toMatchObject({ type: 'tag', name: 'hover', args: ['show_text', '<red>hi: there'] })
    expect(parseMiniMessage('<!italic>x')[0]).toMatchObject({ name: 'italic', negated: true })
  })

  it('keeps unknown tags and escaped brackets literal', () => {
    expect(parseMiniMessage('<foo>bar</foo>')).toEqual([{ type: 'text', text: '<foo>bar</foo>' }])
    expect(parseMiniMessage('\\<red>x')).toEqual([{ type: 'text', text: '<red>x' }])
    expect(parseMiniMessage('a < b > c')).toEqual([{ type: 'text', text: 'a < b > c' }])
    expect(parseMiniMessage('<red')).toEqual([{ type: 'text', text: '<red' }])
  })

  it('recognises placeholders only when they are given', () => {
    expect(parseMiniMessage('<reason>', { placeholders: ['reason'] })).toEqual([{ type: 'placeholder', name: 'reason' }])
    expect(parseMiniMessage('<reason>')).toEqual([{ type: 'text', text: '<reason>' }])
  })

  it('handles newlines, self-closing tags and reset', () => {
    expect(parseMiniMessage('a<newline>b<br>c\nd')).toEqual([
      { type: 'text', text: 'a' }, { type: 'newline' }, { type: 'text', text: 'b' }, { type: 'newline' },
      { type: 'text', text: 'c' }, { type: 'newline' }, { type: 'text', text: 'd' }
    ])
    expect(parseMiniMessage('<red>a<reset>b')).toEqual([
      { type: 'tag', name: 'red', args: [], negated: false, children: [{ type: 'text', text: 'a' }] },
      { type: 'text', text: 'b' }
    ])
  })

  it('closes intermediate tags and drops unmatched closers', () => {
    expect(parseMiniMessage('<red><bold>a</red>b</green>')).toEqual([
      { type: 'tag', name: 'red', args: [], negated: false, children: [
        { type: 'tag', name: 'bold', args: [], negated: false, children: [{ type: 'text', text: 'a' }] }
      ] },
      { type: 'text', text: 'b' }
    ])
    expect(parseMiniMessage('<b>x</bold>y')).toHaveLength(2)
  })
})

describe('renderMiniMessage', () => {
  it('applies colours and decorations', () => {
    const [line] = renderMiniMessage('<#f4a7b9>Sakura</#f4a7b9> <gray>»</gray> <b>x</b><color:gold>y</color>')
    expect(line).toEqual([
      { text: 'Sakura', style: { color: '#f4a7b9' } },
      { text: ' ', style: {} },
      { text: '»', style: { color: '#aaaaaa' } },
      { text: ' ', style: {} },
      { text: 'x', style: { bold: true } },
      { text: 'y', style: { color: '#ffaa00' } }
    ])
  })

  it('substitutes placeholders as plain text', () => {
    const lines = renderMiniMessage('<red>Muted: <reason></red>', { placeholders: { reason: '<bold>spam' } })
    expect(lines).toEqual([[{ text: 'Muted: <bold>spam', style: { color: '#ff5555' } }]])
  })

  it('renders component placeholders such as the prefix', () => {
    const lines = renderMiniMessage('<prefix>hi', { components: { prefix: '<red>P</red> ' } })
    expect(text(lines)).toBe('P hi')
    expect(lines[0]![0]!.style.color).toBe('#ff5555')
  })

  it('colours the palette tags, also inside the prefix and the plugin name', () => {
    const options = {
      components: { prefix: '<plugin> <accent>...🖉</accent> ', plugin: '<gradient:#F591FF:#FFFFFF:#F591FF>SakuraBans</gradient>' },
      colors: { main: '#f591ff', accent: '#b891ff' }
    }
    const [line] = renderMiniMessage('<prefix><main>Reloaded <accent>3</accent></main>', options)
    expect(line!.map(run => run.text).join('')).toBe('SakuraBans ...🖉 Reloaded 3')
    expect(line![0]!.style.color).toBe('#f591ff')
    expect(line!.find(run => run.text.includes('...'))?.style.color).toBe('#b891ff')
    expect(line!.at(-1)).toEqual({ text: '3', style: { color: '#b891ff' } })
    expect(text(renderMiniMessage('<main>x</main>'))).toBe('<main>x</main>')
  })

  it('splits lines', () => {
    expect(text(renderMiniMessage('<red>a\n\nb</red>'))).toBe('a\n\nb')
  })

  it('colours gradients per character, ends included', () => {
    const [line] = renderMiniMessage('<gradient:#000000:#ffffff>abc</gradient>')
    expect(line!.map(run => run.style.color)).toEqual(['#000000', '#808080', '#ffffff'])
  })

  it('keeps explicit colours inside a gradient', () => {
    const [line] = renderMiniMessage('<gradient:red:blue>a<green>b</green>c</gradient>')
    expect(line![1]).toEqual({ text: 'b', style: { color: '#55ff55' } })
  })

  it('colours rainbows', () => {
    const [line] = renderMiniMessage('<rainbow>ab</rainbow>')
    expect(line![0]!.style.color).toBe('#ff0000')
    expect(line).toHaveLength(2)
  })

  it('shows click and hover as metadata', () => {
    const [line] = renderMiniMessage('<click:open_url:https://example.org><hover:show_text:\'<red>Open\'>link</hover></click>')
    expect(line).toEqual([{ text: 'link', style: { click: 'open_url: https://example.org', hover: 'Open' } }])
  })

  it('shows unknown tags literally and lists them', () => {
    expect(text(renderMiniMessage('<sprite:x>hi'))).toBe('<sprite:x>hi')
    expect(unknownTags('<sprite:x> <red>a</red> <reason> \\<nope>', ['reason'])).toEqual(['sprite'])
  })

  it('never produces markup from input', () => {
    const lines = renderMiniMessage('<img src=x onerror=alert(1)>')
    expect(text(lines)).toBe('<img src=x onerror=alert(1)>')
  })
})
