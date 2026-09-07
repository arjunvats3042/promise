import { useState, useEffect, useMemo } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Check, Clock, Plus, Search, PauseCircle, CheckCircle2 } from 'lucide-react'
import {
  backend,
  type Commitment,
} from '../../lib/backend'
import { triggerCelebration } from '../ui/celebrate'
import { CreateCommitmentModal } from './CreateCommitmentModal'
import { SnoozeModal } from './SnoozeModal'
import CommitmentDetailModal from './CommitmentDetailModal'
import './CommitmentsView.css'

type FilterTab = 'OPEN' | 'OVERDUE' | 'TODAY' | 'UPCOMING' | 'DONE'

const STATUS_TABS: { id: FilterTab; label: string }[] = [
  { id: 'OPEN', label: 'Active' },
  { id: 'OVERDUE', label: 'Overdue' },
  { id: 'TODAY', label: 'Today' },
  { id: 'UPCOMING', label: 'Upcoming' },
  { id: 'DONE', label: 'Done' },
]

export default function CommitmentsView() {
  const [commitments, setCommitments] = useState<Commitment[]>([])
  const [loading, setLoading] = useState(true)
  const [filter, setFilter] = useState<FilterTab>('OPEN')
  const [searchQuery, setSearchQuery] = useState('')

  // Modals state
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [snoozeTarget, setSnoozeTarget] = useState<Commitment | null>(null)
  const [detailTarget, setDetailTarget] = useState<Commitment | null>(null)

  function fetchCommitments(f: FilterTab) {
    setLoading(true)
    let params: Record<string, any> = {}
    if (f === 'OVERDUE') {
      params = { is_overdue: true }
    } else if (f === 'DONE') {
      params = { status: 'COMPLETED' }
    } else {
      // OPEN, TODAY, UPCOMING all fetch open items
      params = { status: 'PENDING' }
    }
    backend.commitments
      .list(params)
      .then((r) => setCommitments(r.results))
      .catch(() => {})
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    fetchCommitments(filter)
  }, [filter])

  // Filtered by search query and date bucketing (matching CommitmentListBucketing.kt)
  const filteredCommitments = useMemo(() => {
    const now = new Date()
    const todayStr = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`

    let items = commitments

    if (filter === 'TODAY') {
      items = items.filter((c) => {
        if (!c.due_at) return false
        const d = new Date(c.due_at)
        const cDateStr = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
        return cDateStr === todayStr
      })
    } else if (filter === 'UPCOMING') {
      items = items.filter((c) => {
        if (c.is_overdue || !c.due_at) return false
        const d = new Date(c.due_at)
        const cDateStr = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
        return cDateStr > todayStr
      })
    }

    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase()
      items = items.filter(
        (c) =>
          c.title.toLowerCase().includes(q) ||
          (c.description && c.description.toLowerCase().includes(q))
      )
    }

    // Sort matching CommitmentListBucketing.sortOpen
    return [...items].sort((a, b) => {
      if (a.is_overdue !== b.is_overdue) return a.is_overdue ? -1 : 1
      const dueA = a.due_at || '9999'
      const dueB = b.due_at || '9999'
      if (dueA !== dueB) return dueA.localeCompare(dueB)
      return new Date(b.created_at).getTime() - new Date(a.created_at).getTime()
    })
  }, [commitments, filter, searchQuery])

  async function handleComplete(id: string, e?: React.MouseEvent) {
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
      setCommitments((prev) =>
        prev.map((c) =>
          c.id === id ? { ...c, status: 'COMPLETED', completed_at: new Date().toISOString() } : c
        )
      )
    } catch {
      /* ignore */
    }
  }

  async function handleCancel(id: string) {
    try {
      await backend.commitments.cancel(id)
      setCommitments((prev) => prev.filter((c) => c.id !== id))
    } catch {
      /* ignore */
    }
  }

  async function handleWait(id: string) {
    try {
      const updated = await backend.commitments.wait(id)
      setCommitments((prev) => prev.map((c) => (c.id === id ? updated : c)))
    } catch {
      alert('Could not update status to waiting.')
    }
  }

  async function handleUnsnooze(id: string) {
    try {
      const updated = await backend.commitments.unsnooze(id)
      setCommitments((prev) => prev.map((c) => (c.id === id ? updated : c)))
    } catch {
      alert('Could not unsnooze commitment.')
    }
  }

  return (
    <div className="cmts-view">
      {/* Top Banner Header */}
      <div className="cmts-view__header">
        <div className="cmts-view__header-text">
          <h1 className="cmts-view__heading">Promises & Commitments</h1>
          <p className="cmts-view__subheading">
            Discrete promises you make to yourself or others with radical accountability.
          </p>
        </div>

        <motion.button
          className="cmts-view__new-btn"
          onClick={() => setIsCreateOpen(true)}
          whileHover={{ scale: 1.04 }}
          whileTap={{ scale: 0.96 }}
        >
          <Plus size={16} />
          <span>New Promise</span>
        </motion.button>
      </div>

      {/* Control Bar: Search & Filter Chips */}
      <div className="cmts-view__control-bar">
        <div className="cmts-view__search-wrap">
          <Search size={14} className="cmts-view__search-icon" />
          <input
            type="text"
            className="cmts-view__search-input"
            placeholder="Search promises…"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
          {searchQuery && (
            <button
              className="cmts-view__search-clear"
              onClick={() => setSearchQuery('')}
            >
              ×
            </button>
          )}
        </div>

        <div className="cmts-view__filters" role="tablist">
          {STATUS_TABS.map((tab) => {
            const isActive = filter === tab.id
            return (
              <button
                key={tab.id}
                role="tab"
                aria-selected={isActive}
                className={`cmts-view__filter ${
                  isActive ? 'cmts-view__filter--active' : ''
                }`}
                onClick={() => setFilter(tab.id)}
              >
                {isActive && (
                  <motion.div
                    className="cmts-view__filter-pill"
                    layoutId="activeCmtFilter"
                    transition={{ type: 'spring', stiffness: 450, damping: 35 }}
                  />
                )}
                <span className="cmts-view__filter-text">{tab.label}</span>
              </button>
            )
          })}
        </div>
      </div>

      {/* Commitments List */}
      {loading ? (
        <div className="cmts-view__skeleton">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="cmts-view__skeleton-row" />
          ))}
        </div>
      ) : filteredCommitments.length === 0 ? (
        <div className="cmts-view__empty">
          <div className="cmts-view__empty-icon">
            <CheckCircle2 size={36} strokeWidth={1.5} />
          </div>
          <p className="cmts-view__empty-heading">
            {filter === 'DONE'
              ? 'No completed promises yet'
              : `No ${filter.toLowerCase()} promises`}
          </p>
          <p className="cmts-view__empty-sub">
            {searchQuery
              ? 'No promises matched your search term.'
              : filter === 'OPEN'
              ? 'You’re all caught up on active promises!'
              : filter === 'TODAY'
              ? 'No promises scheduled for today.'
              : filter === 'UPCOMING'
              ? 'No upcoming promises on the horizon.'
              : filter === 'OVERDUE'
              ? 'Zero overdue promises — excellent discipline!'
              : 'Complete a promise to see it archived here.'}
          </p>
          <button
            className="cmts-view__empty-cta"
            onClick={() => setIsCreateOpen(true)}
          >
            <Plus size={14} />
            <span>Make a New Promise</span>
          </button>
        </div>
      ) : (
        <div className="cmts-view__list">
          <AnimatePresence initial={false}>
            {filteredCommitments.map((c) => {
              const isDone = c.status === 'COMPLETED'
              const isOverdue = c.is_overdue
              const isWaiting = c.status === 'WAITING'
              const isSnoozed = c.status === 'SNOOZED'

              return (
                <motion.div
                  key={c.id}
                  className={`cmts-view__item ${
                    isOverdue ? 'cmts-view__item--overdue' : ''
                  } ${isDone ? 'cmts-view__item--done' : ''}`}
                  layout
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, scale: 0.95 }}
                  transition={{ duration: 0.2 }}
                  onClick={() => setDetailTarget(c)}
                >
                  {/* Left Checkmark Button */}
                  <motion.button
                    className={`cmts-view__check-btn ${
                      isDone ? 'cmts-view__check-btn--checked' : ''
                    }`}
                    onClick={(e) => {
                      e.stopPropagation()
                      if (!isDone) handleComplete(c.id, e)
                    }}
                    whileHover={{ scale: 1.15 }}
                    whileTap={{ scale: 0.9 }}
                    aria-label="Toggle completed"
                    title={isDone ? 'Completed' : 'Mark complete'}
                  >
                    {isDone && <Check size={13} strokeWidth={3} />}
                  </motion.button>

                  {/* Main Content */}
                  <div className="cmts-view__item-main">
                    <div className="cmts-view__item-top">
                      <span className="cmts-view__item-title">{c.title}</span>
                      {isOverdue && (
                        <span className="cmts-view__badge cmts-view__badge--overdue">
                          Overdue
                        </span>
                      )}
                      {isWaiting && (
                        <span className="cmts-view__badge cmts-view__badge--waiting">
                          Waiting
                        </span>
                      )}
                      {isSnoozed && (
                        <span className="cmts-view__badge cmts-view__badge--snoozed">
                          Snoozed
                        </span>
                      )}
                    </div>

                    {c.description && (
                      <p className="cmts-view__item-desc">{c.description}</p>
                    )}

                    <div className="cmts-view__item-meta">
                      {c.due_at && (
                        <span className="cmts-view__meta-tag">
                          📅{' '}
                          {new Date(c.due_at).toLocaleDateString('en-US', {
                            month: 'short',
                            day: 'numeric',
                            hour:
                              c.due_precision === 'DATETIME'
                                ? 'numeric'
                                : undefined,
                            minute:
                              c.due_precision === 'DATETIME'
                                ? '2-digit'
                                : undefined,
                          })}
                        </span>
                      )}

                      {c.snoozed_until && (
                        <span className="cmts-view__meta-tag cmts-view__meta-tag--snooze">
                          <Clock size={11} />
                          <span>
                            Until{' '}
                            {new Date(c.snoozed_until).toLocaleDateString('en-US', {
                              month: 'short',
                              day: 'numeric',
                            })}
                          </span>
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Quick Action Button */}
                  <div
                    className="cmts-view__item-actions"
                    onClick={(e) => e.stopPropagation()}
                  >
                    {c.status === 'PENDING' && (
                      <motion.button
                        className="cmts-view__action-snooze"
                        onClick={() => setSnoozeTarget(c)}
                        title="Intentional Snooze"
                        whileHover={{ scale: 1.08 }}
                        whileTap={{ scale: 0.92 }}
                      >
                        <Clock size={13} />
                        <span>Snooze</span>
                      </motion.button>
                    )}
                    {c.status === 'WAITING' && (
                      <span className="cmts-view__status-chip">
                        <PauseCircle size={12} />
                        <span>Blocked</span>
                      </span>
                    )}
                  </div>
                </motion.div>
              )
            })}
          </AnimatePresence>
        </div>
      )}

      {/* Dedicated Modals */}
      <CreateCommitmentModal
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
        onCreated={() => {
          fetchCommitments(filter)
        }}
      />

      <SnoozeModal
        isOpen={Boolean(snoozeTarget)}
        onClose={() => setSnoozeTarget(null)}
        commitmentId={snoozeTarget?.id || ''}
        commitmentTitle={snoozeTarget?.title || ''}
        onSnoozed={() => {
          fetchCommitments(filter)
        }}
      />

      <CommitmentDetailModal
        isOpen={Boolean(detailTarget)}
        commitment={detailTarget}
        onClose={() => setDetailTarget(null)}
        onComplete={handleComplete}
        onSnooze={(c) => setSnoozeTarget(c)}
        onWait={handleWait}
        onUnsnooze={handleUnsnooze}
        onCancel={handleCancel}
      />
    </div>
  )
}
