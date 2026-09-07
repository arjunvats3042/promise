import { useState, useEffect, useMemo } from 'react'
import { motion } from 'framer-motion'
import {
  Flame,
  Clock,
  Check,
  Sparkles,
  ArrowRight,
  Mic,
  TrendingUp,
} from 'lucide-react'
import {
  backend,
  type Commitment,
  type Goal,
  type UserProfile,
  type WeeklyInsights,
  type ParsedThoughtItem,
  type GoalInvitePreview,
} from '../../lib/backend'
import { triggerCelebration } from '../ui/celebrate'
import { SnoozeModal } from './SnoozeModal'
import { CreateCommitmentModal } from './CreateCommitmentModal'
import CommitmentDetailModal from './CommitmentDetailModal'
import VoiceCaptureModal from './VoiceCaptureModal'
import './HomeView.css'

const WEEKDAYS = ['M', 'T', 'W', 'T', 'F', 'S', 'S']

interface HomeViewProps {
  user: UserProfile
  onNavigateToCommitments: () => void
  onNavigateToGoals: () => void
  onOpenGoalDetail?: (goal: Goal) => void
  onOpenCountCheckIn?: (goal: Goal) => void
  onOpenSearch?: () => void
  onOpenInvites?: () => void
}

function getGreeting(name: string): string {
  const h = new Date().getHours()
  const first = name ? name.split(' ')[0] : 'there'
  if (h < 5) return `Still up, ${first}?`
  if (h < 12) return `Good morning, ${first}.`
  if (h < 17) return `Good afternoon, ${first}.`
  if (h < 21) return `Good evening, ${first}.`
  return `Good night, ${first}.`
}

