// Punishment counts the plugin adds to messages (core message.NetworkTotals and
// message.Placeholders). The network's counts work in every message, so they aren't in each
// field's placeholder list; the player's counts are, for the messages that show a punishment.

/** In every message: the network's totals and the punishments in force. */
export const NETWORK_COUNT_TAGS: readonly string[] = [
  'total_bans', 'total_mutes', 'total_warns', 'total_kicks',
  'active_bans', 'active_mutes', 'active_warns'
]

/** Where a message shows a punishment: its victim's counts. */
export const PLAYER_COUNT_TAGS: readonly string[] = [
  'player_bans', 'player_mutes', 'player_warns', 'player_kicks', 'player_active_warns'
]

/** The tags a message may use besides its own placeholders. */
export function messageTagsKnown(placeholders: readonly string[]): string[] {
  return [...placeholders, ...NETWORK_COUNT_TAGS]
}
