import { useState, useRef } from 'react'
import { motion, useInView, useScroll, useTransform, useSpring } from 'framer-motion'
import { Flame, RotateCcw, Users, CalendarDays } from 'lucide-react'
import { SpotlightCard } from '../ui/SpotlightCard'
import { TextBlurReveal } from '../ui/TextBlurReveal'
import { triggerCelebration } from '../ui/celebrate'
import './GoalsSection.css'

const MONTHS = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec']
const now = new Date()
const CURRENT_MONTH = now.getMonth()
const CURRENT_YEAR = now.getFullYear()
const TODAY = now.getDate()

interface CalDay {
  day: number | null
  status: 'empty' | 'future' | 'today' | 'done' | 'skipped' | 'missed'
}

function generateCalendar(): CalDay[] {
  const days: CalDay[] = []
  const daysInMonth = new Date(CURRENT_YEAR, CURRENT_MONTH + 1, 0).getDate()
  const firstDayOfMonth = new Date(CURRENT_YEAR, CURRENT_MONTH, 1).getDay()
  const pad = (firstDayOfMonth + 6) % 7

  for (let i = 0; i < pad; i++) days.push({ day: null, status: 'empty' })
  for (let d = 1; d <= daysInMonth; d++) {
    const isFuture = d > TODAY
    const isToday = d === TODAY
    const rand = Math.random()
    const status = isFuture
      ? 'future'
      : isToday
      ? 'today'
      : rand > 0.2
      ? 'done'
      : rand > 0.1
      ? 'skipped'
      : 'missed'
    days.push({ day: d, status })
  }
  return days
}

const SAMPLE_PRACTICES = [
  { id: '1', title: 'Deep Work (45 min)', streak: 14, completionRate: 94, icon: '🎯' },
  { id: '2', title: 'Physical Training', streak: 8, completionRate: 88, icon: '⚡' },
  { id: '3', title: 'Daily Reading & Reflection', streak: 21, completionRate: 98, icon: '📖' },
]

