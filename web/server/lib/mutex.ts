// Serialises async work per key within this process. The editor site keeps live-apply state in
// one process anyway (protocol section 9, "Hosting"), so this is enough to make a session's
// read-modify-write updates (its change-set list, its revision) and a code's result atomic.

export class KeyedMutex {
  private readonly tails = new Map<string, Promise<unknown>>()

  async run<T>(key: string, work: () => Promise<T>): Promise<T> {
    const previous = this.tails.get(key) ?? Promise.resolve()
    const current = previous.catch(() => undefined).then(work)
    const tail = current.catch(() => undefined)
    this.tails.set(key, tail)
    try {
      return await current
    } finally {
      if (this.tails.get(key) === tail) {
        this.tails.delete(key)
      }
    }
  }
}
