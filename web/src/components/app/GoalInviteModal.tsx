import { useState } from 'react'
import { motion } from 'framer-motion'
import { Users, X, Check } from 'lucide-react'
import { backend, type GoalInvitePreview } from '../../lib/backend'
import { triggerCelebration } from '../ui/celebrate'
import './GoalInviteModal.css'

interface GoalInviteModalProps {
  invites: GoalInvitePreview[]
  isOpen: boolean
  onClose: () => void
  onAccepted: () => void
}

export default function GoalInviteModal({
  invites,
  isOpen,
  onClose,
  onAccepted,
}: GoalInviteModalProps) {
  const [currentIndex, setCurrentIndex] = useState(0)
  const [submitting, setSubmitting] = useState(false)

  if (!isOpen || invites.length === 0) return null

  const current = invites[currentIndex] || invites[0]
  const total = invites.length

  async function handleAccept() {
    if (!current || submitting) return
    setSubmitting(true)
    try {
      await backend.goals.acceptInvite(current.goal_id)
      triggerCelebration({ particleCount: 40, spread: 70 })
      onAccepted()
      if (currentIndex < total - 1) {
        setCurrentIndex((prev) => prev + 1)
      } else {
        onClose()
      }
    } catch {
      alert('Could not accept invitation.')
    } finally {
      setSubmitting(false)
    }
  }

  async function handleDecline() {
    if (!current || submitting) return
    setSubmitting(true)
    try {
      await backend.goals.declineInvite(current.goal_id)
      if (currentIndex < total - 1) {
        setCurrentIndex((prev) => prev + 1)
      } else {
        onClose()
      }
    } catch {
      alert('Could not decline invitation.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="invite-modal__overlay" onClick={onClose}>
      <motion.div
        className="invite-modal__card"
        onClick={(e) => e.stopPropagation()}
        initial={{ opacity: 0, scale: 0.95, y: 15 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.95, y: 15 }}
        transition={{ type: 'spring', stiffness: 420, damping: 30 }}
      >
        <button className="invite-modal__close" onClick={onClose} aria-label="Close">
          <X size={15} />
        </button>

        <div className="invite-modal__icon-wrap">
          <Users size={28} className="invite-modal__icon" />
        </div>

        <span className="invite-modal__badge">SHARED CIRCLE INVITATION</span>

        <h2 className="invite-modal__title">{current.goal_title}</h2>
        <p className="invite-modal__subtitle">
          <strong>{current.inviter_name || current.inviter_email}</strong> invited you to build
          shared momentum together in this practice.
        </p>

        {total > 1 && (
          <span className="invite-modal__counter">
            Invitation {currentIndex + 1} of {total}
          </span>
        )}

        <div className="invite-modal__actions">
          <button
            className="invite-modal__decline-btn"
            onClick={handleDecline}
            disabled={submitting}
          >
            Decline
          </button>
          <motion.button
            className="invite-modal__accept-btn"
            onClick={handleAccept}
            disabled={submitting}
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
          >
            <Check size={16} strokeWidth={3} />
            <span>{submitting ? 'Joining…' : 'Accept & Join Circle'}</span>
          </motion.button>
        </div>
      </motion.div>
    </div>
  )
}
