import { useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { X, Sparkles, Plus, CheckCircle2, Clock } from 'lucide-react'
import { backend, type DuePrecision, type RefinedCommitment } from '../../lib/backend'
import { triggerCelebration } from '../ui/celebrate'
import './CreateCommitmentModal.css'

interface Props {
  isOpen: boolean
  onClose: () => void
  onCreated: () => void
}

export function CreateCommitmentModal({ isOpen, onClose, onCreated }: Props) {
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [dueAt, setDueAt] = useState('')
  const [duePrecision, setDuePrecision] = useState<DuePrecision>('DATETIME')
  const [isRefining, setIsRefining] = useState(false)
  const [refinedResult, setRefinedResult] = useState<RefinedCommitment | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (!isOpen) return null

  async function handleAiRefine() {
    if (!title.trim() || isRefining) return
    setIsRefining(true)
    try {
      const res = await backend.ai.refine(title.trim())
      setRefinedResult(res)
      triggerCelebration({ particleCount: 20, spread: 40 })
    } catch {
      alert('Could not refine with Gemini. Check your network.')
    } finally {
      setIsRefining(false)
    }
  }

  function applyRefinement() {
    if (!refinedResult) return
    if (refinedResult.refined_title) setTitle(refinedResult.refined_title)
    if (refinedResult.refined_description) setDescription(refinedResult.refined_description)
    if (refinedResult.suggested_due_at) {
      const d = new Date(refinedResult.suggested_due_at)
      setDueAt(d.toISOString().slice(0, 16))
    }
    if (refinedResult.suggested_due_precision) {
      setDuePrecision(refinedResult.suggested_due_precision === 'MINUTE' ? 'DATETIME' : 'DATE')
    }
    setRefinedResult(null)
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!title.trim() || submitting) return

    setSubmitting(true)
    try {
      await backend.commitments.create({
        title: title.trim(),
        description: description.trim(),
        due_at: dueAt ? new Date(dueAt).toISOString() : null,
        due_precision: duePrecision,
      })
      triggerCelebration({ particleCount: 30, spread: 50 })
      setTitle('')
      setDescription('')
      setDueAt('')
      setRefinedResult(null)
      onCreated()
      onClose()
    } catch {
      alert('Failed to seal commitment.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AnimatePresence>
      <div className="create-modal-backdrop" onClick={onClose}>
        <motion.div
          className="create-modal"
          onClick={(e) => e.stopPropagation()}
          initial={{ opacity: 0, scale: 0.95, y: 16 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.95, y: 16 }}
          transition={{ duration: 0.25, ease: [0.16, 1, 0.3, 1] }}
        >
          {/* Header */}
          <div className="create-modal__header">
            <div className="create-modal__badge">
              <Plus size={13} />
              <span>DISCIPLINE 01 · NEW COMMITMENT</span>
            </div>
            <button className="create-modal__close-btn" onClick={onClose} aria-label="Close">
              <X size={16} />
            </button>
          </div>

          <h2 className="create-modal__title">Make a Sacred Promise</h2>
          <p className="create-modal__sub">
            Not a casual to-do item. State what you will execute with explicit clarity.
          </p>

          <form onSubmit={handleSubmit} className="create-modal__form">
            {/* Title Input with Gemini Sparkle Button */}
            <div className="create-form-group">
              <div className="create-form-label-row">
                <label className="create-form-label">Promise Title</label>
                <button
                  type="button"
                  className="create-ai-btn"
                  onClick={handleAiRefine}
                  disabled={!title.trim() || isRefining}
                >
                  <Sparkles size={12} />
                  <span>{isRefining ? 'Refining with Gemini…' : 'Refine with Gemini 3.7'}</span>
                </button>
              </div>
              <input
                type="text"
                className="create-input-text"
                placeholder="e.g. Finalize sprint architecture review or kal shaam 6 baje doctor…"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                required
                autoFocus
              />
            </div>

            {/* Inline Gemini Suggestion Card */}
            {refinedResult && (
              <motion.div
                className="create-ai-preview"
                initial={{ opacity: 0, y: -8 }}
                animate={{ opacity: 1, y: 0 }}
              >
                <div className="create-ai-preview__header">
                  <span className="create-ai-preview__tag">Gemini Advisory Suggestion</span>
                  <button
                    type="button"
                    className="create-ai-preview__apply"
                    onClick={applyRefinement}
                  >
                    Apply Suggestion ✓
                  </button>
                </div>
                <h4 className="create-ai-preview__title">{refinedResult.refined_title}</h4>
                {refinedResult.refined_description && (
                  <p className="create-ai-preview__desc">{refinedResult.refined_description}</p>
                )}
                {refinedResult.suggested_due_at && (
                  <span className="create-ai-preview__due">
                    📅 Suggested Due: {new Date(refinedResult.suggested_due_at).toLocaleString()}
                  </span>
                )}
              </motion.div>
            )}

            {/* Description */}
            <div className="create-form-group">
              <label className="create-form-label">Context / Invariants (Optional)</label>
              <textarea
                className="create-input-textarea"
                rows={2}
                placeholder="Specific conditions of satisfaction or deliverable links…"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
            </div>

            {/* Temporal Lock: Due Date & Precision */}
            <div className="create-form-row">
              <div className="create-form-group" style={{ flex: 1.4 }}>
                <label className="create-form-label">
                  <Clock size={12} />
                  <span>Due Date & Time</span>
                </label>
                <input
                  type="datetime-local"
                  className="create-input-date"
                  value={dueAt}
                  onChange={(e) => setDueAt(e.target.value)}
                />
              </div>

              <div className="create-form-group" style={{ flex: 1 }}>
                <label className="create-form-label">Time Precision</label>
                <select
                  className="create-input-select"
                  value={duePrecision}
                  onChange={(e) => setDuePrecision(e.target.value as DuePrecision)}
                >
                  <option value="DATETIME">Minute Precision</option>
                  <option value="DATE">Day-Level</option>
                  <option value="NONE">No Deadline</option>
                </select>
              </div>
            </div>

            {/* Footer */}
            <div className="create-modal__footer">
              <button type="button" className="btn btn--ghost" onClick={onClose}>
                Cancel
              </button>
              <button
                type="submit"
                className="btn btn--primary create-submit-btn"
                disabled={submitting || !title.trim()}
              >
                <CheckCircle2 size={16} />
                <span>{submitting ? 'Sealing Promise…' : 'Seal Promise'}</span>
              </button>
            </div>
          </form>
        </motion.div>
      </div>
    </AnimatePresence>
  )
}
