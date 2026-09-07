import { useState, useRef, useCallback } from 'react'
import { motion, useMotionValue, useSpring, useTransform, AnimatePresence, type MotionValue } from 'framer-motion'
import { Sparkles, Flame, Check, ShieldCheck } from 'lucide-react'
import { triggerCelebration } from '../ui/celebrate'
import { BorderBeam } from '../ui/BorderBeam'
import './PhoneMockup.css'

interface CommitmentItem {
  id: number
  title: string
  done: boolean
  overdue: boolean
}

interface GoalItem {
  id: number
  name: string
  streak: number
  done: boolean
}

const INITIAL_COMMITMENTS: CommitmentItem[] = [
  { id: 1, title: 'Review sprint architecture', done: true, overdue: false },
  { id: 2, title: 'Deploy Python API to Railway', done: false, overdue: false },
  { id: 3, title: 'Check quarterly tax receipts', done: false, overdue: true },
]

const INITIAL_GOALS: GoalItem[] = [
  { id: 1, name: 'Deep focus (45 min)', streak: 14, done: true },
  { id: 2, name: 'Morning physical training', streak: 8, done: false },
]

interface PhoneMockupProps {
  scrollProgress?: MotionValue<number>
}

export default function PhoneMockup({ scrollProgress }: PhoneMockupProps) {
  const [commitments, setCommitments] = useState(INITIAL_COMMITMENTS)
  const [goals, setGoals] = useState(INITIAL_GOALS)
  const [activeTab, setActiveTab] = useState<'today' | 'commitments' | 'goals'>('today')

  // Scroll orbit transforms for satellite HUDs
  const fallbackProgress = useMotionValue(0)
  const progress = scrollProgress || fallbackProgress

  const hud1X = useTransform(progress, [0, 1], [0, 55])
  const hud1Y = useTransform(progress, [0, 1], [0, -65])
  const hud1Rotate = useTransform(progress, [0, 1], [0, 8])
  const hud1Scale = useTransform(progress, [0, 1], [1, 1.08])

  const hud2X = useTransform(progress, [0, 1], [0, -60])
  const hud2Y = useTransform(progress, [0, 1], [0, 55])
  const hud2Rotate = useTransform(progress, [0, 1], [0, -8])
  const hud2Scale = useTransform(progress, [0, 1], [1, 1.1])

  const hud3X = useTransform(progress, [0, 1], [0, 65])
  const hud3Y = useTransform(progress, [0, 1], [0, 25])
  const hud3Rotate = useTransform(progress, [0, 1], [0, 5])
  const hud3Scale = useTransform(progress, [0, 1], [1, 1.06])

  // 3D tilt tracking
  const phoneRef = useRef<HTMLDivElement>(null)
  const mouseX = useMotionValue(0)
  const mouseY = useMotionValue(0)

  const springConfig = { stiffness: 220, damping: 20 }
  const smoothX = useSpring(mouseX, springConfig)
  const smoothY = useSpring(mouseY, springConfig)

  const rotateX = useTransform(smoothY, [-0.5, 0.5], [12, -12])
  const rotateY = useTransform(smoothX, [-0.5, 0.5], [-12, 12])

  const handleMouseMove = useCallback((e: React.MouseEvent) => {
    if (!phoneRef.current) return
    const rect = phoneRef.current.getBoundingClientRect()
    const normX = (e.clientX - rect.left) / rect.width - 0.5
    const normY = (e.clientY - rect.top) / rect.height - 0.5
    mouseX.set(normX)
    mouseY.set(normY)
  }, [mouseX, mouseY])

  const handleMouseLeave = useCallback(() => {
    mouseX.set(0)
    mouseY.set(0)
  }, [mouseX, mouseY])

  function toggleCommitment(id: number, e: React.MouseEvent) {
    const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
    const originX = (rect.left + rect.width / 2) / window.innerWidth
    const originY = (rect.top + rect.height / 2) / window.innerHeight

    setCommitments((prev) =>
      prev.map((c) => {
        if (c.id === id) {
          const nextDone = !c.done
          if (nextDone) {
            triggerCelebration({
              particleCount: 28,
              spread: 45,
              origin: { x: originX, y: originY },
            })
          }
          return { ...c, done: nextDone }
        }
        return c
      })
    )
  }

  function toggleGoal(id: number, e: React.MouseEvent) {
    const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
    const originX = (rect.left + rect.width / 2) / window.innerWidth
    const originY = (rect.top + rect.height / 2) / window.innerHeight

    setGoals((prev) =>
      prev.map((g) => {
        if (g.id === id) {
          const nextDone = !g.done
          if (nextDone) {
            triggerCelebration({
              particleCount: 32,
              spread: 50,
              origin: { x: originX, y: originY },
            })
          }
          return {
            ...g,
            done: nextDone,
            streak: nextDone ? g.streak + 1 : Math.max(0, g.streak - 1),
          }
        }
        return g
      })
    )
  }

  return (
    <div
      className="phone-container"
      onMouseMove={handleMouseMove}
      onMouseLeave={handleMouseLeave}
    >
      {/* Floating HUD Widget 1 (Top Right) */}
      <motion.div
        className="phone-hud phone-hud--top-right"
        style={{ x: hud1X, y: hud1Y, rotate: hud1Rotate, scale: hud1Scale }}
        whileHover={{ scale: 1.05 }}
      >
        <div className="phone-hud__icon phone-hud__icon--sparkles">
          <Sparkles size={14} />
        </div>
        <div className="phone-hud__text">
          <span className="phone-hud__title">Gemini 3.7 Advisory</span>
          <span className="phone-hud__sub">Refining promises live</span>
        </div>
      </motion.div>

      {/* Floating HUD Widget 2 (Bottom Left) */}
      <motion.div
        className="phone-hud phone-hud--bottom-left"
        style={{ x: hud2X, y: hud2Y, rotate: hud2Rotate, scale: hud2Scale }}
        whileHover={{ scale: 1.05 }}
      >
        <div className="phone-hud__icon phone-hud__icon--flame">
          <Flame size={14} />
        </div>
        <div className="phone-hud__text">
          <span className="phone-hud__title">14-Day Streak</span>
          <span className="phone-hud__sub">100% completion rate</span>
        </div>
      </motion.div>

      {/* Floating HUD Widget 3 (Bottom Right) */}
      <motion.div
        className="phone-hud phone-hud--mid-right"
        style={{ x: hud3X, y: hud3Y, rotate: hud3Rotate, scale: hud3Scale }}
        whileHover={{ scale: 1.05 }}
      >
        <div className="phone-hud__icon phone-hud__icon--shield">
          <ShieldCheck size={14} />
        </div>
        <div className="phone-hud__text">
          <span className="phone-hud__title">Quiet Integrity</span>
          <span className="phone-hud__sub">No spam · No feed</span>
        </div>
      </motion.div>

      {/* 3D Phone Shell */}
      <motion.div
        ref={phoneRef}
        className="phone"
        style={{
          rotateX,
          rotateY,
          transformStyle: 'preserve-3d',
        }}
        whileHover={{ scale: 1.02 }}
        transition={{ duration: 0.3 }}
      >
        <div className="phone__shell">
          {/* Luminous perimeter border beam */}
          <BorderBeam size={180} duration={8} borderWidth={1.5} colorFrom="#5B6AF0" colorTo="#3DAA6E" />

          {/* Dynamic Island */}
          <motion.div
            className="phone__island"
            whileHover={{ scaleX: 1.1, scaleY: 1.05 }}
            transition={{ type: 'spring', stiffness: 400, damping: 20 }}
          >
            <div className="phone__island-camera" />
            <div className="phone__island-sensor" />
          </motion.div>

          {/* Screen Content */}
          <div className="phone__screen">
            {/* Screen reflection sheen */}
            <div className="phone__specular-sheen" />

            {/* Status Bar */}
            <div className="phone__status-bar">
              <span className="phone__time">9:41</span>
              <div className="phone__status-icons">
                <SignalIcon />
                <WifiIcon />
                <BatteryIcon />
              </div>
            </div>

            {/* App Header */}
            <div className="phone__app-header">
              <div>
                <p className="phone__header-greeting">Good morning, Arjun</p>
                <h3 className="phone__header-title">Today</h3>
              </div>
              <motion.div
                className="phone__avatar"
                whileTap={{ scale: 0.9 }}
              >
                A
              </motion.div>
            </div>

            {/* Daily Motivation banner */}
            <motion.div
              className="phone__motivation"
              whileHover={{ scale: 1.02 }}
              transition={{ type: 'spring', stiffness: 350, damping: 25 }}
            >
              <span className="phone__motivation-dot" />
              <p>"Do what you said you would do. Nothing less."</p>
            </motion.div>

            {/* Tap interactive hint */}
            <div className="phone__interactive-hint">
              <span>✦ Interactive preview · Tap items below</span>
            </div>

            {/* Section: Commitments */}
            <div className="phone__section-header">
              <span className="phone__section-label">COMMITMENTS</span>
              <span className="phone__section-count">
                {commitments.filter((c) => c.done).length}/{commitments.length}
              </span>
            </div>

            <div className="phone__list">
              <AnimatePresence>
                {commitments.map((c) => (
                  <motion.div
                    key={c.id}
                    className={`phone__commitment ${c.done ? 'phone__commitment--completed' : ''} ${
                      c.overdue && !c.done ? 'phone__commitment--overdue' : ''
                    }`}
                    onClick={(e) => toggleCommitment(c.id, e)}
                    whileHover={{ x: 2 }}
                    whileTap={{ scale: 0.98 }}
                    layout
                  >
                    <motion.div
                      className={`phone__check ${c.done ? 'phone__check--done' : ''} ${
                        c.overdue && !c.done ? 'phone__check--overdue' : ''
                      }`}
                      animate={c.done ? { scale: [1, 1.25, 1] } : { scale: 1 }}
                      transition={{ duration: 0.25 }}
                    >
                      {c.done && <Check size={11} strokeWidth={3} />}
                    </motion.div>
                    <span
                      className={`phone__commitment-title ${
                        c.done ? 'phone__commitment-title--done' : ''
                      }`}
                    >
                      {c.title}
                    </span>
                    {c.overdue && !c.done && (
                      <span className="phone__overdue-badge">Overdue</span>
                    )}
                  </motion.div>
                ))}
              </AnimatePresence>
            </div>

            <div className="phone__divider" />

            {/* Section: Goals / Practices */}
            <div className="phone__section-header">
              <span className="phone__section-label">PRACTICES</span>
              <span className="phone__section-count">Streak check-in</span>
            </div>

            <div className="phone__list">
              {goals.map((g) => (
                <motion.div
                  key={g.id}
                  className="phone__goal"
                  whileHover={{ x: 2 }}
                  layout
                >
                  <div className="phone__goal-info">
                    <span className="phone__goal-name">{g.name}</span>
                    <span className="phone__goal-streak">
                      <Flame size={10} className="phone__flame-icon" /> {g.streak} day streak
                    </span>
                  </div>
                  <motion.button
                    className={`phone__checkin-btn ${
                      g.done ? 'phone__checkin-btn--done' : ''
                    }`}
                    onClick={(e) => toggleGoal(g.id, e)}
                    whileHover={{ scale: 1.08 }}
                    whileTap={{ scale: 0.92 }}
                    aria-label={`Check in ${g.name}`}
                  >
                    {g.done ? <Check size={13} strokeWidth={3} /> : 'Check in'}
                  </motion.button>
                </motion.div>
              ))}
            </div>

            {/* Bottom Nav Dock */}
            <div className="phone__bottom-nav">
              <NavIcon
                label="Today"
                active={activeTab === 'today'}
                onClick={() => setActiveTab('today')}
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M3 9l9-7 9 7v11a2 2 0 01-2 2H5a2 2 0 01-2-2z" />
                  <polyline points="9,22 9,12 15,12 15,22" />
                </svg>
              </NavIcon>
              <NavIcon
                label="Promises"
                active={activeTab === 'commitments'}
                onClick={() => setActiveTab('commitments')}
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M9 11l3 3L22 4" />
                  <path d="M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11" />
                </svg>
              </NavIcon>
              <NavIcon
                label="Goals"
                active={activeTab === 'goals'}
                onClick={() => setActiveTab('goals')}
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <circle cx="12" cy="12" r="10" />
                  <circle cx="12" cy="12" r="6" />
                  <circle cx="12" cy="12" r="2" />
                </svg>
              </NavIcon>
            </div>
          </div>

          {/* Bottom Home Indicator */}
          <div className="phone__home-bar" />
        </div>

        {/* Ambient Ground Glow */}
        <div className="phone__glow" />
      </motion.div>
    </div>
  )
}

