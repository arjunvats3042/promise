import { useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Sparkles, HelpCircle, Check, X, ArrowRight, Loader2 } from 'lucide-react'
import { backend, type GoalSuggestion } from '../../lib/backend'
import { triggerCelebration } from '../ui/celebrate'
import './GoalAiBuilderModal.css'

interface GoalAiBuilderModalProps {
  isOpen: boolean
  onClose: () => void
  onCreated: () => void
}

export default function GoalAiBuilderModal({
  isOpen,
  onClose,
  onCreated,
}: GoalAiBuilderModalProps) {
  const [promptText, setPromptText] = useState('')
  const [clarificationAnswer, setClarificationAnswer] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [suggestion, setSuggestion] = useState<GoalSuggestion | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (!isOpen) return null

  function resetState() {
    setPromptText('')
    setClarificationAnswer('')
    setLoading(false)
    setError(null)
    setSuggestion(null)
    setSubmitting(false)
  }

  function handleClose() {
    resetState()
    onClose()
  }

  async function handleGenerate(query: string) {
    if (!query.trim()) return
    setLoading(true)
    setError(null)
    try {
      const res = await backend.ai.suggestGoal(query.trim())
      setSuggestion(res)
    } catch {
      setError('Could not design habit suggestion. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  async function handleConfirmCreate() {
    if (!suggestion) return
    setSubmitting(true)
    setError(null)
    try {
      await backend.goals.create({
        title: suggestion.title,
        description: suggestion.description || undefined,
        recurrence_kind: suggestion.recurrence_kind,
        weekdays: suggestion.weekdays,
        tracking_kind: suggestion.tracking_kind,
        target_value: suggestion.target_value,
        target_unit: suggestion.target_unit,
        start_date: new Date().toISOString().split('T')[0],
      })
      triggerCelebration({ particleCount: 35, spread: 60 })
      onCreated()
      handleClose()
    } catch {
      setError('Failed to create habit from suggestion.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AnimatePresence>
      <div className="ai-builder-overlay" onClick={handleClose}>
        <motion.div
          className="ai-builder-modal"
          onClick={(e) => e.stopPropagation()}
          initial={{ opacity: 0, scale: 0.95, y: 16 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.95, y: 16 }}
          transition={{ duration: 0.25, ease: [0.16, 1, 0.3, 1] }}
        >
          {/* Header */}
          <div className="ai-builder-header">
            <div className="ai-builder-header-title">
              <div className="ai-builder-icon-wrap">
                <Sparkles size={18} className="ai-builder-icon" />
              </div>
              <div>
                <h2>Design a Habit with AI</h2>
                <p>Tell us what you want to practice. Gemini will structure the rhythm and cadence.</p>
              </div>
            </div>
            <button className="ai-builder-close" onClick={handleClose} aria-label="Close">
              <X size={18} />
            </button>
          </div>

          <div className="ai-builder-body">
            {/* Step 1: Initial Prompt */}
            {!suggestion && (
              <div className="ai-builder-step">
                <label className="ai-builder-label">What is your intention?</label>
                <textarea
                  className="ai-builder-textarea"
                  placeholder="e.g. Read 20 pages every night before sleeping, or workout at the gym 4x a week"
                  rows={4}
                  value={promptText}
                  onChange={(e) => {
                    setPromptText(e.target.value)
                    if (error) setError(null)
                  }}
                  autoFocus
                />

                {error && <p className="ai-builder-error">{error}</p>}

                <div className="ai-builder-presets">
                  <span className="ai-builder-presets-label">Popular intentions:</span>
                  <div className="ai-builder-preset-chips">
                    {[
                      'Meditation 10 mins daily',
                      'Hydrate 3 liters a day',
                      'Code 1 hour 5 days a week',
                      'No screens after 11 PM',
                    ].map((preset) => (
                      <button
                        key={preset}
                        type="button"
                        className="ai-builder-preset-chip"
                        onClick={() => {
                          setPromptText(preset)
                          handleGenerate(preset)
                        }}
                      >
                        {preset}
                      </button>
                    ))}
                  </div>
                </div>

                <div className="ai-builder-actions">
                  <button
                    className="ai-builder-submit-btn"
                    disabled={!promptText.trim() || loading}
                    onClick={() => handleGenerate(promptText)}
                  >
                    {loading ? (
                      <>
                        <Loader2 size={16} className="ai-builder-spinner" />
                        <span>Crafting Habit…</span>
                      </>
                    ) : (
                      <>
                        <Sparkles size={15} />
                        <span>Build Habit</span>
                        <ArrowRight size={14} />
                      </>
                    )}
                  </button>
                </div>
              </div>
            )}

            {/* Step 2A: Gemini Needs Clarification */}
            {suggestion && suggestion.status === 'NEEDS_CLARIFICATION' && (
              <div className="ai-builder-step">
                <div className="ai-builder-card ai-builder-card--clarify">
                  <div className="ai-builder-card-tag">
                    <HelpCircle size={14} />
                    <span>QUICK CLARIFICATION</span>
                  </div>
                  <p className="ai-builder-clarify-q">
                    {suggestion.clarification_question ||
                      'How often or for what duration would you like to practice this habit?'}
                  </p>
                </div>

                <label className="ai-builder-label">Your Answer</label>
                <input
                  type="text"
                  className="ai-builder-input"
                  placeholder="e.g. 5 days a week, 30 minutes each session"
                  value={clarificationAnswer}
                  onChange={(e) => setClarificationAnswer(e.target.value)}
                  autoFocus
                />

                {error && <p className="ai-builder-error">{error}</p>}

                <div className="ai-builder-actions">
                  <button
                    className="ai-builder-text-btn"
                    onClick={() => setSuggestion(null)}
                  >
                    Start over
                  </button>
                  <button
                    className="ai-builder-submit-btn"
                    disabled={!clarificationAnswer.trim() || loading}
                    onClick={() => {
                      const combined = `${promptText}. Details: ${clarificationAnswer.trim()}`
                      handleGenerate(combined)
                    }}
                  >
                    {loading ? (
                      <>
                        <Loader2 size={16} className="ai-builder-spinner" />
                        <span>Refining Habit…</span>
                      </>
                    ) : (
                      <>
                        <span>Continue with Details</span>
                        <ArrowRight size={14} />
                      </>
                    )}
                  </button>
                </div>
              </div>
            )}

            {/* Step 2B: Structured Habit Suggestion Ready */}
            {suggestion && suggestion.status !== 'NEEDS_CLARIFICATION' && (
              <div className="ai-builder-step">
                <div className="ai-builder-card ai-builder-card--ready">
                  <div className="ai-builder-card-tag ai-builder-card-tag--ready">
                    <Sparkles size={13} />
                    <span>SUGGESTED HABIT SPECIFICATION</span>
                  </div>

                  <h3 className="ai-builder-sug-title">{suggestion.title}</h3>

                  {suggestion.description && (
                    <p className="ai-builder-sug-desc">{suggestion.description}</p>
                  )}

                  <div className="ai-builder-specs-grid">
                    <div className="ai-builder-spec-item">
                      <span className="ai-builder-spec-label">Cadence</span>
                      <span className="ai-builder-spec-val">
                        {suggestion.recurrence_kind === 'DAILY'
                          ? 'Every Day'
                          : suggestion.recurrence_kind === 'WEEKLY_DAYS'
                          ? 'Selected Weekdays'
                          : 'Target per Period'}
                      </span>
                    </div>

                    <div className="ai-builder-spec-item">
                      <span className="ai-builder-spec-label">Tracking Mode</span>
                      <span className="ai-builder-spec-val">
                        {suggestion.tracking_kind === 'COUNT'
                          ? `Count (${suggestion.target_value || 1} ${suggestion.target_unit || 'units'})`
                          : 'Completion (Done / Not Done)'}
                      </span>
                    </div>
                  </div>
                </div>

                {error && <p className="ai-builder-error">{error}</p>}

                <div className="ai-builder-actions">
                  <button
                    className="ai-builder-text-btn"
                    onClick={() => setSuggestion(null)}
                  >
                    Edit prompt
                  </button>

                  <button
                    className="ai-builder-submit-btn ai-builder-submit-btn--confirm"
                    disabled={submitting}
                    onClick={handleConfirmCreate}
                  >
                    {submitting ? (
                      <>
                        <Loader2 size={16} className="ai-builder-spinner" />
                        <span>Creating Practice…</span>
                      </>
                    ) : (
                      <>
                        <Check size={16} strokeWidth={2.5} />
                        <span>Confirm & Create Habit</span>
                      </>
                    )}
                  </button>
                </div>
              </div>
            )}
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  )
}
