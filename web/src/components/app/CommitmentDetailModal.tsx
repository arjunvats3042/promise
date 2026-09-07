import { motion } from 'framer-motion'
import { X, Check, Clock, PauseCircle, Ban, Calendar, Sparkles } from 'lucide-react'
import type { Commitment } from '../../lib/backend'
import { triggerCelebration } from '../ui/celebrate'
import './CommitmentDetailModal.css'

interface CommitmentDetailModalProps {
  commitment: Commitment | null
  isOpen: boolean
  onClose: () => void
  onComplete: (id: string, e?: React.MouseEvent) => void
  onSnooze: (commitment: Commitment) => void
  onWait: (id: string) => void
  onUnsnooze: (id: string) => void
  onCancel: (id: string) => void
}

export default function CommitmentDetailModal({
  commitment,
  isOpen,
  onClose,
  onComplete,
  onSnooze,
  onWait,
  onUnsnooze,
  onCancel,
}: CommitmentDetailModalProps) {
  if (!isOpen || !commitment) return null

  const isTerminal = commitment.status === 'COMPLETED' || commitment.status === 'CANCELLED'
  const canComplete = !isTerminal
  const canSnooze = commitment.status === 'PENDING' || commitment.status === 'SNOOZED'
  const canWait = commitment.status === 'PENDING'
  const canUnsnooze = commitment.status === 'SNOOZED'
  const canCancel = !isTerminal

  function handleCompleteClick(e: React.MouseEvent) {
    if (!commitment) return
    triggerCelebration({ particleCount: 36, spread: 60 })
    onComplete(commitment.id, e)
    onClose()
  }

  function handleCancelClick() {
    if (!commitment) return
    if (confirm('Cancel this commitment? This cannot be undone.')) {
      onCancel(commitment.id)
      onClose()
    }
  }

  return (
    <div className="cmt-detail__overlay" onClick={onClose}>
      <motion.div
        className="cmt-detail__card"
        onClick={(e) => e.stopPropagation()}
        initial={{ opacity: 0, scale: 0.95, y: 15 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.95, y: 15 }}
        transition={{ type: 'spring', stiffness: 420, damping: 30 }}
      >
        {/* Header */}
        <div className="cmt-detail__header">
          <div className="cmt-detail__status-row">
            <span className={`cmt-detail__status-pill cmt-detail__status-pill--${commitment.status.toLowerCase()}`}>
              <span className="cmt-detail__status-dot" />
              {commitment.status}
            </span>
            {commitment.is_overdue && (
              <span className="cmt-detail__badge-overdue">OVERDUE</span>
            )}
          </div>
          <button className="cmt-detail__close-btn" onClick={onClose} aria-label="Close">
            <X size={16} />
          </button>
        </div>

        {/* Title & Description */}
        <div className="cmt-detail__body">
          <h2 className="cmt-detail__title">{commitment.title}</h2>
          {commitment.description ? (
            <p className="cmt-detail__desc">{commitment.description}</p>
          ) : (
            <p className="cmt-detail__desc-empty">No detailed description provided.</p>
          )}

          {/* Timing & Precision Card */}
          <div className="cmt-detail__meta-grid">
            <div className="cmt-detail__meta-item">
              <span className="cmt-detail__meta-label">
                <Calendar size={13} />
                <span>Deadline</span>
              </span>
              <span className="cmt-detail__meta-val">
                {commitment.due_at
                  ? new Date(commitment.due_at).toLocaleDateString('en-US', {
                      weekday: 'short',
                      month: 'short',
                      day: 'numeric',
                      hour: commitment.due_precision === 'DATETIME' ? 'numeric' : undefined,
                      minute: commitment.due_precision === 'DATETIME' ? '2-digit' : undefined,
                    })
                  : 'No set deadline'}
              </span>
            </div>

            <div className="cmt-detail__meta-item">
              <span className="cmt-detail__meta-label">
                <Clock size={13} />
                <span>Precision</span>
              </span>
              <span className="cmt-detail__meta-val">
                {commitment.due_precision || 'DAY'}
              </span>
            </div>

            {commitment.snoozed_until && (
              <div className="cmt-detail__meta-item cmt-detail__meta-item--snooze">
                <span className="cmt-detail__meta-label">
                  <Clock size={13} />
                  <span>Snoozed Until</span>
                </span>
                <span className="cmt-detail__meta-val">
                  {new Date(commitment.snoozed_until).toLocaleString('en-US', {
                    month: 'short',
                    day: 'numeric',
                    hour: 'numeric',
                    minute: '2-digit',
                  })}
                </span>
              </div>
            )}
          </div>
        </div>

        {/* Action Controls */}
        <div className="cmt-detail__actions">
          {canComplete && (
            <motion.button
              className="cmt-detail__btn cmt-detail__btn--complete"
              onClick={handleCompleteClick}
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
            >
              <Check size={16} strokeWidth={3} />
              <span>Mark Complete</span>
            </motion.button>
          )}

          <div className="cmt-detail__secondary-actions">
            {canSnooze && (
              <motion.button
                className="cmt-detail__btn cmt-detail__btn--secondary"
                onClick={() => {
                  onSnooze(commitment)
                  onClose()
                }}
                whileHover={{ scale: 1.03 }}
                whileTap={{ scale: 0.97 }}
              >
                <Clock size={14} />
                <span>Snooze</span>
              </motion.button>
            )}

            {canUnsnooze && (
              <motion.button
                className="cmt-detail__btn cmt-detail__btn--secondary"
                onClick={() => {
                  onUnsnooze(commitment.id)
                  onClose()
                }}
                whileHover={{ scale: 1.03 }}
                whileTap={{ scale: 0.97 }}
              >
                <Sparkles size={14} />
                <span>Unsnooze</span>
              </motion.button>
            )}

            {canWait && (
              <motion.button
                className="cmt-detail__btn cmt-detail__btn--secondary"
                onClick={() => {
                  onWait(commitment.id)
                  onClose()
                }}
                title="Waiting on external input or dependencies"
                whileHover={{ scale: 1.03 }}
                whileTap={{ scale: 0.97 }}
              >
                <PauseCircle size={14} />
                <span>Wait On Dependency</span>
              </motion.button>
            )}

            {canCancel && (
              <motion.button
                className="cmt-detail__btn cmt-detail__btn--danger"
                onClick={handleCancelClick}
                whileHover={{ scale: 1.03 }}
                whileTap={{ scale: 0.97 }}
              >
                <Ban size={14} />
                <span>Cancel</span>
              </motion.button>
            )}
          </div>
        </div>
      </motion.div>
    </div>
  )
}