function NavIcon({
  label,
  active,
  onClick,
  children,
}: {
  label: string
  active?: boolean
  onClick?: () => void
  children: React.ReactNode
}) {
  return (
    <button
      className={`phone__nav-icon ${active ? 'phone__nav-icon--active' : ''}`}
      onClick={onClick}
      title={label}
      type="button"
    >
      {children}
    </button>
  )
}

function SignalIcon() {
  return (
    <svg width="15" height="11" viewBox="0 0 24 18" fill="currentColor">
      <rect x="0" y="12" width="4" height="6" rx="1" />
      <rect x="5" y="8" width="4" height="10" rx="1" />
      <rect x="10" y="4" width="4" height="14" rx="1" />
      <rect x="15" y="0" width="4" height="18" rx="1" opacity="0.35" />
    </svg>
  )
}

function WifiIcon() {
  return (
    <svg width="15" height="11" viewBox="0 0 24 18" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round">
      <path d="M1 7.5C5.4 3 10.4 1 12 1s6.6 2 11 6.5" />
      <path d="M4.5 11C7.5 8 10 7 12 7s4.5 1 7.5 4" />
      <circle cx="12" cy="16.5" r="1.5" fill="currentColor" />
    </svg>
  )
}

function BatteryIcon() {
  return (
    <svg width="18" height="11" viewBox="0 0 28 14" fill="none" stroke="currentColor" strokeWidth="1.8">
      <rect x="1" y="1" width="22" height="12" rx="3" />
      <rect x="2.5" y="2.5" width="16" height="9" rx="2" fill="currentColor" stroke="none" />
      <path d="M24.5 4.5v5a2 2 0 000-5z" fill="currentColor" stroke="none" />
    </svg>
  )
}
