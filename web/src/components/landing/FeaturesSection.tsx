import { useState, useRef } from 'react'
import { motion, useInView, useScroll, useTransform, useSpring } from 'framer-motion'
import { Sparkles, Flame, ChevronRight } from 'lucide-react'
import { SpotlightCard } from '../ui/SpotlightCard'
import { SoundWaveform } from '../ui/SoundWaveform'
import { TextBlurReveal } from '../ui/TextBlurReveal'
import { triggerCelebration } from '../ui/celebrate'
import './FeaturesSection.css'

const COMMITMENT_STATES = [
  { state: 'PENDING', label: 'Pending', color: '#5B6AF0', desc: 'Awaiting execution' },
  { state: 'SNOOZED', label: 'Snoozed', color: '#F59E0B', desc: 'Rescheduled with reason' },
  { state: 'WAITING', label: 'Waiting', color: '#9D7BFF', desc: 'Blocked on external input' },
  { state: 'COMPLETED', label: 'Completed', color: '#10B981', desc: 'Fulfilled with integrity' },
]

export default function FeaturesSection() {
  const sectionRef = useRef<HTMLElement>(null)
  const headRef = useRef<HTMLDivElement>(null)
  const headInView = useInView(headRef, { once: true, margin: '-60px' })

  // Scroll parallax across Bento columns
  const { scrollYProgress } = useScroll({
    target: sectionRef,
    offset: ['start end', 'end start'],
  })

  const smoothProgress = useSpring(scrollYProgress, { stiffness: 90, damping: 22 })
  const yCard1 = useTransform(smoothProgress, [0, 1], [35, -35])
  const yCard2 = useTransform(smoothProgress, [0, 1], [65, -55])
  const yCard3 = useTransform(smoothProgress, [0, 1], [45, -45])
  const yCard4 = useTransform(smoothProgress, [0, 1], [75, -35])

  // Interactive Card 1: 5-state machine cycle
  const [stateIndex, setStateIndex] = useState(0)

  // Interactive Card 2: 7-day streak tracker
  const [activeDays, setActiveDays] = useState<boolean[]>([true, true, true, true, true, false, true])
  const [streakCount, setStreakCount] = useState(14)

  // Interactive Card 3: Shared room presence
  const [sharedMessages] = useState([
    { user: 'Maya', text: 'Completed morning review', time: '9:12 AM' },
    { user: 'Liam', text: 'Checked in on physical training 🔥', time: '9:38 AM' },
  ])

  // Interactive Card 4: Gemini wave & prompt
  const [isAudioListening, setIsAudioListening] = useState(true)

  function cycleCommitmentState(e: React.MouseEvent) {
    const next = (stateIndex + 1) % COMMITMENT_STATES.length
    setStateIndex(next)
    if (COMMITMENT_STATES[next].state === 'COMPLETED') {
      const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
      triggerCelebration({
        particleCount: 30,
        spread: 50,
        origin: {
          x: (rect.left + rect.width / 2) / window.innerWidth,
          y: (rect.top + rect.height / 2) / window.innerHeight,
        },
      })
    }
  }

  function toggleDay(idx: number, e: React.MouseEvent) {
    const next = [...activeDays]
    next[idx] = !next[idx]
    setActiveDays(next)
    if (next[idx]) {
      setStreakCount((s) => s + 1)
      const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
      triggerCelebration({
        particleCount: 20,
        spread: 40,
        origin: {
          x: (rect.left + rect.width / 2) / window.innerWidth,
          y: (rect.top + rect.height / 2) / window.innerHeight,
        },
      })
    } else {
      setStreakCount((s) => Math.max(0, s - 1))
    }
  }

  const currentState = COMMITMENT_STATES[stateIndex]

  return (
    <section className="feat" id="features" ref={sectionRef} aria-labelledby="feat-heading">
      <div className="container">
        {/* Header */}
        <motion.div
          className="feat__header"
          ref={headRef}
          initial={{ opacity: 0, y: 32 }}
          animate={headInView ? { opacity: 1, y: 0 } : {}}
          transition={{ duration: 0.7, ease: [0.16, 1, 0.3, 1] }}
        >
          <p className="feat__eyebrow">The Architecture of Integrity</p>
          <TextBlurReveal
            text="Four disciplines. Crafted for quiet focus."
            as="h2"
            className="feat__heading"
          />
          <p className="feat__sub">
            Every screen and interaction is engineered around psychological follow-through.
            Zero feeds, zero likes, zero vanity metrics.
          </p>
        </motion.div>

        {/* Apple Bento Grid */}
        <div className="bento-grid">
          {/* Bento 1: Discrete Commitments */}
          <motion.div style={{ y: yCard1 }} className="bento-col bento-col--commitments">
            <SpotlightCard
              className="bento-card bento-card--commitments"
              spotlightColor="rgba(91, 106, 240, 0.2)"
            >
            <div className="bento-card__inner">
              <div className="bento-card__header">
                <span className="bento-card__tag">DISCIPLINE 01</span>
                <span className="bento-card__badge-sub">State Machine</span>
              </div>

              <h3 className="bento-card__title">Discrete Commitments</h3>
              <p className="bento-card__desc">
                Not a messy to-do list. Every commitment is a sacred promise with an explicit
                lifecycle, time precision, and accountability transitions.
              </p>

              {/* Interactive State Machine Demo */}
              <div className="bento-demo-state">
                <div className="bento-demo-state__card">
                  <div className="bento-demo-state__top">
                    <span className="bento-demo-state__title">Finalize sprint release notes</span>
                    <span
                      className="bento-demo-state__badge"
                      style={{
                        backgroundColor: `${currentState.color}22`,
                        color: currentState.color,
                        borderColor: `${currentState.color}55`,
                      }}
                    >
                      {currentState.label}
                    </span>
                  </div>
                  <p className="bento-demo-state__desc">{currentState.desc}</p>

                  <div className="bento-demo-state__footer">
                    <button
                      className="bento-demo-state__cycle-btn"
                      onClick={cycleCommitmentState}
                      style={{ borderColor: currentState.color }}
                    >
                      <span>Cycle State Machine</span>
                      <ChevronRight size={14} />
                    </button>
                    <span className="bento-demo-hint">Tap to test transition</span>
                  </div>
                </div>
              </div>
            </div>
          </SpotlightCard>
        </motion.div>

        {/* Bento 2: Daily Practices & Streaks */}
        <motion.div style={{ y: yCard2 }} className="bento-col bento-col--streaks">
          <SpotlightCard
            className="bento-card bento-card--streaks"
            spotlightColor="rgba(245, 158, 11, 0.2)"
          >
            <div className="bento-card__inner">
              <div className="bento-card__header">
                <span className="bento-card__tag bento-card__tag--amber">DISCIPLINE 02</span>
                <div className="bento-card__streak-pill">
                  <Flame size={12} className="bento-card__flame" />
                  <span>{streakCount}d</span>
                </div>
              </div>

              <h3 className="bento-card__title">Daily Practices</h3>
              <p className="bento-card__desc">
                Habits built on quiet consistency. Tap days to log or repair history without
                fear of broken chains.
              </p>

              {/* Interactive 7-day pill row */}
              <div className="bento-days-row">
                {['M', 'T', 'W', 'T', 'F', 'S', 'S'].map((day, idx) => (
                  <motion.button
                    key={idx}
                    className={`bento-day-btn ${activeDays[idx] ? 'bento-day-btn--active' : ''}`}
                    onClick={(e) => toggleDay(idx, e)}
                    whileHover={{ scale: 1.1 }}
                    whileTap={{ scale: 0.9 }}
                    aria-label={`Toggle ${day}`}
                  >
                    <span className="bento-day-btn__label">{day}</span>
                    <div className="bento-day-btn__dot" />
                  </motion.button>
                ))}
              </div>
              <span className="bento-demo-hint" style={{ marginTop: '8px' }}>
                Tap any weekday to toggle check-in
              </span>
            </div>
          </SpotlightCard>
        </motion.div>

        {/* Bento 3: Shared Accountability */}
        <motion.div style={{ y: yCard3 }} className="bento-col bento-col--shared">
          <SpotlightCard
            className="bento-card bento-card--shared"
            spotlightColor="rgba(16, 185, 129, 0.2)"
          >
            <div className="bento-card__inner">
              <div className="bento-card__header">
                <span className="bento-card__tag bento-card__tag--green">DISCIPLINE 03</span>
                <div className="bento-card__live-pill">
                  <span className="bento-card__live-dot" />
                  <span>2 Online</span>
                </div>
              </div>

              <h3 className="bento-card__title">Shared Accountability</h3>
              <p className="bento-card__desc">
                Small, intimate circles where progress is transparent and quiet follow-through is
                celebrated in real-time.
              </p>

              {/* Live room activity preview */}
              <div className="bento-shared-activity">
                {sharedMessages.map((msg, i) => (
                  <div key={i} className="bento-shared-item">
                    <div className="bento-shared-avatar">
                      {msg.user[0]}
                    </div>
                    <div className="bento-shared-text">
                      <span className="bento-shared-name">{msg.user}</span>
                      <span className="bento-shared-content">{msg.text}</span>
                    </div>
                    <span className="bento-shared-time">{msg.time}</span>
                  </div>
                ))}
              </div>
            </div>
          </SpotlightCard>
        </motion.div>

        {/* Bento 4: Gemini Advisory Engine */}
        <motion.div style={{ y: yCard4 }} className="bento-col bento-col--gemini">
          <SpotlightCard
            className="bento-card bento-card--gemini"
            spotlightColor="rgba(157, 123, 255, 0.2)"
          >
            <div className="bento-card__inner">
              <div className="bento-card__header">
                <span className="bento-card__tag bento-card__tag--purple">DISCIPLINE 04</span>
                <div className="bento-card__gemini-badge">
                  <Sparkles size={12} />
                  <span>Gemini 3.7 Advisory</span>
                </div>
              </div>

              <h3 className="bento-card__title">Advisory AI Concierge</h3>
              <p className="bento-card__desc">
                Advisory, never prescriptive. Parses unstructured thoughts and Hinglish notes
                into crisp, structured commitments with precision timing.
              </p>

              {/* Live Waveform & Prompt Demo */}
              <div className="bento-gemini-demo">
                <div className="bento-gemini-demo__waveform-box">
                  <div className="bento-gemini-demo__waveform-header">
                    <span className="bento-gemini-demo__status">
                      <span className="bento-gemini-demo__pulse" />
                      Thought Parser Audio Frequency
                    </span>
                    <button
                      className="bento-gemini-demo__toggle"
                      onClick={() => setIsAudioListening(!isAudioListening)}
                    >
                      {isAudioListening ? 'Pause Wave' : 'Resume Wave'}
                    </button>
                  </div>
                  <div className="bento-gemini-demo__waveform-render">
                    <SoundWaveform isListening={isAudioListening} barCount={28} color="#9D7BFF" />
                  </div>
                </div>

                <div className="bento-gemini-demo__quote">
                  <span className="bento-gemini-demo__raw">Input: "Doctor appointment kal shaam 6 baje"</span>
                  <span className="bento-gemini-demo__arrow">→</span>
                  <span className="bento-gemini-demo__refined">
                    Refined: <strong>Doctor Appointment</strong> · Tomorrow, 6:00 PM (Minute Precision)
                  </span>
                </div>
              </div>
            </div>
          </SpotlightCard>
        </motion.div>
      </div>
      </div>
    </section>
  )
}