export default function GoalsSection() {
  const sectionRef = useRef<HTMLElement>(null)
  const inView = useInView(sectionRef, { once: true, margin: '-80px' })

  // Scroll parallax between left interactive card and right feature list
  const { scrollYProgress } = useScroll({
    target: sectionRef,
    offset: ['start end', 'end start'],
  })
  const smoothProgress = useSpring(scrollYProgress, { stiffness: 90, damping: 24 })
  const leftY = useTransform(smoothProgress, [0, 1], [35, -35])
  const rightY = useTransform(smoothProgress, [0, 1], [15, -15])

  const [activePractice, setActivePractice] = useState(0)
  const [calendar, setCalendar] = useState<CalDay[]>(generateCalendar)

  const practice = SAMPLE_PRACTICES[activePractice]

  function toggleDayStatus(idx: number, e: React.MouseEvent) {
    const dayObj = calendar[idx]
    if (!dayObj.day || dayObj.status === 'future' || dayObj.status === 'empty') return

    const nextStatus = dayObj.status === 'done' ? 'skipped' : 'done'
    const nextCal = [...calendar]
    nextCal[idx] = { ...dayObj, status: nextStatus }
    setCalendar(nextCal)

    if (nextStatus === 'done') {
      const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
      triggerCelebration({
        particleCount: 20,
        spread: 40,
        origin: {
          x: (rect.left + rect.width / 2) / window.innerWidth,
          y: (rect.top + rect.height / 2) / window.innerHeight,
        },
      })
    }
  }

  return (
    <section className="goals-sec" id="goals" ref={sectionRef} aria-labelledby="goals-heading">
      <div className="container">
        <div className="goals-sec__layout">
          {/* LEFT: Calendar Interactive Card */}
          <motion.div
            className="goals-sec__left"
            style={{ y: leftY }}
            initial={{ opacity: 0, x: -40 }}
            animate={inView ? { opacity: 1, x: 0 } : {}}
            transition={{ duration: 0.8, ease: [0.16, 1, 0.3, 1] }}
          >
            <SpotlightCard
              className="goals-cal-card"
              spotlightColor="rgba(245, 158, 11, 0.2)"
            >
              {/* Practice selector tabs */}
              <div className="goals-practice-tabs" role="tablist">
                {SAMPLE_PRACTICES.map((p, i) => (
                  <button
                    key={p.id}
                    role="tab"
                    aria-selected={activePractice === i}
                    className={`goals-practice-tab ${activePractice === i ? 'goals-practice-tab--active' : ''}`}
                    onClick={() => setActivePractice(i)}
                  >
                    <span className="goals-practice-tab__icon">{p.icon}</span>
                    <span className="goals-practice-tab__title">{p.title}</span>
                    <span className="goals-practice-tab__streak">🔥 {p.streak}d</span>
                  </button>
                ))}
              </div>

              {/* Consistency Metric Header */}
              <div className="goals-cal-stats">
                <div className="goals-cal-stats__left">
                  <span className="goals-cal-stats__month">
                    {MONTHS[CURRENT_MONTH]} {CURRENT_YEAR}
                  </span>
                  <span className="goals-cal-stats__sub">
                    Interactive Grid · Tap past days to repair history
                  </span>
                </div>
                <div className="goals-cal-stats__score">
                  <div className="goals-progress-ring">
                    <svg width="44" height="44" viewBox="0 0 44 44">
                      <circle
                        cx="22"
                        cy="22"
                        r="18"
                        fill="none"
                        stroke="rgba(255,255,255,0.08)"
                        strokeWidth="4"
                      />
                      <motion.circle
                        cx="22"
                        cy="22"
                        r="18"
                        fill="none"
                        stroke="#F59E0B"
                        strokeWidth="4"
                        strokeLinecap="round"
                        strokeDasharray={2 * Math.PI * 18}
                        initial={{ strokeDashoffset: 2 * Math.PI * 18 }}
                        animate={
                          inView
                            ? {
                                strokeDashoffset:
                                  2 * Math.PI * 18 * (1 - practice.completionRate / 100),
                              }
                            : {}
                        }
                        transition={{ duration: 1.2, ease: [0.16, 1, 0.3, 1] }}
                        style={{ transformOrigin: 'center', transform: 'rotate(-90deg)' }}
                      />
                    </svg>
                    <span className="goals-progress-num">{practice.completionRate}%</span>
                  </div>
                </div>
              </div>

              {/* Day column headers */}
              <div className="goals-days-header" aria-hidden="true">
                {['M','T','W','T','F','S','S'].map((d, i) => (
                  <span key={i} className="goals-day-name">{d}</span>
                ))}
              </div>

              {/* Calendar Grid */}
              <div className="goals-grid-matrix">
                {calendar.map((cell, idx) => (
                  <motion.button
                    key={idx}
                    className={`goals-matrix-cell goals-matrix-cell--${cell.status}`}
                    onClick={(e) => toggleDayStatus(idx, e)}
                    whileHover={cell.day ? { scale: 1.2, zIndex: 10 } : {}}
                    whileTap={cell.day ? { scale: 0.9 } : {}}
                    disabled={!cell.day || cell.status === 'future'}
                    aria-label={cell.day ? `Day ${cell.day}: ${cell.status}` : undefined}
                  >
                    {cell.day && <span className="goals-matrix-num">{cell.day}</span>}
                  </motion.button>
                ))}
              </div>

              {/* Legend & Active Streak Footer */}
              <div className="goals-cal-footer">
                <div className="goals-legend">
                  <span className="goals-legend-item">
                    <span className="goals-legend-dot goals-legend-dot--done" /> Kept
                  </span>
                  <span className="goals-legend-item">
                    <span className="goals-legend-dot goals-legend-dot--skipped" /> Excused
                  </span>
                  <span className="goals-legend-item">
                    <span className="goals-legend-dot goals-legend-dot--missed" /> Slipped
                  </span>
                </div>
                <div className="goals-streak-pill">
                  <Flame size={13} className="goals-flame-icon" />
                  <span>{practice.streak}-Day Current Streak</span>
                </div>
              </div>
            </SpotlightCard>
          </motion.div>

          {/* RIGHT: Copy & Disciplines */}
          <motion.div
            className="goals-sec__right"
            style={{ y: rightY }}
            initial={{ opacity: 0, y: 32 }}
            animate={inView ? { opacity: 1, y: 0 } : {}}
            transition={{ duration: 0.7, delay: 0.15, ease: [0.16, 1, 0.3, 1] }}
          >
            <p className="goals-sec__eyebrow">Goals & Practices</p>
            <TextBlurReveal
              text="Build the practice. Not just the streak."
              as="h2"
              className="goals-sec__heading"
            />
            <p className="goals-sec__body">
              Traditional habit trackers shame you when life happens. Promise is designed
              around psychological safety: flexible cadences, retroactive history repair, and
              the understanding that integrity is a lifelong practice, not a fragile number.
            </p>

            <ul className="goals-feature-list">
              {[
                {
                  icon: <RotateCcw size={16} />,
                  title: 'Flexible Recurrence',
                  desc: 'Daily, specific weekdays, or N times per week. Fits your real cadence.',
                },
                {
                  icon: <CalendarDays size={16} />,
                  title: 'Retroactive History Repair',
                  desc: 'Forgot to log last night? Repair your check-in with honesty and keep moving.',
                },
                {
                  icon: <Users size={16} />,
                  title: 'Shared Accountability Rooms',
                  desc: 'Invite friends or colleagues to share the journey in quiet circles.',
                },
              ].map((item, i) => (
                <motion.li
                  key={i}
                  className="goals-feature-item"
                  initial={{ opacity: 0, x: 20 }}
                  animate={inView ? { opacity: 1, x: 0 } : {}}
                  transition={{ duration: 0.5, delay: 0.3 + i * 0.1 }}
                >
                  <div className="goals-feature-icon">{item.icon}</div>
                  <div>
                    <h4 className="goals-feature-title">{item.title}</h4>
                    <p className="goals-feature-desc">{item.desc}</p>
                  </div>
                </motion.li>
              ))}
            </ul>
          </motion.div>
        </div>
      </div>
    </section>
  )
}
