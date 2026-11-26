// An in-memory sliding window per client address. One process only: behind several instances,
// each counts on its own.

export class SlidingWindowLimiter {
  private readonly hits = new Map<string, number[]>()
  private lastSweep = 0

  /**
   * Records an attempt. Returns 0 when it is allowed, otherwise the milliseconds until the
   * oldest attempt in the window leaves it (the attempt isn't recorded then).
   */
  hit(address: string, limit: number, windowMs: number, now: number): number {
    if (limit <= 0) {
      return 0
    }
    this.sweep(windowMs, now)
    const recent = (this.hits.get(address) ?? []).filter(time => time > now - windowMs)
    if (recent.length >= limit) {
      this.hits.set(address, recent)
      return Math.max(1, recent[0]! + windowMs - now)
    }
    recent.push(now)
    this.hits.set(address, recent)
    return 0
  }

  private sweep(windowMs: number, now: number) {
    if (now - this.lastSweep < windowMs) {
      return
    }
    this.lastSweep = now
    for (const [address, times] of this.hits) {
      if (times.every(time => time <= now - windowMs)) {
        this.hits.delete(address)
      }
    }
  }

  clear() {
    this.hits.clear()
  }
}
