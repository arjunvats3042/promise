import { useRef } from 'react'
import { motion, useScroll, useTransform, useSpring } from 'framer-motion'
import { Sparkles, Clock, CheckCircle2, ShieldAlert } from 'lucide-react'
import { BorderBeam } from '../ui/BorderBeam'
import { SoundWaveform } from '../ui/SoundWaveform'
import './PinnedLifecycleStage.css'

const STAGES = [
  {
    step: '01',
    phase: 'PHASE 01 / FORMULATION',
    title: 'Thoughts crystallized into unbreakable words.',
    desc: 'Speak naturally in English, Hindi, or Hinglish. Gemini 3.7 parses unstructured intent, eliminates ambiguity, and extracts the exact deliverable.',
    accent: '#5B6AF0',
    tag: 'Advisory AI Parsing',
  },
  {
    step: '02',
    phase: 'PHASE 02 / TEMPORAL LOCK',
    title: 'Time is not fuzzy. Neither is your integrity.',
    desc: 'No "someday" or vague to-do lists. Every promise is bound to minute precision. Rescheduling requires conscious accountability, not effortless procrastination.',
    accent: '#F59E0B',
    tag: 'Minute Precision Timing',
  },
  {
    step: '03',
    phase: 'PHASE 03 / QUIET FULFILLMENT',
    title: 'Kept without noise. Reputation built on reality.',
    desc: 'Zero social vanity feeds. Zero algorithm manipulation. Just the quiet, immovable power of doing exactly what you said you would do.',
    accent: '#10B981',
    tag: 'Immutable Reputation',
  },
]

