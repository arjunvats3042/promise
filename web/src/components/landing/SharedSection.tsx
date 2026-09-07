import { motion, useInView, useScroll, useTransform, useSpring } from 'framer-motion'
import { useRef } from 'react'
import { SpotlightCard } from '../ui/SpotlightCard'
import { TextBlurReveal } from '../ui/TextBlurReveal'
import './SharedSection.css'

const MEMBERS = [
  { name: 'Arjun', avatar: 'A', done: true },
  { name: 'Rahul', avatar: 'R', done: true },
  { name: 'Priya', avatar: 'P', done: false },
  { name: 'Dev', avatar: 'D', done: true },
]

const MESSAGES = [
  { user: 'Rahul', text: 'Done! 25 min run this morning ✓', time: '7:42 AM', mine: false },
  { user: 'You', text: 'Checked in. 30 min today 🎯', time: '8:15 AM', mine: true },
  { user: 'Priya', text: "Missed today, will double tomorrow", time: '9:01 AM', mine: false },
  { user: 'Dev', text: '14 day streak! This group keeps me going', time: '9:30 AM', mine: false },
]

export default function SharedSection() {
  const sectionRef = useRef<HTMLElement>(null)
  const inView = useInView(sectionRef, { once: true, margin: '-80px' })

  // Scroll parallax between copy and interactive chat mockup
  const { scrollYProgress } = useScroll({
    target: sectionRef,
    offset: ['start end', 'end start'],
  })
  const smoothProgress = useSpring(scrollYProgress, { stiffness: 90, damping: 24 })
  const copyY = useTransform(smoothProgress, [0, 1], [25, -25])
  const chatY = useTransform(smoothProgress, [0, 1], [45, -40])

  return (
    <section className="shared-sec" id="shared" ref={sectionRef} aria-labelledby="shared-heading">
      <div className="container">
        <div className="shared-sec__layout">
          {/* LEFT: copy */}
          <motion.div
            className="shared-sec__copy"
            style={{ y: copyY }}
            initial={{ opacity: 0, y: 32 }}
            animate={inView ? { opacity: 1, y: 0 } : {}}
            transition={{ duration: 0.7 }}
          >
            <p className="shared-sec__eyebrow">Shared Goals</p>
            <TextBlurReveal
              text="Accountability is not a solo sport."
              as="h2"
              className="shared-sec__heading"
            />
            <p className="shared-sec__body">
              Convert any personal goal into a shared room.
              Invite up to 10 members. Track collective and individual progress.
              Real-time chat, milestone alerts, and weekly AI summaries — built for people who take each other seriously.
            </p>
            <ul className="shared-sec__list">
              {[
                'Live WebSocket chat with message history',
                'Collective progress bars and individual streaks',
                'Owner can transfer leadership and remove members',
                'AI weekly group reflection every Monday',
              ].map((item, i) => (
                <motion.li
                  key={i}
                  className="shared-sec__list-item"
                  initial={{ opacity: 0, x: -16 }}
                  animate={inView ? { opacity: 1, x: 0 } : {}}
                  transition={{ duration: 0.5, delay: 0.2 + i * 0.08 }}
                >
                  <span className="shared-sec__list-mark" aria-hidden="true">→</span>
                  {item}
                </motion.li>
              ))}
            </ul>
          </motion.div>

          {/* RIGHT: Chat mockup */}
          <motion.div
            className="shared-sec__chat-wrap"
            style={{ y: chatY }}
            initial={{ opacity: 0, x: 40 }}
            animate={inView ? { opacity: 1, x: 0 } : {}}
            transition={{ duration: 0.8, delay: 0.15, ease: [0.16, 1, 0.3, 1] }}
          >
            <SpotlightCard
              className="shared-sec__chat"
              spotlightColor="rgba(16, 185, 129, 0.2)"
              enableTilt={false}
              aria-label="Shared goal chat mockup"
            >
              {/* Header */}
              <div className="shared-sec__chat-header">
                <div className="shared-sec__chat-title">
                  <span className="shared-sec__chat-goal-dot" aria-hidden="true" />
                  <strong>Morning Workout — Shared</strong>
                </div>
                <div className="shared-sec__members" aria-label="Members">
                  {MEMBERS.map((m) => (
                    <div
                      key={m.name}
                      className={`shared-sec__member ${m.done ? 'shared-sec__member--done' : ''}`}
                      title={`${m.name}: ${m.done ? 'checked in' : 'pending'}`}
                      aria-label={`${m.name}: ${m.done ? 'checked in' : 'pending'}`}
                    >
                      {m.avatar}
                    </div>
                  ))}
                </div>
              </div>

              {/* Collective progress */}
              <div className="shared-sec__progress">
                <div className="shared-sec__progress-label">
                  <span>Collective today</span>
                  <span>{MEMBERS.filter(m => m.done).length}/{MEMBERS.length} checked in</span>
                </div>
                <div className="shared-sec__progress-bar">
                  <motion.div
                    className="shared-sec__progress-fill"
                    initial={{ width: 0 }}
                    animate={inView ? { width: `${(MEMBERS.filter(m => m.done).length / MEMBERS.length) * 100}%` } : {}}
                    transition={{ duration: 1, delay: 0.5, ease: [0.16, 1, 0.3, 1] }}
                  />
                </div>
              </div>

              {/* Messages */}
              <div className="shared-sec__messages" aria-label="Chat messages">
                {MESSAGES.map((msg, i) => (
                  <motion.div
                    key={i}
                    className={`shared-sec__msg ${msg.mine ? 'shared-sec__msg--mine' : ''}`}
                    initial={{ opacity: 0, y: 12 }}
                    animate={inView ? { opacity: 1, y: 0 } : {}}
                    transition={{ duration: 0.4, delay: 0.4 + i * 0.1 }}
                  >
                    {!msg.mine && <span className="shared-sec__msg-avatar" aria-hidden="true">{msg.user[0]}</span>}
                    <div className="shared-sec__msg-content">
                      {!msg.mine && <span className="shared-sec__msg-user">{msg.user}</span>}
                      <div className={`shared-sec__msg-bubble ${msg.mine ? 'shared-sec__msg-bubble--mine' : ''}`}>
                        {msg.text}
                      </div>
                      <span className="shared-sec__msg-time">{msg.time}</span>
                    </div>
                  </motion.div>
                ))}
              </div>

              {/* Input */}
              <div className="shared-sec__input-row" aria-hidden="true">
                <input className="shared-sec__input" placeholder="Send a message…" readOnly />
                <button className="shared-sec__send" tabIndex={-1}>
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M22 2L11 13M22 2L15 22 11 13 2 9l20-7z" />
                  </svg>
                </button>
              </div>
            </SpotlightCard>
          </motion.div>
        </div>
      </div>
    </section>
  )
}
