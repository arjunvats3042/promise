import { useState, useEffect } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import {
  X,
  Check,
  Flame,
  Users,
  MessageSquare,
  Pause,
  Play,
  Share2,
  Trash2,
  Calendar,
  UserPlus,
  Send,
  Target,
  CheckCircle2,
} from 'lucide-react'
import {
  backend,
  type Goal,
  type GoalParticipant,
  type GoalCheckInItem,
} from '../../lib/backend'
import { triggerCelebration } from '../ui/celebrate'
import './GoalDetailModal.css'

const WEEKDAY_LABELS = ['M', 'T', 'W', 'T', 'F', 'S', 'S']

interface GoalDetailModalProps {
  goal: Goal | null
  isOpen: boolean
  onClose: () => void
  onCheckIn: (id: string, e?: React.MouseEvent) => void
  onCountCheckIn?: (goal: Goal) => void
  onOpenChat?: (goal: Goal) => void
  onUpdated?: () => void
}

export default function GoalDetailModal({
  goal,
  isOpen,
  onClose,
  onCheckIn,
  onCountCheckIn,
  onOpenChat,
  onUpdated,
}: GoalDetailModalProps) {
  const [participants, setParticipants] = useState<GoalParticipant[]>([])
  const [checkIns, setCheckIns] = useState<GoalCheckInItem[]>([])
  const [loadingExtras, setLoadingExtras] = useState(false)
  const [showInviteBox, setShowInviteBox] = useState(false)
  const [inviteEmail, setInviteEmail] = useState('')
  const [inviting, setInviting] = useState(false)
  const [actionLoading, setActionLoading] = useState(false)

  useEffect(() => {
    if (!isOpen || !goal) return

    setLoadingExtras(true)
    Promise.allSettled([
      goal.is_shared ? backend.goals.listParticipants(goal.id) : Promise.resolve([]),
      backend.goals.listCheckIns(goal.id),
    ])
      .then(([partsRes, checkInsRes]) => {
        if (partsRes.status === 'fulfilled') setParticipants(partsRes.value)
        if (checkInsRes.status === 'fulfilled') setCheckIns(checkInsRes.value)
      })
      .finally(() => setLoadingExtras(false))
  }, [isOpen, goal?.id, goal?.is_shared])

  if (!isOpen || !goal) return null

  const isPaused = goal.status === 'PAUSED'
  const isCompleted = goal.status === 'COMPLETED'
  const isCountTracking = goal.tracking_kind === 'COUNT'
  const progressPercent = goal.progress ? Math.round(goal.progress * 100) : 75

  async function handleCheckInClick(e: React.MouseEvent) {
    if (!goal) return
    if (isCountTracking && onCountCheckIn) {
      onCountCheckIn(goal)
      onClose()
      return
    }
    triggerCelebration({ particleCount: 35, spread: 60 })
    onCheckIn(goal.id, e)
    onUpdated?.()
  }

  async function handlePauseResume() {
    if (!goal) return
    setActionLoading(true)
    try {
      if (isPaused) {
        await backend.goals.resume(goal.id)
      } else {
        await backend.goals.pause(goal.id)
      }
      onUpdated?.()
      onClose()
    } catch {
      alert('Could not update habit state.')
    } finally {
      setActionLoading(false)
    }
  }

  async function handleComplete() {
    if (!goal) return
    if (!confirm('Mark this habit as fully completed?')) return
    setActionLoading(true)
    try {
      await backend.goals.complete(goal.id)
      onUpdated?.()
      onClose()
    } catch {
      alert('Could not complete habit.')
    } finally {
      setActionLoading(false)
    }
  }

  async function handleCancel() {
    if (!goal) return
    if (!confirm('Cancel and archive this habit? This cannot be undone.')) return
    setActionLoading(true)
    try {
      await backend.goals.cancel(goal.id)
      onUpdated?.()
      onClose()
    } catch {
      alert('Could not cancel habit.')
    } finally {
      setActionLoading(false)
    }
  }

  async function handleConvertToShared() {
    if (!goal) return
    setActionLoading(true)
    try {
      await backend.goals.convertToShared(goal.id)
      triggerCelebration({ particleCount: 30, spread: 50 })
      onUpdated?.()
      onClose()
    } catch {
      alert('Could not convert to shared circle.')
    } finally {
      setActionLoading(false)
    }
  }

  async function handleSendInvite(e: React.FormEvent) {
    e.preventDefault()
    if (!inviteEmail.trim() || !goal) return
    setInviting(true)
    try {
      const newPart = await backend.goals.inviteParticipant(goal.id, inviteEmail.trim())
      setParticipants((prev) => [...prev, newPart])
      setInviteEmail('')
      setShowInviteBox(false)
      triggerCelebration({ particleCount: 25, spread: 45 })
    } catch {
      alert('Failed to send invitation. Please check the email address.')
    } finally {
      setInviting(false)
    }
  }

  return (
    <div className="goal-detail__overlay" onClick={onClose}>
      <motion.div
        className="goal-detail__card"
        onClick={(e) => e.stopPropagation()}
        initial={{ opacity: 0, scale: 0.95, y: 15 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.95, y: 15 }}
        transition={{ type: 'spring', stiffness: 420, damping: 30 }}
      >
        {/* Top Header */}
        <div className="goal-detail__header">
          <div className="goal-detail__badges-row">
            <span
              className={`goal-detail__status-pill goal-detail__status-pill--${goal.status.toLowerCase()}`}
            >
              <span className="goal-detail__status-dot" />
              {goal.status}
            </span>
            {goal.is_shared && (
              <span className="goal-detail__circle-pill">
                <Users size={12} />
                <span>Shared Circle</span>
              </span>
            )}
            <span className="goal-detail__kind-pill">
              {goal.recurrence_kind === 'DAILY' ? 'Daily Rhythm' : goal.recurrence_kind}
            </span>
          </div>

          <button className="goal-detail__close-btn" onClick={onClose} aria-label="Close">
            <X size={16} />
          </button>
        </div>

        {/* Body Content */}
        <div className="goal-detail__body">
          <h2 className="goal-detail__title">{goal.title}</h2>
          {goal.description ? (
            <p className="goal-detail__desc">{goal.description}</p>
          ) : (
            <p className="goal-detail__desc-empty">No detailed description provided.</p>
          )}

          {/* Quick Metrics Grid */}
          <div className="goal-detail__metrics-grid">
            <div className="goal-detail__metric">
              <div className="goal-detail__metric-header">
                <Flame size={14} className="goal-detail__flame-icon" />
                <span>Current Streak</span>
              </div>
              <span className="goal-detail__metric-num">
                {goal.current_streak} {goal.current_streak === 1 ? 'day' : 'days'}
              </span>
            </div>

            <div className="goal-detail__metric">
              <div className="goal-detail__metric-header">
                <Target size={14} className="goal-detail__target-icon" />
                <span>Tracking Kind</span>
              </div>
              <span className="goal-detail__metric-num">
                {isCountTracking
                  ? `${goal.target_value || 0} ${goal.target_unit || 'units'}`
                  : 'Daily Binary'}
              </span>
            </div>

            <div className="goal-detail__metric">
              <div className="goal-detail__metric-header">
                <Calendar size={14} className="goal-detail__cal-icon" />
                <span>Started</span>
              </div>
              <span className="goal-detail__metric-val">
                {new Date(goal.start_date || goal.created_at).toLocaleDateString('en-US', {
                  month: 'short',
                  day: 'numeric',
                  year: 'numeric',
                })}
              </span>
            </div>
          </div>

          {/* 7-Day Consistency Dot Matrix (Matching Android PromiseWeeklyHistoryGrid.kt) */}
          <div className="goal-detail__history-card">
            <div className="goal-detail__history-head">
              <span className="goal-detail__history-title">7-Day Consistency Rhythm</span>
              <span className="goal-detail__history-sub">Current Week</span>
            </div>
            <div className="goal-detail__dots-row">
              {WEEKDAY_LABELS.map((day, idx) => {
                const isCompleted = idx < Math.min(goal.current_streak % 7 || 7, 7)
                return (
                  <div key={idx} className="goal-detail__dot-wrap">
                    <span className="goal-detail__dot-label">{day}</span>
                    <div
                      className={`goal-detail__dot ${
                        isCompleted ? 'goal-detail__dot--done' : ''
                      }`}
                    >
                      {isCompleted && <Check size={11} strokeWidth={3} />}
                    </div>
                  </div>
                )
              })}
            </div>
          </div>

          {/* Recent Check-in Logs (Telemetry from backend) */}
          <div className="goal-detail__history-card">
            <div className="goal-detail__history-head">
              <span className="goal-detail__history-title">Recent Check-in Logs</span>
              <span className="goal-detail__history-sub">
                {loadingExtras ? 'Updating…' : `${checkIns.length} recorded`}
              </span>
            </div>
            {checkIns.length === 0 ? (
              <p className="goal-detail__log-empty">
                {loadingExtras ? 'Retrieving telemetry…' : 'No past check-ins recorded yet for this practice cycle.'}
              </p>
            ) : (
              <div className="goal-detail__logs-list">
                {checkIns.slice(0, 5).map((log) => (
                  <div key={log.id} className="goal-detail__log-item">
                    <div className="goal-detail__log-left">
                      <CheckCircle2 size={13} className="goal-detail__log-check" />
                      <span className="goal-detail__log-date">{log.period_date}</span>
                    </div>
                    <div className="goal-detail__log-right">
                      {log.value !== null && log.value !== undefined && (
                        <span className="goal-detail__log-count">
                          {log.value} {goal.target_unit || 'units'}
                        </span>
                      )}
                      {log.notes && <span className="goal-detail__log-note">“{log.notes}”</span>}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Shared Circle Section (If Shared) */}
          {goal.is_shared && (
            <div className="goal-detail__shared-section">
              <div className="goal-detail__shared-header">
                <div className="goal-detail__shared-title-wrap">
                  <Users size={14} />
                  <span>Accountability Partners ({participants.length})</span>
                </div>
                <button
                  className="goal-detail__invite-btn"
                  onClick={() => setShowInviteBox(!showInviteBox)}
                >
                  <UserPlus size={13} />
                  <span>Invite Partner</span>
                </button>
              </div>

              {/* Collective Rhythm Bar */}
              <div className="goal-detail__progress-box">
                <div className="goal-detail__progress-info">
                  <span>Group Momentum</span>
                  <span>{progressPercent}%</span>
                </div>
                <div className="goal-detail__progress-track">
                  <div
                    className="goal-detail__progress-bar"
                    style={{ width: `${progressPercent}%` }}
                  />
                </div>
              </div>

              {/* Invite Partner Inline Form */}
              <AnimatePresence>
                {showInviteBox && (
                  <motion.form
                    className="goal-detail__invite-form"
                    onSubmit={handleSendInvite}
                    initial={{ opacity: 0, height: 0 }}
                    animate={{ opacity: 1, height: 'auto' }}
                    exit={{ opacity: 0, height: 0 }}
                  >
                    <input
                      type="email"
                      className="goal-detail__invite-input"
                      placeholder="Enter partner's Promise email…"
                      value={inviteEmail}
                      onChange={(e) => setInviteEmail(e.target.value)}
                      required
                    />
                    <button
                      type="submit"
                      className="goal-detail__send-invite-btn"
                      disabled={inviting || !inviteEmail.trim()}
                    >
                      <Send size={13} />
                      <span>{inviting ? 'Sending…' : 'Send Invite'}</span>
                    </button>
                  </motion.form>
                )}
              </AnimatePresence>

              {/* Members List */}
              <div className="goal-detail__members-list">
                {participants.map((p) => (
                  <div key={p.id} className="goal-detail__member-row">
                    <div className="goal-detail__member-info">
                      <div className="goal-detail__member-avatar">
                        {p.name ? p.name.charAt(0).toUpperCase() : 'P'}
                      </div>
                      <div>
                        <span className="goal-detail__member-name">{p.name}</span>
                        <span className="goal-detail__member-email">{p.email}</span>
                      </div>
                    </div>
                    <span
                      className={`goal-detail__role-badge ${
                        p.role === 'OWNER' ? 'goal-detail__role-badge--owner' : ''
                      }`}
                    >
                      {p.role}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Action Controls */}
        <div className="goal-detail__actions">
          {/* Primary Action Button */}
          {!isCompleted && (
            <motion.button
              className="goal-detail__btn goal-detail__btn--checkin"
              onClick={handleCheckInClick}
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
            >
              <Check size={16} strokeWidth={3} />
              <span>
                {isCountTracking
                  ? `Record Check-In (${goal.target_value || ''} ${goal.target_unit || 'target'})`
                  : 'Check In for Today'}
              </span>
            </motion.button>
          )}

          {/* Secondary Actions */}
          <div className="goal-detail__secondary-actions">
            {goal.is_shared && onOpenChat && (
              <button
                className="goal-detail__secondary-btn goal-detail__secondary-btn--chat"
                onClick={() => {
                  onClose()
                  onOpenChat(goal)
                }}
              >
                <MessageSquare size={14} />
                <span>Open Circle Chat</span>
              </button>
            )}

            {!goal.is_shared && (
              <button
                className="goal-detail__secondary-btn"
                onClick={handleConvertToShared}
                disabled={actionLoading}
              >
                <Share2 size={14} />
                <span>Convert to Shared Circle</span>
              </button>
            )}

            <button
              className="goal-detail__secondary-btn"
              onClick={handlePauseResume}
              disabled={actionLoading}
            >
              {isPaused ? <Play size={14} /> : <Pause size={14} />}
              <span>{isPaused ? 'Resume Rhythm' : 'Pause'}</span>
            </button>

            {!isCompleted && (
              <button
                className="goal-detail__secondary-btn goal-detail__secondary-btn--complete"
                onClick={handleComplete}
                disabled={actionLoading}
              >
                <CheckCircle2 size={14} />
                <span>Complete</span>
              </button>
            )}

            <button
              className="goal-detail__secondary-btn goal-detail__secondary-btn--danger"
              onClick={handleCancel}
              disabled={actionLoading}
            >
              <Trash2 size={14} />
              <span>Archive</span>
            </button>
          </div>
        </div>
      </motion.div>
    </div>
  )
}
