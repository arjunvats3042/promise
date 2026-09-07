import { useState, useEffect } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Sparkles, CheckCircle, Flame, X } from 'lucide-react'
import './DynamicIsland.css'

export interface DynamicIslandItem {
  id: string
  icon: 'sparkles' | 'check' | 'flame'
  title: string
  subtitle: string
  details?: string
  accentColor?: string
}

const DEFAULT_ITEMS: DynamicIslandItem[] = [
  {
    id: 'ai',
    icon: 'sparkles',
    title: 'Gemini 3.7 Online',
    subtitle: 'Advisory engine standing by',
    details: 'Natural language refiner, thought parsing, and weekly accountability digest active.',
    accentColor: '#5B6AF0',
  },
  {
    id: 'focus',
    icon: 'check',
    title: '3 Promises Today',
    subtitle: '1 completed · 2 pending',
    details: 'Zero notifications mode enabled. Focus on follow-through.',
    accentColor: '#10B981',
  },
  {
    id: 'streak',
    icon: 'flame',
    title: '14-Day Streak',
    subtitle: 'Morning routine on track',
    details: 'Consistently kept promises for 2 consecutive weeks.',
    accentColor: '#F59E0B',
  },
]

export function DynamicIsland({ items = DEFAULT_ITEMS }: { items?: DynamicIslandItem[] }) {
  const [currentIndex, setCurrentIndex] = useState(0)
  const [isExpanded, setIsExpanded] = useState(false)

  // Rotate items when collapsed
  useEffect(() => {
    if (isExpanded) return
    const interval = setInterval(() => {
      setCurrentIndex((prev) => (prev + 1) % items.length)
    }, 4500)
    return () => clearInterval(interval)
  }, [isExpanded, items.length])

  const current = items[currentIndex]

  const getIcon = (type: string) => {
    switch (type) {
      case 'sparkles':
        return <Sparkles size={14} className="dynamic-island__icon-svg" />
      case 'check':
        return <CheckCircle size={14} className="dynamic-island__icon-svg" />
      case 'flame':
        return <Flame size={14} className="dynamic-island__icon-svg" />
      default:
        return <Sparkles size={14} className="dynamic-island__icon-svg" />
    }
  }

  return (
    <div className="dynamic-island-wrapper">
      <motion.div
        layout
        className={`dynamic-island ${isExpanded ? 'dynamic-island--expanded' : ''}`}
        transition={{
          type: 'spring',
          stiffness: 420,
          damping: 32,
        }}
        onClick={() => setIsExpanded(!isExpanded)}
        style={{ cursor: 'pointer' }}
        role="button"
        aria-expanded={isExpanded}
        tabIndex={0}
      >
        <AnimatePresence mode="wait">
          {!isExpanded ? (
            <motion.div
              key={current.id}
              className="dynamic-island__compact"
              initial={{ opacity: 0, y: 10, scale: 0.95 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: -10, scale: 0.95 }}
              transition={{ duration: 0.25 }}
            >
              <div
                className="dynamic-island__badge-dot"
                style={{ backgroundColor: current.accentColor || '#5B6AF0' }}
              >
                {getIcon(current.icon)}
              </div>
              <span className="dynamic-island__text-main">{current.title}</span>
              <span className="dynamic-island__text-sep">·</span>
              <span className="dynamic-island__text-sub">{current.subtitle}</span>
            </motion.div>
          ) : (
            <motion.div
              key="expanded"
              className="dynamic-island__expanded-content"
              initial={{ opacity: 0, scale: 0.92 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.92 }}
              transition={{ duration: 0.25, delay: 0.05 }}
            >
              <div className="dynamic-island__expanded-top">
                <div className="dynamic-island__expanded-header">
                  <div
                    className="dynamic-island__badge-dot dynamic-island__badge-dot--lg"
                    style={{ backgroundColor: current.accentColor || '#5B6AF0' }}
                  >
                    {getIcon(current.icon)}
                  </div>
                  <div>
                    <h4 className="dynamic-island__expanded-title">{current.title}</h4>
                    <p className="dynamic-island__expanded-subtitle">{current.subtitle}</p>
                  </div>
                </div>
                <button
                  className="dynamic-island__close-btn"
                  onClick={(e) => {
                    e.stopPropagation()
                    setIsExpanded(false)
                  }}
                  aria-label="Close"
                >
                  <X size={14} />
                </button>
              </div>

              {current.details && (
                <p className="dynamic-island__expanded-details">{current.details}</p>
              )}

              <div className="dynamic-island__expanded-footer">
                <div className="dynamic-island__dots">
                  {items.map((it, idx) => (
                    <button
                      key={it.id}
                      className={`dynamic-island__dot ${idx === currentIndex ? 'dynamic-island__dot--active' : ''}`}
                      onClick={(e) => {
                        e.stopPropagation()
                        setCurrentIndex(idx)
                      }}
                      aria-label={`Show ${it.title}`}
                    />
                  ))}
                </div>
                <span className="dynamic-island__hint">Tap to switch · ESC to close</span>
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </motion.div>
    </div>
  )
}
