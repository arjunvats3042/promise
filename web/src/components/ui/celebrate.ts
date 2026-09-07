import confetti from 'canvas-confetti'

interface CelebrateOptions {
  particleCount?: number
  spread?: number
  origin?: { x?: number; y?: number }
}

export function triggerCelebration(options?: CelebrateOptions) {
  const count = options?.particleCount ?? 50
  const spread = options?.spread ?? 70
  const origin = options?.origin ?? { y: 0.7, x: 0.5 }

  // Apple-grade palette: Indigo, Emerald, Amber, Silver, Violet
  const colors = ['#5B6AF0', '#10B981', '#F59E0B', '#F8FAFC', '#8B5CF6']

  try {
    // Primary burst
    confetti({
      particleCount: count,
      spread,
      origin,
      colors,
      ticks: 200,
      gravity: 1.1,
      scalar: 0.9,
      shapes: ['circle', 'square'],
      disableForReducedMotion: true,
    })

    // Secondary subtle sparkle burst
    setTimeout(() => {
      confetti({
        particleCount: Math.round(count * 0.4),
        spread: spread * 1.3,
        origin: { x: origin.x, y: (origin.y ?? 0.7) - 0.05 },
        colors: ['#F8FAFC', '#5B6AF0'],
        ticks: 150,
        gravity: 0.9,
        scalar: 0.6,
        shapes: ['circle'],
        disableForReducedMotion: true,
      })
    }, 120)
  } catch (err) {
    // Canvas confetti gracefully fails if canvas is unsupported
    console.debug('Confetti trigger skipped', err)
  }
}
