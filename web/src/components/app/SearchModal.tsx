import { useState, useEffect, useRef } from 'react'
import { motion } from 'framer-motion'
import {
  Search,
  X,
  CheckSquare,
  Target,
  Flame,
  Clock,
  ArrowRight,
} from 'lucide-react'
import { backend, type Commitment, type Goal } from '../../lib/backend'
import './SearchModal.css'

interface SearchModalProps {
  isOpen: boolean
  onClose: () => void
  onSelectCommitment: (commitment: Commitment) => void
  onSelectGoal: (goal: Goal) => void
}

export default function SearchModal({
  isOpen,
  onClose,
  onSelectCommitment,
  onSelectGoal,
}: SearchModalProps) {
  const [query, setQuery] = useState('')
  const [commitments, setCommitments] = useState<Commitment[]>([])
  const [goals, setGoals] = useState<Goal[]>([])
  const [loading, setLoading] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (isOpen) {
      setQuery('')
      setCommitments([])
      setGoals([])
      setTimeout(() => inputRef.current?.focus(), 50)
    }
  }, [isOpen])

  // Global hotkey Cmd+K or Ctrl+K is handled in AppPage, but Esc closes here
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape' && isOpen) {
        onClose()
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [isOpen, onClose])

  // Debounced live search
  useEffect(() => {
    if (!query.trim()) {
      setCommitments([])
      setGoals([])
      setLoading(false)
      return
    }

    const timer = setTimeout(() => {
      setLoading(true)
      backend.search
        .query(query)
        .then((res) => {
          setCommitments(res.commitments)
          setGoals(res.goals)
        })
        .catch(() => {})
        .finally(() => setLoading(false))
    }, 180)

    return () => clearTimeout(timer)
  }, [query])

  if (!isOpen) return null

  const hasResults = commitments.length > 0 || goals.length > 0
  const isQuerying = query.trim().length > 0

  return (
    <div className="search-modal__overlay" onClick={onClose}>
      <motion.div
        className="search-modal__card"
        onClick={(e) => e.stopPropagation()}
        initial={{ opacity: 0, scale: 0.96, y: -20 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.96, y: -20 }}
        transition={{ type: 'spring', stiffness: 450, damping: 32 }}
      >
        {/* Search Input Bar */}
        <div className="search-modal__input-row">
          <Search size={18} className="search-modal__icon" />
          <input
            ref={inputRef}
            type="text"
            className="search-modal__input"
            placeholder="Search commitments, practices, and circles…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          {query && (
            <button
              className="search-modal__clear"
              onClick={() => setQuery('')}
              aria-label="Clear search"
            >
              <X size={14} />
            </button>
          )}
          <kbd className="search-modal__kbd">ESC</kbd>
        </div>

        {/* Results Body */}
        <div className="search-modal__body">
          {loading && (
            <div className="search-modal__loading">
              <div className="search-modal__spinner" />
              <span>Searching across Promise…</span>
            </div>
          )}

          {!loading && isQuerying && !hasResults && (
            <div className="search-modal__empty">
              <p>No results found for “{query}”</p>
              <span>Try searching for a task name, deadline, or habit keyword.</span>
            </div>
          )}

          {!loading && !isQuerying && (
            <div className="search-modal__hint-box">
              <span className="search-modal__hint-title">QUICK SPOTLIGHT SEARCH</span>
              <p>Type to search across all discrete promises, daily rituals, and group circles.</p>
            </div>
          )}

          {/* Commitments Section */}
          {commitments.length > 0 && (
            <div className="search-modal__section">
              <div className="search-modal__section-head">
                <CheckSquare size={13} />
                <span>Promises & Commitments ({commitments.length})</span>
              </div>
              <div className="search-modal__list">
                {commitments.map((c) => (
                  <div
                    key={c.id}
                    className="search-result-item"
                    onClick={() => {
                      onSelectCommitment(c)
                      onClose()
                    }}
                  >
                    <div className="search-result-item__left">
                      <span
                        className={`search-result-item__status search-result-item__status--${c.status.toLowerCase()}`}
                      >
                        {c.status}
                      </span>
                      {c.is_overdue && (
                        <span className="search-result-item__overdue">OVERDUE</span>
                      )}
                      <span className="search-result-item__title">{c.title}</span>
                    </div>

                    <div className="search-result-item__right">
                      {c.due_at && (
                        <span className="search-result-item__due">
                          <Clock size={11} />
                          <span>{new Date(c.due_at).toLocaleDateString()}</span>
                        </span>
                      )}
                      <ArrowRight size={13} className="search-result-item__arrow" />
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Goals Section */}
          {goals.length > 0 && (
            <div className="search-modal__section">
              <div className="search-modal__section-head">
                <Target size={13} />
                <span>Goals & Daily Practices ({goals.length})</span>
              </div>
              <div className="search-modal__list">
                {goals.map((g) => (
                  <div
                    key={g.id}
                    className="search-result-item"
                    onClick={() => {
                      onSelectGoal(g)
                      onClose()
                    }}
                  >
                    <div className="search-result-item__left">
                      <span className="search-result-item__streak">
                        <Flame size={12} />
                        <span>{g.current_streak}d</span>
                      </span>
                      {g.is_shared && (
                        <span className="search-result-item__circle-badge">Circle</span>
                      )}
                      <span className="search-result-item__title">{g.title}</span>
                    </div>

                    <div className="search-result-item__right">
                      <span className="search-result-item__due">
                        {g.recurrence_kind === 'DAILY' ? 'Daily' : g.recurrence_kind}
                      </span>
                      <ArrowRight size={13} className="search-result-item__arrow" />
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </motion.div>
    </div>
  )
}
