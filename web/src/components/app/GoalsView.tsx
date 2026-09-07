import { useState, useEffect, useMemo } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import {
  Target,
  Plus,
  Sparkles,
  Users,
  MessageSquare,
  Flame,
  Check,
  Pause,
  Play,
  Share2,
} from 'lucide-react'
import { backend, type Goal, type GoalInvitePreview } from '../../lib/backend'
import { triggerCelebration } from '../ui/celebrate'
import GoalChatModal from './GoalChatModal'
import GoalAiBuilderModal from './GoalAiBuilderModal'
import './GoalsView.css'

const WEEKDAY_KEYS = ['M', 'T', 'W', 'T', 'F', 'S', 'S']

interface GoalsViewProps {
  onOpenGoalDetail?: (goal: Goal) => void
  onOpenCountCheckIn?: (goal: Goal) => void
}

export default function GoalsView({
  onOpenGoalDetail,
  onOpenCountCheckIn,
}: GoalsViewProps = {}) {
  const [goals, setGoals] = useState<Goal[]>([])
  const [loading, setLoading] = useState(true)
  const [checkingInId, setCheckingInId] = useState<string | null>(null)
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [showAiBuilderModal, setShowAiBuilderModal] = useState(false)
  const [selectedChatGoal, setSelectedChatGoal] = useState<Goal | null>(null)
  const [invites, setInvites] = useState<GoalInvitePreview[]>([])

  // New goal state
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [recurrenceKind, setRecurrenceKind] = useState<'DAILY' | 'WEEKLY_DAYS' | 'N_PER_PERIOD'>('DAILY')
  const [trackingKind, setTrackingKind] = useState<'BINARY' | 'COUNT'>('BINARY')
  const [targetValue, setTargetValue] = useState<number | ''>('')
  const [targetUnit, setTargetUnit] = useState('')
  const [isSharedNew, setIsSharedNew] = useState(false)
  const [aiPrompt, setAiPrompt] = useState('')
  const [aiLoading, setAiLoading] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  function fetchGoals() {
    setLoading(true)
    backend.goals
      .list()
      .then((r) => setGoals(r.results))
      .catch(() => {})
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    fetchGoals()
    backend.goals.listInvites().then(setInvites).catch(() => {})
  }, [])

  async function handleAcceptInvite(goalId: string) {
    try {
      await backend.goals.acceptInvite(goalId)
      setInvites((prev) => prev.filter((i) => i.goal_id !== goalId))
      fetchGoals()
      triggerCelebration({ particleCount: 30, spread: 50 })
    } catch {
      alert('Failed to accept invite.')
    }
  }

  async function handleDeclineInvite(goalId: string) {
    try {
      await backend.goals.declineInvite(goalId)
      setInvites((prev) => prev.filter((i) => i.goal_id !== goalId))
    } catch {
      alert('Failed to decline invite.')
    }
  }

  async function handleCheckIn(id: string, e?: React.MouseEvent) {
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

  async function handlePauseResume(g: Goal) {
    try {
      const updated =
        g.status === 'ACTIVE'
          ? await backend.goals.pause(g.id)
          : await backend.goals.resume(g.id)
      setGoals((prev) => prev.map((x) => (x.id === g.id ? updated : x)))
    } catch {
      alert('Could not update goal status.')
    }
  }

  async function handleConvertToShared(id: string) {
    try {
      const updated = await backend.goals.convertToShared(id)
      setGoals((prev) => prev.map((x) => (x.id === id ? updated : x)))
      triggerCelebration({ particleCount: 25, spread: 45 })
    } catch {
      alert('Could not convert practice to shared circle.')
    }
  }

  async function handleAiSuggest() {
    if (!aiPrompt.trim() || aiLoading) return
    setAiLoading(true)
    try {
      const sug = await backend.ai.suggestGoal(aiPrompt.trim())
      setTitle(sug.title)
      if (sug.description) setDescription(sug.description)
      setRecurrenceKind(sug.recurrence_kind)
      setTrackingKind(sug.tracking_kind)
      if (sug.target_value) setTargetValue(sug.target_value)
      if (sug.target_unit) setTargetUnit(sug.target_unit)
      triggerCelebration({ particleCount: 18, spread: 35 })
    } catch {
      alert('Could not generate suggestion from Gemini.')
    } finally {
      setAiLoading(false)
    }
  }

  async function handleCreateGoal(e: React.FormEvent) {
    e.preventDefault()
    if (!title.trim()) return
    setSubmitting(true)
    try {
      const payload = {
        title: title.trim(),
        description: description.trim() || undefined,
        recurrence_kind: recurrenceKind,
        tracking_kind: trackingKind,
        start_date: new Date().toISOString().split('T')[0],
        target_value: trackingKind === 'COUNT' && targetValue ? Number(targetValue) : undefined,
        target_unit: trackingKind === 'COUNT' && targetUnit ? targetUnit : undefined,
      }
      let newGoal = await backend.goals.create(payload)
      if (isSharedNew) {
        newGoal = await backend.goals.convertToShared(newGoal.id)
      }
      setGoals((prev) => [newGoal, ...prev])
      setShowCreateModal(false)
      setTitle('')
      setDescription('')
      setAiPrompt('')
      setIsSharedNew(false)
      triggerCelebration({ particleCount: 30, spread: 50 })
    } catch {
      alert('Failed to create practice.')
    } finally {
      setSubmitting(false)
    }
  }

  const [filter, setFilter] = useState<'ACTIVE' | 'PAUSED' | 'COMPLETED'>('ACTIVE')

  const displayedGoals = useMemo(() => {
    if (filter === 'ACTIVE') return goals.filter((g) => g.status === 'ACTIVE')
    if (filter === 'PAUSED') return goals.filter((g) => g.status === 'PAUSED')
    if (filter === 'COMPLETED') return goals.filter((g) => g.status === 'COMPLETED')
    return goals
  }, [goals, filter])

  return (
    <div className="goals-view">
      {/* Header */}
      <div className="goals-view__header">
        <div className="goals-view__header-text">
          <h1 className="goals-view__heading">Goals</h1>
          <p className="goals-view__subheading">
            Build lasting consistency through daily rituals and shared accountability.
          </p>
        </div>

        <div className="goals-view__header-actions">
          <motion.button
            className="goals-view__ai-intention-btn"
            onClick={() => setShowAiBuilderModal(true)}
            whileHover={{ scale: 1.04 }}
            whileTap={{ scale: 0.96 }}
          >
            <Sparkles size={13} />
            <span>AI Intention</span>
          </motion.button>

          <motion.button
            className="goals-view__new-btn"
            onClick={() => {
              setIsSharedNew(false)
              setShowCreateModal(true)
            }}
            whileHover={{ scale: 1.04 }}
            whileTap={{ scale: 0.96 }}
          >
            <Plus size={16} />
            <span>New Goal</span>
          </motion.button>
        </div>
      </div>

      {/* Filter Chips Navigation Bar (Matching Android FilterChipsRow) */}
      <div className="goals-view__tabs" role="tablist">
        {(
          [
            { id: 'ACTIVE', label: 'Active', icon: Target },
            { id: 'PAUSED', label: 'Paused', icon: Pause },
            { id: 'COMPLETED', label: 'Completed', icon: Check },
          ] as const
        ).map((tab) => {
          const isActive = filter === tab.id
          const Icon = tab.icon
          return (
            <button
              key={tab.id}
              role="tab"
              aria-selected={isActive}
              className={`goals-view__tab ${isActive ? 'goals-view__tab--active' : ''}`}
              onClick={() => setFilter(tab.id)}
            >
              {isActive && (
                <motion.div
                  className="goals-view__tab-pill"
                  layoutId="activeGoalFilterTab"
                  transition={{ type: 'spring', stiffness: 450, damping: 35 }}
                />
              )}
              <span className="goals-view__tab-content">
                <Icon size={13} />
                <span>{tab.label}</span>
              </span>
            </button>
          )
        })}
      </div>

      {/* Pending Invites Banner (Matching Android GoalListItem.Invite) */}
      {invites.length > 0 && (
        <div className="goals-view__invites-section">
          {invites.map((inv) => (
            <div key={inv.goal_id} className="goals-view__invite-card">
              <div className="goals-view__invite-info">
                <span className="goals-view__invite-tag">SHARED CIRCLE INVITATION</span>
                <p className="goals-view__invite-title">
                  <strong>{inv.inviter_name || 'Someone'}</strong> invited you to join{' '}
                  <strong>"{inv.goal_title}"</strong>
                </p>
              </div>
              <div className="goals-view__invite-actions">
                <button
                  className="goals-view__invite-accept-btn"
                  onClick={() => handleAcceptInvite(inv.goal_id)}
                >
                  Accept
                </button>
                <button
                  className="goals-view__invite-decline-btn"
                  onClick={() => handleDeclineInvite(inv.goal_id)}
                >
                  Decline
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Content */}
      {loading ? (
        <div className="goals-view__skeleton">
          {[1, 2, 3].map((i) => (
            <div key={i} className="goals-view__skeleton-row" />
          ))}
        </div>
      ) : displayedGoals.length === 0 ? (
        <div className="goals-view__empty">
          <Target size={36} className="goals-view__empty-icon" />
          <p className="goals-view__empty-heading">No goals in this filter</p>
          <p className="goals-view__empty-sub">
            {filter === 'ACTIVE'
              ? 'Create a daily ritual or shared circle to build momentum and consistency.'
              : filter === 'PAUSED'
              ? 'No paused goals right now.'
              : 'Completed goals will be archived here.'}
          </p>
          <button
            className="goals-view__empty-cta"
            onClick={() => {
              setIsSharedNew(false)
              setShowCreateModal(true)
            }}
          >
            <Plus size={14} />
            <span>Create Goal</span>
          </button>
        </div>
      ) : (
        <div className="goals-view__grid">
          <AnimatePresence initial={false}>
            {displayedGoals.map((g: Goal) =>
              g.is_shared ? (
                <SharedCircleCard
                  key={g.id}
                  goal={g}
                  onCheckIn={handleCheckIn}
                  onOpenChat={() => setSelectedChatGoal(g)}
                  onOpenGoalDetail={onOpenGoalDetail}
                  onOpenCountCheckIn={onOpenCountCheckIn}
                  checkingInId={checkingInId}
                />
              ) : (
                <PracticeCard
                  key={g.id}
                  goal={g}
                  onCheckIn={handleCheckIn}
                  onPauseResume={handlePauseResume}
                  onConvertToShared={handleConvertToShared}
                  onOpenGoalDetail={onOpenGoalDetail}
                  onOpenCountCheckIn={onOpenCountCheckIn}
                  checkingInId={checkingInId}
                />
              )
            )}
          </AnimatePresence>
        </div>
      )}

      {/* Modal: Create Goal */}
      <AnimatePresence>
        {showCreateModal && (
          <div
            className="goals-view__modal-overlay"
            onClick={() => setShowCreateModal(false)}
          >
            <motion.div
              className="goals-view__modal"
              onClick={(e) => e.stopPropagation()}
              initial={{ opacity: 0, scale: 0.95, y: 15 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: 15 }}
              transition={{ type: 'spring', stiffness: 420, damping: 30 }}
            >
              <div className="goals-view__modal-head">
                <h2 className="goals-view__modal-title">
                  {isSharedNew ? 'New Shared Accountability Circle' : 'New Daily Practice'}
                </h2>
                <button
                  className="goals-view__modal-close"
                  onClick={() => setShowCreateModal(false)}
                >
                  ✕
                </button>
              </div>

              {/* Gemini Suggestion Bar */}
              <div className="goals-view__ai-bar">
                <Sparkles size={14} className="goals-view__ai-icon" />
                <input
                  type="text"
                  className="goals-view__ai-input"
                  placeholder="Ask Gemini: 'Read 20 pages every night before bed'"
                  value={aiPrompt}
                  onChange={(e) => setAiPrompt(e.target.value)}
                />
                <button
                  type="button"
                  className="goals-view__ai-btn"
                  onClick={handleAiSuggest}
                  disabled={aiLoading || !aiPrompt.trim()}
                >
                  {aiLoading ? 'Thinking…' : 'Refine'}
                </button>
              </div>

              <form onSubmit={handleCreateGoal} className="goals-view__modal-form">
                <div className="goals-view__input-group">
                  <label className="goals-view__input-label">Practice Title</label>
                  <input
                    type="text"
                    className="goals-view__input"
                    placeholder="e.g. Morning 5km Run"
                    value={title}
                    onChange={(e) => setTitle(e.target.value)}
                    required
                  />
                </div>

                <div className="goals-view__input-group">
                  <label className="goals-view__input-label">Description (Optional)</label>
                  <input
                    type="text"
                    className="goals-view__input"
                    placeholder="e.g. Zone 2 cardio pace before 8:30 AM"
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                  />
                </div>

                <div className="goals-view__modal-grid">
                  <div className="goals-view__input-group">
                    <label className="goals-view__input-label">Recurrence</label>
                    <select
                      className="goals-view__select"
                      value={recurrenceKind}
                      onChange={(e) => setRecurrenceKind(e.target.value as any)}
                    >
                      <option value="DAILY">Daily</option>
                      <option value="WEEKLY_DAYS">Specific Weekdays</option>
                      <option value="N_PER_PERIOD">N Times per Period</option>
                    </select>
                  </div>

                  <div className="goals-view__input-group">
                    <label className="goals-view__input-label">Tracking Type</label>
                    <select
                      className="goals-view__select"
                      value={trackingKind}
                      onChange={(e) => setTrackingKind(e.target.value as any)}
                    >
                      <option value="BINARY">Yes / No (Binary)</option>
                      <option value="COUNT">Numeric Target (Count)</option>
                    </select>
                  </div>
                </div>

                {trackingKind === 'COUNT' && (
                  <div className="goals-view__modal-grid">
                    <div className="goals-view__input-group">
                      <label className="goals-view__input-label">Target Number</label>
                      <input
                        type="number"
                        className="goals-view__input"
                        placeholder="e.g. 20"
                        value={targetValue}
                        onChange={(e) =>
                          setTargetValue(e.target.value ? Number(e.target.value) : '')
                        }
                        required
                      />
                    </div>
                    <div className="goals-view__input-group">
                      <label className="goals-view__input-label">Unit</label>
                      <input
                        type="text"
                        className="goals-view__input"
                        placeholder="e.g. pages, km, mins"
                        value={targetUnit}
                        onChange={(e) => setTargetUnit(e.target.value)}
                        required
                      />
                    </div>
                  </div>
                )}

                <div className="goals-view__modal-actions">
                  <motion.button
                    type="submit"
                    className="goals-view__submit-btn"
                    disabled={submitting || !title.trim()}
                    whileHover={{ scale: 1.02 }}
                    whileTap={{ scale: 0.98 }}
                  >
                    {submitting ? 'Creating…' : 'Create Practice'}
                  </motion.button>
                  <button
                    type="button"
                    className="goals-view__cancel-btn"
                    onClick={() => setShowCreateModal(false)}
                  >
                    Cancel
                  </button>
                </div>
              </form>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* Modal: Goal Chat Room */}
      <GoalChatModal
        goal={selectedChatGoal}
        isOpen={Boolean(selectedChatGoal)}
        onClose={() => setSelectedChatGoal(null)}
      />

      {/* Modal: Goal AI Builder */}
      <GoalAiBuilderModal
        isOpen={showAiBuilderModal}
        onClose={() => setShowAiBuilderModal(false)}
        onCreated={fetchGoals}
      />
    </div>
  )
}

/* Solo Practice Card */
function PracticeCard({
  goal,
  onCheckIn,
  onPauseResume,
  onConvertToShared,
  onOpenGoalDetail,
  onOpenCountCheckIn,
  checkingInId,
}: {
  goal: Goal
  onCheckIn: (id: string, e?: React.MouseEvent) => void
  onPauseResume: (g: Goal) => void
  onConvertToShared: (id: string) => void
  onOpenGoalDetail?: (goal: Goal) => void
  onOpenCountCheckIn?: (goal: Goal) => void
  checkingInId: string | null
}) {
  const isPaused = goal.status === 'PAUSED'

  return (
    <motion.div
      className={`practice-card ${isPaused ? 'practice-card--paused' : ''}`}
      layout
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, scale: 0.95 }}
      transition={{ duration: 0.2 }}
      onClick={() => onOpenGoalDetail?.(goal)}
      style={{ cursor: 'pointer' }}
      title="View habit details & history"
    >
      <div className="practice-card__top">
        <div className="practice-card__title-row">
          <span className="practice-card__title">{goal.title}</span>
          {isPaused && <span className="practice-card__paused-pill">PAUSED</span>}
        </div>
        {goal.description && (
          <p className="practice-card__desc">{goal.description}</p>
        )}
      </div>

      {/* Streak & 7-Day Matrix Row */}
      <div className="practice-card__history-box">
        <div className="practice-card__streak-line">
          <div className="practice-card__flame-badge">
            <Flame size={13} />
            <span>
              {goal.current_streak} {goal.current_streak === 1 ? 'day' : 'days'}
            </span>
          </div>
          <span className="practice-card__cadence-lbl">
            {goal.recurrence_kind === 'DAILY' ? 'Daily Rhythm' : goal.recurrence_kind}
          </span>
        </div>

        {/* 7-day dot matrix matching Android's PromiseWeeklyHistoryGrid.kt */}
        <div className="practice-card__dots-row">
          {WEEKDAY_KEYS.map((day, idx) => {
            const isCompleted = idx < Math.min(goal.current_streak % 7 || 7, 7)
            return (
              <div key={idx} className="practice-card__day-dot-wrap">
                <span className="practice-card__day-char">{day}</span>
                <div
                  className={`practice-card__day-dot ${
                    isCompleted ? 'practice-card__day-dot--done' : ''
                  }`}
                />
              </div>
            )
          })}
        </div>
      </div>

      {/* Bottom Controls */}
      <div className="practice-card__bottom" onClick={(e) => e.stopPropagation()}>
        <div className="practice-card__left-actions">
          <button
            className="practice-card__tool-btn"
            onClick={() => onPauseResume(goal)}
            title={isPaused ? 'Resume practice' : 'Pause practice'}
          >
            {isPaused ? <Play size={12} /> : <Pause size={12} />}
            <span>{isPaused ? 'Resume' : 'Pause'}</span>
          </button>

          <button
            className="practice-card__tool-btn practice-card__tool-btn--share"
            onClick={() => onConvertToShared(goal.id)}
            title="Convert to Shared Accountability Circle"
          >
            <Share2 size={12} />
            <span>Make Circle</span>
          </button>
        </div>

        {!isPaused && (
          <motion.button
            className="practice-card__checkin-btn"
            onClick={(e) => {
              e.stopPropagation()
              if (goal.tracking_kind === 'COUNT') {
                onOpenCountCheckIn?.(goal)
              } else {
                onCheckIn(goal.id, e)
              }
            }}
            disabled={checkingInId === goal.id}
            whileHover={{ scale: 1.05 }}
            whileTap={{ scale: 0.95 }}
          >
            <Check size={14} strokeWidth={3} />
            <span>
              {checkingInId === goal.id
                ? '…'
                : goal.tracking_kind === 'COUNT'
                ? 'Record'
                : 'Check In'}
            </span>
          </motion.button>
        )}
      </div>
    </motion.div>
  )
}

/* Shared Circle Card */
function SharedCircleCard({
  goal,
  onCheckIn,
  onOpenChat,
  onOpenGoalDetail,
  onOpenCountCheckIn,
  checkingInId,
}: {
  goal: Goal
  onCheckIn: (id: string, e?: React.MouseEvent) => void
  onOpenChat: () => void
  onOpenGoalDetail?: (goal: Goal) => void
  onOpenCountCheckIn?: (goal: Goal) => void
  checkingInId: string | null
}) {
  const progressPercent = goal.progress ? Math.round(goal.progress * 100) : 75

  return (
    <motion.div
      className="shared-card"
      layout
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, scale: 0.95 }}
      transition={{ duration: 0.2 }}
      onClick={() => onOpenGoalDetail?.(goal)}
      style={{ cursor: 'pointer' }}
    >
      <div className="shared-card__top">
        <div className="shared-card__badge-row">
          <span className="shared-card__circle-pill">
            <Users size={12} />
            <span>Circle</span>
          </span>
          <span className="shared-card__streak-pill">
            🔥 {goal.current_streak} days
          </span>
        </div>
        <span className="shared-card__title">{goal.title}</span>
        {goal.description && (
          <p className="shared-card__desc">{goal.description}</p>
        )}
      </div>

      {/* Collective Progress Bar */}
      <div className="shared-card__progress-box">
        <div className="shared-card__progress-labels">
          <span className="shared-card__progress-txt">Collective Rhythm</span>
          <span className="shared-card__progress-val">{progressPercent}%</span>
        </div>
        <div className="shared-card__bar">
          <div
            className="shared-card__bar-fill"
            style={{ width: `${progressPercent}%` }}
          />
        </div>
      </div>

      {/* Latest message snippet or members */}
      <div className="shared-card__chat-preview">
        <MessageSquare size={13} className="shared-card__chat-icon" />
        <span className="shared-card__chat-text">
          {goal.latest_chat_message || 'Active circle feed · Tap to open room chat'}
        </span>
      </div>

      {/* Action Footer */}
      <div
        className="shared-card__footer"
        onClick={(e) => e.stopPropagation()}
      >
        <button
          className="shared-card__room-btn"
          onClick={() => onOpenGoalDetail?.(goal)}
          title="Circle details & members"
        >
          <Users size={13} />
          <span>Members</span>
        </button>

        <button className="shared-card__room-btn" onClick={onOpenChat}>
          <MessageSquare size={13} />
          <span>Chat</span>
        </button>

        <motion.button
          className="shared-card__checkin-btn"
          onClick={(e) => {
            e.stopPropagation()
            if (goal.tracking_kind === 'COUNT') {
              onOpenCountCheckIn?.(goal)
            } else {
              onCheckIn(goal.id, e)
            }
          }}
          disabled={checkingInId === goal.id}
          whileHover={{ scale: 1.05 }}
          whileTap={{ scale: 0.95 }}
        >
          <Check size={14} strokeWidth={3} />
          <span>
            {checkingInId === goal.id
              ? '…'
              : goal.tracking_kind === 'COUNT'
              ? 'Record'
              : 'Check In'}
          </span>
        </motion.button>
      </div>
    </motion.div>
  )
}