export default function HomeView({
  user,
  onNavigateToCommitments,
  onNavigateToGoals,
  onOpenGoalDetail,
  onOpenCountCheckIn,
  onOpenSearch: _onOpenSearch,
  onOpenInvites,
}: HomeViewProps) {
  const [commitments, setCommitments] = useState<Commitment[]>([])
  const [goals, setGoals] = useState<Goal[]>([])
  const [motivation, setMotivation] = useState<string>('')
  const [insights, setInsights] = useState<WeeklyInsights | null>(null)
  const [loading, setLoading] = useState(true)
  const [pendingInvites, setPendingInvites] = useState<GoalInvitePreview[]>([])

  // Modals & Sheets
  const [snoozeTarget, setSnoozeTarget] = useState<Commitment | null>(null)
  const [detailTarget, setDetailTarget] = useState<Commitment | null>(null)
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [showThoughtParser, setShowThoughtParser] = useState(false)
  const [showVoiceModal, setShowVoiceModal] = useState(false)
  const [thoughtInput, setThoughtInput] = useState('')
  const [parsingThought, setParsingThought] = useState(false)
  const [parsedItems, setParsedItems] = useState<ParsedThoughtItem[]>([])
  const [checkingInId, setCheckingInId] = useState<string | null>(null)

  const todayDateStr = new Date().toLocaleDateString('en-US', {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
  })

  function loadFeed() {
    setLoading(true)
    Promise.allSettled([
      backend.commitments.list({ status: 'PENDING' }),
      backend.goals.list({ status: 'ACTIVE' }),
      backend.ai.motivation(),
      backend.ai.insights(),
      backend.goals.listInvites(),
    ])
      .then(([cmtRes, goalRes, motRes, insRes, invRes]) => {
        if (cmtRes.status === 'fulfilled') setCommitments(cmtRes.value.results)
        if (goalRes.status === 'fulfilled') setGoals(goalRes.value.results)
        if (motRes.status === 'fulfilled') setMotivation(motRes.value.quote)
        if (insRes.status === 'fulfilled') setInsights(insRes.value)
        if (invRes.status === 'fulfilled') setPendingInvites(invRes.value || [])
      })
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    loadFeed()
  }, [])

  // Smart triage matching Android HomeScreen.kt: Overdue first -> Incomplete -> Completed
  const sortedCommitments = useMemo(() => {
    return [...commitments].sort((a, b) => {
      if (a.is_overdue && !b.is_overdue) return -1
      if (!a.is_overdue && b.is_overdue) return 1
      return 0
    })
  }, [commitments])

  // Daily momentum calculation
  const totalItems = commitments.length + goals.length
  const completedToday = goals.filter((g) => (g.current_streak > 0)).length // surrogate for checked-in
  const momentumPercent = totalItems > 0 ? Math.round((completedToday / totalItems) * 100) : 0

  async function handleCompleteCommitment(id: string, e?: React.MouseEvent) {
    if (e) {
      const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
      triggerCelebration({
        particleCount: 35,
        spread: 60,
        origin: {
          x: (rect.left + rect.width / 2) / window.innerWidth,
          y: (rect.top + rect.height / 2) / window.innerHeight,
        },
      })
    } else {
      triggerCelebration({ particleCount: 35, spread: 60 })
    }

    try {
      await backend.commitments.complete(id)
      setCommitments((prev) => prev.filter((c) => c.id !== id))
    } catch {
      /* ignore */
    }
  }

  async function handleCheckInGoal(id: string, e?: React.MouseEvent) {
    if (e) {
      const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
      triggerCelebration({
        particleCount: 32,
        spread: 55,
        origin: {
          x: (rect.left + rect.width / 2) / window.innerWidth,
          y: (rect.top + rect.height / 2) / window.innerHeight,
        },
      })
    } else {
      triggerCelebration({ particleCount: 30, spread: 50 })
    }

    setCheckingInId(id)
    try {
      await backend.goals.checkIn(id, {
        status: 'COMPLETED',
        period_date: new Date().toISOString().split('T')[0],
      })
      setGoals((prev) =>
        prev.map((g) =>
          g.id === id ? { ...g, current_streak: g.current_streak + 1 } : g
        )
      )
    } catch {
      alert('Could not record check-in.')
    } finally {
      setCheckingInId(null)
    }
  }

  async function parseThoughtString(text: string) {
    if (!text.trim() || parsingThought) return
    setParsingThought(true)
    setShowThoughtParser(true)
    try {
      const res = await backend.ai.parseThought(text.trim())
      setParsedItems(res.items)
      triggerCelebration({ particleCount: 25, spread: 45 })
    } catch {
      alert('Could not parse thought with Gemini.')
    } finally {
      setParsingThought(false)
    }
  }

  // Thought -> Promise / Goal parser
  async function handleParseThought(e: React.FormEvent) {
    e.preventDefault()
    await parseThoughtString(thoughtInput)
  }

  function handleVoiceCaptured(transcript: string) {
    setThoughtInput(transcript)
    parseThoughtString(transcript)
  }

  async function handleConfirmParsedItem(item: ParsedThoughtItem) {
    try {
      if (item.type === 'goal') {
        const newGoal = await backend.goals.create({
          title: item.title,
          recurrence_kind: 'DAILY',
          tracking_kind: 'BINARY',
        })
        setGoals((prev) => [newGoal, ...prev])
      } else {
        const newCmt = await backend.commitments.create({
          title: item.title,
          due_at: item.due_at || null,
          due_precision: (item.due_precision as any) || 'NONE',
        })
        setCommitments((prev) => [newCmt, ...prev])
      }
      setParsedItems((prev) => prev.filter((x) => x !== item))
      triggerCelebration({ particleCount: 20, spread: 35 })
    } catch {
      alert('Failed to save parsed commitment.')
    }
  }

  return (
    <div className="home-view">
      {/* 1. Header (Greeting, Date & Quick Capture Bar) */}
      <div className="home-view__header">
        <div className="home-view__greeting-box">
          <span className="home-view__date-label">{todayDateStr}</span>
          <h1 className="home-view__greeting">{getGreeting(user.name)}</h1>
        </div>

        {/* Quick Capture Pill Bar (Matching Android HomeScreen.kt) */}
        <div
          className="home-view__capture-pill"
          onClick={() => setShowThoughtParser(true)}
        >
          <div className="home-view__capture-left">
            <Sparkles size={16} className="home-view__sparkle-icon" />
            <span className="home-view__capture-placeholder">
              What’s on your mind today?
            </span>
          </div>
          <button
            className="home-view__voice-btn"
            onClick={(e) => {
              e.stopPropagation()
              setShowVoiceModal(true)
            }}
            title="Speech-to-Text Voice Capture"
          >
            <Mic size={15} />
          </button>
        </div>
      </div>

      {/* Pending Shared Circle Invitations (Matching Android GoalInvitePopupDialog) */}
      {pendingInvites.length > 0 && (
        <motion.div
          className="home-view__invite-alert"
          initial={{ opacity: 0, y: -6 }}
          animate={{ opacity: 1, y: 0 }}
          onClick={onOpenInvites}
        >
          <div className="home-view__invite-alert-left">
            <span className="home-view__invite-alert-tag">Invitation</span>
            <span className="home-view__invite-alert-text">
              You have {pendingInvites.length} pending shared circle invitation{pendingInvites.length > 1 ? 's' : ''}
            </span>
          </div>
          <span className="home-view__invite-alert-cta">Review & Join →</span>
        </motion.div>
      )}

      {/* 2. Daily Momentum Hero Card (Matching Android DailyMomentumHeroCard) */}
      <div className="home-view__hero-card">
        <div className="home-view__hero-content">
          <span className="home-view__hero-tag">TODAY’S FOCUS</span>
          <h2 className="home-view__hero-headline">
            {commitments.length === 0
              ? 'All clear for today ✨'
              : commitments.some((c) => c.is_overdue)
              ? 'Overdue promises need your focus'
              : 'Keep the momentum going'}
          </h2>
          <p className="home-view__hero-sub">
            {commitments.length} pending promises · {goals.length} active practices
          </p>
        </div>

        {/* Momentum Circular Progress Ring */}
        <div className="home-view__ring-wrap">
          <svg className="home-view__ring" viewBox="0 0 48 48">
            <circle
              className="home-view__ring-bg"
              cx="24"
              cy="24"
              r="20"
              strokeWidth="4"
            />
            <circle
              className="home-view__ring-fill"
              cx="24"
              cy="24"
              r="20"
              strokeWidth="4"
              strokeDasharray={125.6}
              strokeDashoffset={125.6 - (125.6 * momentumPercent) / 100}
            />
          </svg>
          <span className="home-view__ring-val">{momentumPercent}%</span>
        </div>
      </div>

      {/* 3. Today's Thought / Motivation Section (Matching Android TodayThoughtSection) */}
      {motivation && (
        <div className="home-view__thought-card">
          <span className="home-view__thought-lbl">TODAY’S MOTIVATION</span>
          <p className="home-view__thought-quote">“{motivation}”</p>
        </div>
      )}

      {/* 4. Dual Section Layout: Today's Focus & Daily Rituals */}
      <div className="home-view__main-sections">
        {/* Left Column: Today's Focus (Commitments) */}
        <div className="home-view__section">
          <div className="home-view__section-head">
            <div>
              <h2 className="home-view__section-title">Today’s Focus</h2>
              <p className="home-view__section-sub">
                {commitments.length} promises due or pending
              </p>
            </div>
            <button
              className="home-view__link-btn"
              onClick={onNavigateToCommitments}
            >
              <span>Commitments</span>
              <ArrowRight size={13} />
            </button>
          </div>

          {loading ? (
            <div className="home-view__skeleton">
              <div className="home-view__skeleton-row" />
              <div className="home-view__skeleton-row" />
            </div>
          ) : commitments.length === 0 ? (
            <div className="home-view__empty-box">
              <p>No pending commitments today.</p>
              <button
                className="home-view__empty-btn"
                onClick={() => setIsCreateOpen(true)}
              >
                + Add Commitment
              </button>
            </div>
          ) : (
            <div className="home-view__list">
              {sortedCommitments.slice(0, 4).map((c) => (
                <div
                  key={c.id}
                  className={`home-item ${c.is_overdue ? 'home-item--overdue' : ''}`}
                  onClick={() => setDetailTarget(c)}
                >
                  <button
                    className="home-item__check-btn"
                    onClick={(e) => {
                      e.stopPropagation()
                      handleCompleteCommitment(c.id, e)
                    }}
                    title="Mark complete"
                  >
                    <Check size={12} strokeWidth={3} />
                  </button>

                  <div className="home-item__info">
                    <span className="home-item__title">{c.title}</span>
                    <div className="home-item__meta">
                      {c.is_overdue && (
                        <span className="home-item__badge home-item__badge--overdue">
                          Overdue
                        </span>
                      )}
                      {c.due_at && (
                        <span className="home-item__due">
                          Due{' '}
                          {new Date(c.due_at).toLocaleDateString('en-US', {
                            month: 'short',
                            day: 'numeric',
                          })}
                        </span>
                      )}
                    </div>
                  </div>

                  <button
                    className="home-item__snooze-btn"
                    onClick={(e) => {
                      e.stopPropagation()
                      setSnoozeTarget(c)
                    }}
                    title="Intentional Snooze"
                  >
                    <Clock size={12} />
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Right Column: Daily Rituals & Practices (Goals) */}
        <div className="home-view__section">
          <div className="home-view__section-head">
            <div>
              <h2 className="home-view__section-title">Daily Rituals</h2>
              <p className="home-view__section-sub">
                {goals.length} recurring consistency habits
              </p>
            </div>
            <button
              className="home-view__link-btn"
              onClick={onNavigateToGoals}
            >
              <span>Goals & Circles</span>
              <ArrowRight size={13} />
            </button>
          </div>

          {loading ? (
            <div className="home-view__skeleton">
              <div className="home-view__skeleton-row" />
              <div className="home-view__skeleton-row" />
            </div>
          ) : goals.length === 0 ? (
            <div className="home-view__empty-box">
              <p>No active daily practices set up.</p>
              <button
                className="home-view__empty-btn"
                onClick={onNavigateToGoals}
              >
                + Create Practice
              </button>
            </div>
          ) : (
            <div className="home-view__list">
              {goals.slice(0, 4).map((g) => (
                <div
                  key={g.id}
                  className="home-practice-card"
                  onClick={() => onOpenGoalDetail?.(g)}
                  style={{ cursor: 'pointer' }}
                  title="View practice details and streak"
                >
                  <div className="home-practice-card__top">
                    <div className="home-practice-card__left">
                      <span className="home-practice-card__title">{g.title}</span>
                      <div className="home-practice-card__streak">
                        <Flame size={12} />
                        <span>
                          {g.current_streak} {g.current_streak === 1 ? 'day' : 'days'}
                        </span>
                      </div>
                    </div>

                    <motion.button
                      className="home-practice-card__checkin"
                      onClick={(e) => {
                        e.stopPropagation()
                        if (g.tracking_kind === 'COUNT') {
                          onOpenCountCheckIn?.(g)
                        } else {
                          handleCheckInGoal(g.id, e)
                        }
                      }}
                      disabled={checkingInId === g.id}
                      whileHover={{ scale: 1.05 }}
                      whileTap={{ scale: 0.95 }}
                    >
                      <Check size={13} strokeWidth={3} />
                      <span>
                        {checkingInId === g.id
                          ? '…'
                          : g.tracking_kind === 'COUNT'
                          ? 'Record'
                          : 'Check In'}
                      </span>
                    </motion.button>
                  </div>

                  {/* 7-day consistency dot row matching Android PromiseWeeklyHistoryGrid.kt */}
                  <div className="home-practice-card__dots">
                    {WEEKDAYS.map((day, idx) => {
                      const isCompleted = idx < Math.min(g.current_streak % 7 || 7, 7)
                      return (
                        <div key={idx} className="home-practice-card__dot-col">
                          <span className="home-practice-card__dot-lbl">{day}</span>
                          <div
                            className={`home-practice-card__dot ${
                              isCompleted ? 'home-practice-card__dot--done' : ''
                            }`}
                          />
                        </div>
                      )
                    })}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* 5. Weekly Insights Section (Matching Android WeeklyInsightsCard) */}
      {insights && (
        <div className="home-view__insights-card">
          <div className="home-view__insights-head">
            <TrendingUp size={16} className="home-view__insights-icon" />
            <div>
              <h3 className="home-view__insights-title">Weekly Insights</h3>
              <p className="home-view__insights-sub">
                Your 7-day retrospective & momentum telemetry
              </p>
            </div>
          </div>
          <div className="home-view__insights-grid">
            <div className="home-view__insight-metric">
              <span className="home-view__metric-val">{insights.commitments_completed}</span>
              <span className="home-view__metric-lbl">Kept Promises</span>
            </div>
            <div className="home-view__insight-metric">
              <span className="home-view__metric-val">{insights.goals_checked_in}</span>
              <span className="home-view__metric-lbl">Practice Check-Ins</span>
            </div>
          </div>
          {(insights.ai_suggestions?.[0] || insights.motivation_line) && (
            <p className="home-view__insight-rec">
              ✦ {insights.ai_suggestions?.[0] || insights.motivation_line}
            </p>
          )}
        </div>
      )}

      {/* Modals & Thought Parser */}
      <CreateCommitmentModal
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
        onCreated={() => loadFeed()}
      />

      <SnoozeModal
        isOpen={Boolean(snoozeTarget)}
        onClose={() => setSnoozeTarget(null)}
        commitmentId={snoozeTarget?.id || ''}
        commitmentTitle={snoozeTarget?.title || ''}
        onSnoozed={() => loadFeed()}
      />

      <CommitmentDetailModal
        isOpen={Boolean(detailTarget)}
        commitment={detailTarget}
        onClose={() => setDetailTarget(null)}
        onComplete={(id) => handleCompleteCommitment(id)}
        onSnooze={(c) => setSnoozeTarget(c)}
        onWait={() => loadFeed()}
        onUnsnooze={() => loadFeed()}
        onCancel={() => loadFeed()}
      />

      {/* Thought -> Promise / Goal Parser Modal */}
      {showThoughtParser && (
        <div
          className="home-parser__overlay"
          onClick={() => setShowThoughtParser(false)}
        >
          <motion.div
            className="home-parser__card"
            onClick={(e) => e.stopPropagation()}
            initial={{ opacity: 0, scale: 0.95, y: 15 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
          >
            <div className="home-parser__head">
              <div className="home-parser__title-box">
                <Sparkles size={16} className="home-parser__sparkle" />
                <h3 className="home-parser__title">AI Thought Parser</h3>
              </div>
              <button
                className="home-parser__close"
                onClick={() => setShowThoughtParser(false)}
              >
                ✕
              </button>
            </div>
            <p className="home-parser__sub">
              Brain-dump anything. Gemini will decompose it into structured commitments and daily habits.
            </p>

            <form onSubmit={handleParseThought} className="home-parser__form">
              <textarea
                className="home-parser__textarea"
                placeholder="e.g. Call client tomorrow at 11am, finish quarterly slides by Thursday, and read 20 pages every night"
                value={thoughtInput}
                onChange={(e) => setThoughtInput(e.target.value)}
                rows={3}
              />
              <button
                type="submit"
                className="home-parser__submit"
                disabled={!thoughtInput.trim() || parsingThought}
              >
                <Sparkles size={14} />
                <span>{parsingThought ? 'Decomposing…' : 'Parse Thought'}</span>
              </button>
            </form>

            {/* Parsed items results */}
            {parsedItems.length > 0 && (
              <div className="home-parser__results">
                <span className="home-parser__results-lbl">EXTRACTED COMMITMENTS</span>
                {parsedItems.map((item, i) => (
                  <div key={i} className="home-parser__item">
                    <div>
                      <span className="home-parser__item-type">{item.type}</span>
                      <p className="home-parser__item-title">{item.title}</p>
                      {item.due_at && (
                        <span className="home-parser__item-due">
                          {item.due_at}
                        </span>
                      )}
                    </div>
                    <button
                      className="home-parser__accept-btn"
                      onClick={() => handleConfirmParsedItem(item)}
                    >
                      <Check size={13} />
                      <span>Create</span>
                    </button>
                  </div>
                ))}
              </div>
            )}
          </motion.div>
        </div>
      )}

      {/* Speech-to-Text Voice Capture Modal (Matching Android VoiceCaptureSheet.kt) */}
      <VoiceCaptureModal
        isOpen={showVoiceModal}
        onClose={() => setShowVoiceModal(false)}
        onThoughtCaptured={handleVoiceCaptured}
      />
    </div>
  )
}
