// Sample values for message placeholders in previews.
export const PLACEHOLDER_SAMPLES: Readonly<Record<string, string>> = {
  victim: 'Steve',
  name: 'Steve',
  player: 'Steve',
  operator: 'Alex',
  revoker: 'Alex',
  actor: 'Alex',
  reason: 'Unfair advantage (hacked client)',
  revoke_reason: 'Appeal accepted',
  duration: '7 days',
  remaining: '6 days and 23 hours',
  expires: '03 Dec 2026 18:00',
  created: '26 Nov 2026 18:00',
  at: '26 Nov 2026 18:00',
  id: '42',
  display_id: 'K7Q2M9XA',
  scope: 'everywhere',
  type: 'ban',
  server: 'lobby',
  count: '3',
  message: 'hello everyone',
  input: 'Stevee',
  alts: 'Steve2, Steve3',
  details: 'example',
  page: '1',
  pages: '3',
  total: '12',
  template: 'hacking',
  step: '2',
  address: '203.0.113.7',
  uuid: 'ec561538-f3fd-461d-aff5-086b22154bce',
  key: 'reasons.default',
  locale: 'en_US'
}

export function sampleFor(name: string): string {
  return PLACEHOLDER_SAMPLES[name] ?? name.replace(/_/g, ' ')
}
