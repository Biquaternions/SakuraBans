import { describe, expect, it } from 'vitest'
import { messageStyles, STYLE_TAGS } from '../shared/editor/styles'

describe('messageStyles', () => {
  it('takes the prefix, the name and the palette from the texts in effect', () => {
    const texts: Record<string, string> = {
      'prefix': '<plugin> » ',
      'plugin': 'Sakura',
      'palette.main': 'dark_aqua',
      'palette.accent': '#ABCDEF',
      'palette.highlight': 'not a colour'
    }
    const styles = messageStyles(key => texts[key])
    expect(styles.components).toEqual({ prefix: '<plugin> » ', plugin: 'Sakura' })
    expect(styles.colors).toEqual({ main: '#00aaaa', accent: '#abcdef', highlight: '#ff91bf', danger: '#ff5555' })
  })

  it('leaves out the message being edited, which can\'t insert itself', () => {
    expect(Object.keys(messageStyles(() => 'x', 'prefix').components)).toEqual(['plugin'])
  })

  it('lists every tag that isn\'t a placeholder', () => {
    expect(STYLE_TAGS).toEqual(['prefix', 'plugin', 'main', 'accent', 'highlight', 'danger'])
  })
})
