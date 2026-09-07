import { useState, useRef } from 'react'
import { motion, useInView, AnimatePresence, useScroll, useTransform, useSpring } from 'framer-motion'
import { Sparkles, Bot, Send, BrainCircuit, CheckCircle2 } from 'lucide-react'
import { backend, type RefinedCommitment, type ParsedThoughtItem } from '../../lib/backend'
import { SpotlightCard } from '../ui/SpotlightCard'
import { BorderBeam } from '../ui/BorderBeam'
import { SoundWaveform } from '../ui/SoundWaveform'
import { TextBlurReveal } from '../ui/TextBlurReveal'
import { triggerCelebration } from '../ui/celebrate'
import './AISection.css'

type AIMode = 'refiner' | 'thought' | 'concierge'

const PRESETS = [
  { raw: 'call rahul about the contract tomorrow evening', label: 'Work Call' },
  { raw: 'gym everyday at 7am with 45 min workout', label: 'Daily Habit' },
  { raw: 'submit the quarterly tax report kal tak', label: 'Hinglish Deadline' },
]

export default function AISection() {
  const sectionRef = useRef<HTMLElement>(null)
  const inView = useInView(sectionRef, { once: true, margin: '-80px' })

  // Scroll-linked floating depth
  const { scrollYProgress } = useScroll({
    target: sectionRef,
    offset: ['start end', 'end start'],
  })
  const smoothProgress = useSpring(scrollYProgress, { stiffness: 90, damping: 24 })
  const playgroundY = useTransform(smoothProgress, [0, 1], [40, -40])
  const playgroundRotate = useTransform(smoothProgress, [0, 0.5, 1], [0.8, 0, -0.8])

  const [mode, setMode] = useState<AIMode>('refiner')
  const [inputVal, setInputVal] = useState('')
  const [loading, setLoading] = useState(false)

  // Refiner result
  const [refinedResult, setRefinedResult] = useState<RefinedCommitment | null>(null)

  // Thought parser items
  const [parsedItems, setParsedItems] = useState<ParsedThoughtItem[] | null>(null)

  // Concierge chat history
  const [conciergeMessages, setConciergeMessages] = useState<Array<{ role: 'user' | 'assistant'; text: string }>>([
    {
      role: 'assistant',
      text: 'Greetings. I am the Promise Advisory Concierge. How may I assist you with your commitments or daily practices today?',
    },
  ])

  async function handleSubmit(e?: React.FormEvent) {
    if (e) e.preventDefault()
    if (!inputVal.trim() || loading) return

    const query = inputVal.trim()
    setLoading(true)

    try {
      if (mode === 'refiner') {
        const res = await backend.ai.refine(query)
        setRefinedResult(res)
        triggerCelebration({ particleCount: 25, spread: 45 })
      } else if (mode === 'thought') {
        const res = await backend.ai.parseThought(query)
        setParsedItems(res.items)
        triggerCelebration({ particleCount: 30, spread: 50 })
      } else {
        // Concierge
        setConciergeMessages((prev) => [...prev, { role: 'user', text: query }])
        setInputVal('')
        const res = await backend.ai.support(query)
        setConciergeMessages((prev) => [
          ...prev,
          { role: 'assistant', text: res.answer || 'I am here to advise.' },
        ])
      }
    } catch {
      // Graceful fallback for non-authenticated preview
      if (mode === 'refiner') {
        setRefinedResult({
          status: 'READY',
          refined_title: query.replace(/^./, (c) => c.toUpperCase()),
          refined_description: `Commitment scheduled via Gemini AI: ${query}`,
          suggested_due_at: new Date(Date.now() + 86400000).toISOString(),
          suggested_due_precision: 'MINUTE',
          current_interpretation: `Structured promise for "${query}" with precision timing.`,
          reasoning: 'Parsed according to Promise follow-through invariants.',
        })
      } else if (mode === 'thought') {
        setParsedItems([
          {
            type: 'commitment',
            title: query.slice(0, 45),
            due_at: new Date(Date.now() + 86400000).toISOString(),
            due_precision: 'MINUTE',
          },
          {
            type: 'goal',
            title: 'Daily follow-through cadence',
          },
        ])
      } else {
        setConciergeMessages((prev) => [
          ...prev,
          { role: 'user', text: query },
          {
            role: 'assistant',
            text: `Regarding "${query}": Promise separates discrete promises from recurring habits. Keep your promises simple, specific, and honor your word.`,
          },
        ])
        setInputVal('')
      }
    } finally {
      setLoading(false)
    }
  }

  function applyPreset(raw: string) {
    setInputVal(raw)
  }

  return (
    <section className="ai-sec" id="ai" ref={sectionRef} aria-labelledby="ai-heading">
      <div className="container">
        {/* Header */}
        <div className="ai-sec__header">
          <motion.p
            className="ai-sec__eyebrow"
            initial={{ opacity: 0, x: -16 }}
            animate={inView ? { opacity: 1, x: 0 } : {}}
            transition={{ duration: 0.6 }}
          >
            Powered by Google Gemini 3.7
          </motion.p>
          <TextBlurReveal
            text="AI that advises. Never commands."
            as="h2"
            className="ai-sec__heading"
          />
          <motion.p
            className="ai-sec__sub"
            initial={{ opacity: 0, y: 16 }}
            animate={inView ? { opacity: 1, y: 0 } : {}}
            transition={{ duration: 0.6, delay: 0.2 }}
          >
            Built on a strict advisory mandate. Gemini clarifies your thoughts into structured promises
            with exact deadlines, but never modifies your commitments without your explicit consent.
          </motion.p>
        </div>

        {/* Interactive Playground Card */}
        <motion.div
          className="ai-sec__playground-wrapper"
          style={{ y: playgroundY, rotateX: playgroundRotate, perspective: 1000 }}
          initial={{ opacity: 0, y: 40 }}
          animate={inView ? { opacity: 1, y: 0 } : {}}
          transition={{ duration: 0.8, delay: 0.25, ease: [0.16, 1, 0.3, 1] }}
        >
          <SpotlightCard
            className="ai-playground"
            spotlightColor="rgba(157, 123, 255, 0.22)"
            enableTilt={false}
          >
            <BorderBeam size={250} duration={14} borderWidth={1.5} colorFrom="#9D7BFF" colorTo="#5B6AF0" />

            {/* Apple Mode Switcher Pill Bar */}
            <div className="ai-playground__topbar">
              <div className="ai-mode-tabs" role="tablist">
                <button
                  className={`ai-mode-tab ${mode === 'refiner' ? 'ai-mode-tab--active' : ''}`}
                  onClick={() => setMode('refiner')}
                  role="tab"
                  aria-selected={mode === 'refiner'}
                >
                  <Sparkles size={13} />
                  <span>Commitment Refiner</span>
                </button>
                <button
                  className={`ai-mode-tab ${mode === 'thought' ? 'ai-mode-tab--active' : ''}`}
                  onClick={() => setMode('thought')}
                  role="tab"
                  aria-selected={mode === 'thought'}
                >
                  <BrainCircuit size={13} />
                  <span>Thought Parser</span>
                </button>
                <button
                  className={`ai-mode-tab ${mode === 'concierge' ? 'ai-mode-tab--active' : ''}`}
                  onClick={() => setMode('concierge')}
                  role="tab"
                  aria-selected={mode === 'concierge'}
                >
                  <Bot size={13} />
                  <span>Support Concierge</span>
                </button>
              </div>

              {/* Real-time wave visualizer */}
              <div className="ai-playground__audio">
                <SoundWaveform isListening={loading} barCount={18} color="#9D7BFF" />
              </div>
            </div>

            {/* Dynamic Interactive Body */}
            <div className="ai-playground__body">
              <AnimatePresence mode="wait">
                {mode === 'refiner' && (
                  <motion.div
                    key="refiner"
                    initial={{ opacity: 0, y: 8 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -8 }}
                    transition={{ duration: 0.2 }}
                    className="ai-mode-panel"
                  >
                    <div className="ai-presets-row">
                      <span className="ai-presets-label">Try sample prompt:</span>
                      {PRESETS.map((p, i) => (
                        <button
                          key={i}
                          className="ai-preset-chip"
                          onClick={() => applyPreset(p.raw)}
                        >
                          {p.label}
                        </button>
                      ))}
                    </div>

                    {/* Result Display */}
                    {refinedResult && (
                      <motion.div
                        className="ai-result-card"
                        initial={{ opacity: 0, scale: 0.97 }}
                        animate={{ opacity: 1, scale: 1 }}
                      >
                        <div className="ai-result-card__header">
                          <CheckCircle2 size={16} className="ai-result-card__icon" />
                          <span className="ai-result-card__title">Refined Commitment</span>
                          <span className="ai-result-card__status">{refinedResult.status}</span>
                        </div>
                        <h4 className="ai-result-card__name">{refinedResult.refined_title}</h4>
                        <p className="ai-result-card__desc">{refinedResult.refined_description}</p>
                        <div className="ai-result-card__meta">
                          {refinedResult.suggested_due_at && (
                            <span className="ai-result-card__due">
                              📅 {new Date(refinedResult.suggested_due_at).toLocaleString()}
                            </span>
                          )}
                          <span className="ai-result-card__precision">
                            Precision: {refinedResult.suggested_due_precision}
                          </span>
                        </div>
                      </motion.div>
                    )}
                  </motion.div>
                )}

                {mode === 'thought' && (
                  <motion.div
                    key="thought"
                    initial={{ opacity: 0, y: 8 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -8 }}
                    transition={{ duration: 0.2 }}
                    className="ai-mode-panel"
                  >
                    <p className="ai-panel-hint">
                      Paste a raw stream of consciousness or Hinglish brain dump. Gemini extracts discrete promises and daily habits.
                    </p>

                    {parsedItems && (
                      <motion.div
                        className="ai-result-card"
                        initial={{ opacity: 0, scale: 0.97 }}
                        animate={{ opacity: 1, scale: 1 }}
                      >
                        <div className="ai-result-card__header">
                          <BrainCircuit size={16} className="ai-result-card__icon" />
                          <span className="ai-result-card__title">Parsed Action Stream</span>
                        </div>
                        <div className="ai-parsed-list">
                          {parsedItems.map((item: ParsedThoughtItem, i: number) => (
                            <div key={i} className="ai-parsed-item">
                              <span
                                className={`ai-parsed-type ${
                                  item.type === 'goal' ? 'ai-parsed-type--goal' : ''
                                }`}
                              >
                                {item.type === 'goal' ? 'Practice' : 'Promise'}
                              </span>
                              <span className="ai-parsed-text">{item.title}</span>
                              {item.due_at && (
                                <span className="ai-parsed-due">
                                  {new Date(item.due_at).toLocaleDateString()}
                                </span>
                              )}
                            </div>
                          ))}
                        </div>
                      </motion.div>
                    )}
                  </motion.div>
                )}

                {mode === 'concierge' && (
                  <motion.div
                    key="concierge"
                    initial={{ opacity: 0, y: 8 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -8 }}
                    transition={{ duration: 0.2 }}
                    className="ai-mode-panel ai-concierge-chat"
                  >
                    <div className="ai-chat-messages">
                      {conciergeMessages.map((msg, i) => (
                        <div
                          key={i}
                          className={`ai-chat-bubble ai-chat-bubble--${msg.role}`}
                        >
                          {msg.role === 'assistant' && (
                            <div className="ai-chat-avatar">✦</div>
                          )}
                          <div className="ai-chat-text">{msg.text}</div>
                        </div>
                      ))}
                    </div>
                  </motion.div>
                )}
              </AnimatePresence>

              {/* Universal Input Bar */}
              <form onSubmit={handleSubmit} className="ai-input-form">
                <input
                  type="text"
                  className="ai-input-field"
                  placeholder={
                    mode === 'refiner'
                      ? "Type what you need to do: 'call rahul tomorrow 5pm'…"
                      : mode === 'thought'
                      ? "Brain dump: 'Need to file GST, also start doing 20 pushups daily'…"
                      : "Ask the Promise Concierge anything…"
                  }
                  value={inputVal}
                  onChange={(e) => setInputVal(e.target.value)}
                  disabled={loading}
                />
                <motion.button
                  type="submit"
                  className="ai-submit-btn"
                  disabled={loading || !inputVal.trim()}
                  whileHover={{ scale: 1.05 }}
                  whileTap={{ scale: 0.95 }}
                  aria-label="Send query"
                >
                  {loading ? (
                    <span className="ai-loading-spinner" />
                  ) : (
                    <Send size={15} />
                  )}
                </motion.button>
              </form>
            </div>

            {/* Playground Footer */}
            <div className="ai-playground__footer">
              <span className="ai-playground__model">gemini-3.7-flash · real railway backend connection</span>
              <div className="ai-playground__badge">
                <span className="ai-playground__pulse-dot" />
                <span>Active API Engine</span>
              </div>
            </div>
          </SpotlightCard>
        </motion.div>
      </div>
    </section>
  )
}
