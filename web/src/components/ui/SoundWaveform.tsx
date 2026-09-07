import { motion } from 'framer-motion'
import './SoundWaveform.css'

interface SoundWaveformProps {
  isListening?: boolean
  barCount?: number
  color?: string
}

export function SoundWaveform({
  isListening = true,
  barCount = 24,
  color = '#5B6AF0',
}: SoundWaveformProps) {
  // Generate random initial height variations
  const bars = Array.from({ length: barCount }, (_, i) => {
    // bell-curve factor so edges are lower and center is higher
    const centerFactor = 1 - Math.abs((i - barCount / 2) / (barCount / 2)) * 0.6
    return {
      id: i,
      minHeight: 4,
      maxHeight: 12 + centerFactor * 24,
      duration: 0.5 + (i % 5) * 0.12,
    }
  })

  return (
    <div className="sound-waveform" aria-hidden="true">
      {bars.map((b) => (
        <motion.span
          key={b.id}
          className="sound-waveform__bar"
          style={{ backgroundColor: color }}
          animate={
            isListening
              ? {
                  height: [b.minHeight, b.maxHeight, b.minHeight],
                  opacity: [0.6, 1, 0.6],
                }
              : { height: 4, opacity: 0.3 }
          }
          transition={{
            duration: b.duration,
            repeat: Infinity,
            repeatType: 'reverse',
            ease: 'easeInOut',
            delay: (b.id * 0.04) % 0.4,
          }}
        />
      ))}
    </div>
  )
}