export function PinnedLifecycleStage() {
  const containerRef = useRef<HTMLDivElement>(null)

  const { scrollYProgress } = useScroll({
    target: containerRef,
    offset: ['start start', 'end end'],
  })

  const smooth = useSpring(scrollYProgress, { stiffness: 100, damping: 25 })

  // Phase opacity & transform interpolations (3 stages across 0..1)
  // Stage 1: active 0.0 to 0.33
  const op1 = useTransform(smooth, [0, 0.05, 0.28, 0.35], [0, 1, 1, 0])
  const y1 = useTransform(smooth, [0, 0.05, 0.28, 0.35], [30, 0, 0, -30])
  const scale1 = useTransform(smooth, [0, 0.05, 0.28, 0.35], [0.95, 1, 1, 0.95])

  // Stage 2: active 0.33 to 0.66
  const op2 = useTransform(smooth, [0.32, 0.38, 0.62, 0.68], [0, 1, 1, 0])
  const y2 = useTransform(smooth, [0.32, 0.38, 0.62, 0.68], [30, 0, 0, -30])
  const scale2 = useTransform(smooth, [0.32, 0.38, 0.62, 0.68], [0.95, 1, 1, 0.95])

  // Stage 3: active 0.66 to 1.00
  const op3 = useTransform(smooth, [0.65, 0.72, 0.95, 1], [0, 1, 1, 1])
  const y3 = useTransform(smooth, [0.65, 0.72, 0.95, 1], [30, 0, 0, 0])
  const scale3 = useTransform(smooth, [0.65, 0.72, 0.95, 1], [0.95, 1, 1, 1])

  // Timeline scrub bar fill (0% -> 100%)
  const timelineHeight = useTransform(smooth, [0, 1], ['0%', '100%'])

  // Dynamic ambient aura color shift
  const auraColor = useTransform(
    smooth,
    [0, 0.33, 0.66, 1],
    [
      'radial-gradient(circle, rgba(91, 106, 240, 0.25) 0%, transparent 70%)',
      'radial-gradient(circle, rgba(245, 158, 11, 0.25) 0%, transparent 70%)',
      'radial-gradient(circle, rgba(16, 185, 129, 0.25) 0%, transparent 70%)',
      'radial-gradient(circle, rgba(16, 185, 129, 0.3) 0%, transparent 70%)',
    ]
  )

  return (
    <div className="pinned-stage" ref={containerRef} aria-label="Promise Lifecycle Keynote">
      <div className="pinned-stage__sticky">
        {/* Ambient Color Glow reacting to stage */}
        <motion.div
          className="pinned-stage__aura"
          style={{ background: auraColor }}
          aria-hidden="true"
        />

        <div className="container pinned-stage__container">
          {/* Header & Sub-bar */}
          <div className="pinned-stage__intro">
            <span className="pinned-stage__supertag">THE INTEGRITY ENGINE</span>
            <h2 className="pinned-stage__main-title">
              How a Promise is Honored.
            </h2>
            <p className="pinned-stage__scrub-hint">
              <span className="pinned-stage__scrub-dot" />
              Scroll down to scrub the lifecycle
            </p>
          </div>

          <div className="pinned-stage__split">
            {/* LEFT: Interactive Progress Scrubber */}
            <div className="pinned-timeline">
              <div className="pinned-timeline__track">
                <motion.div
                  className="pinned-timeline__fill"
                  style={{ height: timelineHeight }}
                />
              </div>

              <div className="pinned-timeline__nodes">
                {STAGES.map((s) => (
                  <div key={s.step} className="pinned-timeline__node">
                    <div className="pinned-timeline__bullet">
                      <span>{s.step}</span>
                    </div>
                    <div className="pinned-timeline__meta">
                      <span className="pinned-timeline__label">{s.phase}</span>
                      <span className="pinned-timeline__sub">{s.tag}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* RIGHT: Stacked 3D Keynote Showcase Cards */}
            <div className="pinned-cards-viewport">
              {/* STAGE 1 CARD */}
              <motion.div
                className="pinned-card pinned-card--stage1"
                style={{ opacity: op1, y: y1, scale: scale1 }}
              >
                <div className="pinned-card__glass">
                  <BorderBeam size={220} duration={10} colorFrom="#5B6AF0" colorTo="#9D7BFF" />
                  <div className="pinned-card__header">
                    <span className="pinned-card__step-pill">01 / FORMULATION</span>
                    <div className="pinned-card__gemini-pill">
                      <Sparkles size={13} />
                      <span>Gemini 3.7 Active</span>
                    </div>
                  </div>

                  <h3 className="pinned-card__heading">{STAGES[0].title}</h3>
                  <p className="pinned-card__desc">{STAGES[0].desc}</p>

                  <div className="pinned-card__interactive-demo">
                    <div className="pinned-voice-input">
                      <div className="pinned-voice-input__top">
                        <span className="pinned-voice-input__pulse" />
                        <span className="pinned-voice-input__status">Speech-to-Promise Live Audio</span>
                      </div>
                      <div className="pinned-voice-input__wave">
                        <SoundWaveform isListening={true} barCount={32} color="#5B6AF0" />
                      </div>
                      <div className="pinned-voice-input__transcription">
                        <span className="pinned-voice-input__quote">
                          "Doctor appointment kal shaam 6 baje confirm karna hai"
                        </span>
                        <div className="pinned-voice-input__result">
                          <CheckCircle2 size={14} color="#10B981" />
                          <span>Structured as: <strong>Doctor Appointment Confirmation</strong></span>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </motion.div>

              {/* STAGE 2 CARD */}
              <motion.div
                className="pinned-card pinned-card--stage2"
                style={{ opacity: op2, y: y2, scale: scale2 }}
              >
                <div className="pinned-card__glass">
                  <BorderBeam size={220} duration={10} colorFrom="#F59E0B" colorTo="#EF4444" />
                  <div className="pinned-card__header">
                    <span className="pinned-card__step-pill pinned-card__step-pill--amber">
                      02 / TEMPORAL LOCK
                    </span>
                    <div className="pinned-card__badge-amber">
                      <Clock size={13} />
                      <span>Exact Minute Precision</span>
                    </div>
                  </div>

                  <h3 className="pinned-card__heading">{STAGES[1].title}</h3>
                  <p className="pinned-card__desc">{STAGES[1].desc}</p>

                  <div className="pinned-card__interactive-demo">
                    <div className="pinned-time-demo">
                      <div className="pinned-time-demo__dial">
                        <span className="pinned-time-demo__time">6:00 PM</span>
                        <span className="pinned-time-demo__date">Tomorrow · Minute Accuracy</span>
                      </div>
                      <div className="pinned-time-demo__lock-row">
                        <div className="pinned-time-demo__lock-badge">
                          <ShieldAlert size={14} />
                          <span>Accountability Safeguard</span>
                        </div>
                        <span className="pinned-time-demo__lock-text">
                          Rescheduling requires documented rationale
                        </span>
                      </div>
                    </div>
                  </div>
                </div>
              </motion.div>

              {/* STAGE 3 CARD */}
              <motion.div
                className="pinned-card pinned-card--stage3"
                style={{ opacity: op3, y: y3, scale: scale3 }}
              >
                <div className="pinned-card__glass">
                  <BorderBeam size={220} duration={10} colorFrom="#10B981" colorTo="#34D399" />
                  <div className="pinned-card__header">
                    <span className="pinned-card__step-pill pinned-card__step-pill--green">
                      03 / FULFILLMENT
                    </span>
                    <div className="pinned-card__badge-green">
                      <CheckCircle2 size={13} />
                      <span>Promise Honored</span>
                    </div>
                  </div>

                  <h3 className="pinned-card__heading">{STAGES[2].title}</h3>
                  <p className="pinned-card__desc">{STAGES[2].desc}</p>

                  <div className="pinned-card__interactive-demo">
                    <div className="pinned-triumph-demo">
                      <div className="pinned-triumph-demo__score-card">
                        <div className="pinned-triumph-demo__check-circle">
                          <CheckCircle2 size={32} color="#10B981" />
                        </div>
                        <div className="pinned-triumph-demo__meta">
                          <span className="pinned-triumph-demo__status">100% Integrity Retained</span>
                          <span className="pinned-triumph-demo__sub">Promise fulfilled on time without excuse</span>
                        </div>
                        <div className="pinned-triumph-demo__streak-pill">
                          🔥 +1 Streak
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </motion.div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
