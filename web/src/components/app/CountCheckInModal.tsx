import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { X, Check, Minus, Plus, Target } from 'lucide-react'
import { backend, type Goal } from '../../lib/backend'
import { triggerCelebration } from '../ui/celebrate'
import './CountCheckInModal.css'

interface CountCheckInModalProps {
  goal: Goal | null
  isOpen: boolean
  onClose: () => void
  onSuccess: () => void
}

export default function CountCheckInModal({
  goal,
  isOpen,
  onClose,
  onSuccess,
}: CountCheckInModalProps) {
  const [value, setValue] = useState<number>(1)
  const [notes, setNotes] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (goal) {
      setValue(goal.target_value || 1)
      setNotes('')
    }
  }, [goal])

  if (!isOpen || !goal) return null

  const target = goal.target_value || 1
  const unit = goal.target_unit || 'units'
  const isTargetMet = value >= target

  function adjust(delta: number) {
    setValue((prev) => Math.max(0, prev + delta))
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!goal || submitting) return

    setSubmitting(true)
    try {
      await backend.goals.checkIn(goal.id, {
        status: 'COMPLETED',
        value: Number(value),
        notes: notes.trim() || undefined,
      })
      triggerCelebration({ particleCount: 36, spread: 60 })
      onSuccess()
      onClose()
    } catch {
      alert('Could not record check-in. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="count-checkin__overlay" onClick={onClose}>
      <motion.div
        className="count-checkin__card"
        onClick={(e) => e.stopPropagation()}
        initial={{ opacity: 0, scale: 0.95, y: 15 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.95, y: 15 }}
        transition={{ type: 'spring', stiffness: 420, damping: 30 }}
      >
        <div className="count-checkin__head">
          <div className="count-checkin__head-left">
            <Target size={16} className="count-checkin__target-icon" />
            <span className="count-checkin__head-title">Record Progress</span>
          </div>
          <button className="count-checkin__close" onClick={onClose} aria-label="Close">
            <X size={15} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="count-checkin__body">
          <h2 className="count-checkin__goal-title">{goal.title}</h2>
          <p className="count-checkin__goal-target">
            Daily Target: <strong>{target} {unit}</strong>
          </p>

          {/* Stepper Input */}
          <div className="count-checkin__stepper">
            <button
              type="button"
              className="count-checkin__step-btn"
              onClick={() => adjust(-1)}
            >
              <Minus size={18} />
            </button>

            <div className="count-checkin__input-wrap">
              <input
                type="number"
                className="count-checkin__val-input"
                value={value}
                min={0}
                onChange={(e) => setValue(Math.max(0, parseInt(e.target.value) || 0))}
              />
              <span className="count-checkin__unit-label">{unit}</span>
            </div>

            <button
              type="button"
              className="count-checkin__step-btn"
              onClick={() => adjust(1)}
            >
              <Plus size={18} />
            </button>
          </div>

          {/* Quick Presets */}
          <div className="count-checkin__presets">
            <button type="button" onClick={() => adjust(1)}>+1</button>
            <button type="button" onClick={() => adjust(5)}>+5</button>
            <button type="button" onClick={() => adjust(10)}>+10</button>
            <button type="button" onClick={() => setValue(target)}>Set Target ({target})</button>
          </div>

          {/* Progress Indicator */}
          <div className="count-checkin__progress-track">
            <div
              className={`count-checkin__progress-bar ${isTargetMet ? 'count-checkin__progress-bar--met' : ''}`}
              style={{ width: `${Math.min(100, Math.round((value / target) * 100))}%` }}
            />
          </div>
          <span className="count-checkin__pct-label">
            {Math.round((value / target) * 100)}% of daily target {isTargetMet ? '✨ (Completed!)' : ''}
          </span>

          {/* Reflection Notes */}
          <div className="count-checkin__notes-field">
            <label className="count-checkin__notes-label">Reflection note (optional)</label>
            <input
              type="text"
              className="count-checkin__notes-input"
              placeholder="e.g. Completed chapters 4 & 5 with ease"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
            />
          </div>

          {/* Submit button */}
          <motion.button
            type="submit"
            className="count-checkin__submit-btn"
            disabled={submitting}
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
          >
            <Check size={16} strokeWidth={3} />
            <span>{submitting ? 'Recording…' : `Record ${value} ${unit}`}</span>
          </motion.button>
        </form>
      </motion.div>
    </div>
  )
}
