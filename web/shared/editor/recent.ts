// The "Recent changes" list (protocol 5.6): how one entry reads. Pure, so it is tested.
import type { RecentChange } from '../protocol/schema'

const VERBS: Record<string, string> = {
  update: 'updated',
  create: 'created',
  delete: 'deleted',
  reset: 'reset'
}

const KINDS: Record<RecentChange['kind'], string> = {
  settings: 'settings',
  messages: 'message',
  templates: 'template'
}

/** What was changed: the keys of a settings change, a message's key and locale, a template's name. */
export function recentSubject(change: RecentChange): string {
  if (change.kind === 'settings') {
    return change.keys.length > 0 ? change.keys.join(', ') : 'settings'
  }
  if (change.kind === 'messages') {
    return change.locale ? `${change.target} (${change.locale})` : change.target
  }
  return change.target
}

/** For example `Alex updated message screen.ban (en_US) in global`. */
export function recentText(change: RecentChange): string {
  const verb = VERBS[change.action] ?? change.action
  const what = change.kind === 'settings' && change.keys.length === 0 ? 'settings' : `${KINDS[change.kind]} ${recentSubject(change)}`
  return `${change.actor} ${verb} ${what} in ${change.scope}`
}

export const RECENT_TAB = 'recent'

// The editor's tabs, in order: the sections you may edit, then Recent changes when the payload
// carries any.
export function editorTabs<T extends string>(sections: readonly T[], recentCount: number): (T | typeof RECENT_TAB)[] {
  return recentCount > 0 ? [...sections, RECENT_TAB] : [...sections]
}
