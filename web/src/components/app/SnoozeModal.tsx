import { useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { X, Clock, AlertTriangle, ShieldCheck } from 'lucide-react'
import { backend } from '../../lib/backend'
import './SnoozeModal.css'

interface Props {
  commitmentId: string | null
  commitmentTitle: string
  isOpen: boolean
  onClose: () => void
  onSnoozed: () => void
}

const REASONS = [
  'Blocked on external dependency or response',
  'Reprioritized for critical deadline',
  'Underestimated task scope & complexity',
  'Physical energy or health buffer needed',
  'Custom rationale',
]

export function SnoozeModal({ commitmentId, commitmentTitle, isOpen, onClose, onSnoozed }: Props) {
  const [selectedReason, setSelectedReason] = useState(REASONS[0])
  const [customReason, setCustomReason] = useState('')
  const [snoozeDate, setSnoozeDate] = useState(() => {
    const d = new Date()
    d.setDate(d.getDate() + 1)
    d.setHours(18, 0, 0, 0)
    return d.toISOString().slice(0, 16)
  })
  const [submitting, setSubmitting] = useState(false)

  if (!isOpen || !commitmentId) return null

  async function handleConfirm(e: React.FormEvent) {
    e.preventDefault()
    if (!commitmentId) return

    setSubmitting(true)
    try {
      const iso = new Date(snoozeDate).toISOString()
      await backend.commitments.snooze(commitmentId, iso)
      onSnoozed()
      onClose()
    } catch {
      alert('Could not snooze commitment. Please verify your connection.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AnimatePresence>
      <div className="snooze-modal-backdrop" onClick={onClose}>
        <motion.div
          className="snooze-modal"
          onClick={(e) => e.stopPropagation()}
          initial={{ opacity: 0, scale: 0.95, y: 16 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.95, y: 16 }}
          transition={{ duration: 0.25, ease: [0.16, 1, 0.3, 1] }}
        >
          {/* Header */}
          <div className="snooze-modal__header">
            <div className="snooze-modal__badge">
              <Clock size={13} />
              <span>INTENTIONAL FRICTION · SNOOZE</span>
            </div>
            <button className="snooze-modal__close-btn" onClick={onClose} aria-label="Close">
              <X size={16} />
            </button>
          </div>

          <h3 className="snooze-modal__title">Reschedule Commitment</h3>
          <p className="snooze-modal__commitment-name">"{commitmentTitle}"</p>

          <div className="snooze-modal__safeguard">
            <AlertTriangle size={15} className="snooze-modal__safeguard-icon" />
            <p>
              Promise avoids effortless procrastination. Moving a commitment requires conscious intent and an explicit reason.
            </p>
          </div>

          <form onSubmit={handleConfirm} className="snooze-modal__form">
            {/* Reason Selector */}
            <div className="snooze-form-group">
              <label className="snooze-form-label">Snooze Rationale</label>
              <div className="snooze-reasons-list">
                {REASONS.map((r) => (
                  <label key={r} className="snooze-reason-option">
                    <input
                      type="radio"
                      name="reason"
                      value={r}
                      checked={selectedReason === r}
                      onChange={() => setSelectedReason(r)}
                    />
                    <span>{r}</span>
                  </label>
                ))}
              </div>

              {selectedReason === 'Custom rationale' && (
                <input
                  type="text"
                  className="snooze-input-text"
                  placeholder="State your documented rationale…"
                  value={customReason}
                  onChange={(e) => setCustomReason(e.target.value)}
                  required
                />
              )}
            </div>

            {/* New Date Time Picker */}
            <div className="snooze-form-group">
              <label className="snooze-form-label">Reschedule Until</label>
              <input
                type="datetime-local"
                className="snooze-input-date"
                value={snoozeDate}
                onChange={(e) => setSnoozeDate(e.target.value)}
                required
              />
            </div>

            {/* Footer Buttons */}
            <div className="snooze-modal__footer">
              <button type="button" className="btn btn--ghost" onClick={onClose}>
                Cancel
              </button>
              <button
                type="submit"
                className="btn btn--primary snooze-confirm-btn"
                disabled={submitting}
              >
                <ShieldCheck size={15} />
                <span>{submitting ? 'Updating…' : 'Confirm Reschedule'}</span>
              </button>
            </div>
          </form>
        </motion.div>
      </div>
    </AnimatePresence>
  )
}
