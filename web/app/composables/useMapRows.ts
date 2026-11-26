import { canonicalJson } from '#shared/json'
import type { JsonValue } from '#shared/protocol/schema'

export interface MapRow<V> {
  id: number
  key: string
  value: V
}

let nextId = 0

/**
 * Edits a JSON object as a list of rows, so keys can be renamed while typing. Rows are written
 * back on every change; an outside change (an undo) replaces them.
 */
export function useMapRows<V>(
  source: () => Record<string, JsonValue>,
  write: (value: Record<string, JsonValue>) => void,
  toValue: (value: JsonValue) => V,
  fromValue: (value: V) => JsonValue
) {
  const toRows = (map: Record<string, JsonValue>): Array<MapRow<V>> =>
    Object.entries(map).map(([key, value]) => ({ id: nextId++, key, value: toValue(value) }))

  const rows = ref(toRows(source())) as Ref<Array<MapRow<V>>>

  function toMap(list: Array<MapRow<V>>): Record<string, JsonValue> {
    const map: Record<string, JsonValue> = {}
    for (const row of list) {
      map[row.key] = fromValue(row.value)
    }
    return map
  }

  watch(source, (next) => {
    if (canonicalJson(next) !== canonicalJson(toMap(rows.value))) {
      rows.value = toRows(next)
    }
  }, { deep: true })

  function commit() {
    write(toMap(rows.value))
  }

  function add(key: string, value: V) {
    rows.value.push({ id: nextId++, key, value })
    commit()
  }

  function remove(row: MapRow<V>) {
    rows.value = rows.value.filter(other => other !== row)
    commit()
  }

  const duplicates = computed(() => {
    const seen = new Set<string>()
    const twice = new Set<string>()
    for (const row of rows.value) {
      if (seen.has(row.key)) {
        twice.add(row.key)
      }
      seen.add(row.key)
    }
    return twice
  })

  return { rows, commit, add, remove, duplicates }
}
