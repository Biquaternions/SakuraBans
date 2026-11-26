// Limits the plugin enforces, mirrored for client-side validation (the plugin checks again).

export const MAX_IDENTIFIER = 64
export const MAX_REASON = 1024
export const MAX_MESSAGE = 4096
export const MAX_STEPS = 64
export const MAX_GROUPS = 16
export const MAX_WEIGHT = 100
export const MAX_ACTIONS = 16
export const MAX_ACTION_LENGTH = 255

export const IDENTIFIER = /^[a-z0-9_-]{1,64}$/

export function isIdentifier(text: string): boolean {
  return IDENTIFIER.test(text)
}

export const IDENTIFIER_HINT = '1-64 characters of a-z, 0-9, _ and -'

/** Checks one console command (template actions, warning actions); null when fine. */
export function actionProblem(action: string): string | null {
  const command = action.trim().replace(/^\//, '')
  if (command.length === 0) {
    return 'must not be empty'
  }
  if (/[\r\n]/.test(command)) {
    return 'must be one line'
  }
  if (command.length > MAX_ACTION_LENGTH) {
    return `is longer than ${MAX_ACTION_LENGTH} characters`
  }
  return null
}
