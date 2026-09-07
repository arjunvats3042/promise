import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import {
  Shield,
  Palette,
  Bell,
  HelpCircle,
  ChevronDown,
  ChevronUp,
  LogOut,
  Trash2,
  CheckCircle2,
  Smartphone,
  Globe,
  Server,
  Clock,
  Sparkles,
  AlertTriangle,
} from 'lucide-react'
import { backend, type UserProfile, type AuthSession } from '../../lib/backend'
import './ProfileView.css'

interface Props {
  user: UserProfile
}

const PROMISE_FAQS = [
  {
    question: 'What is the difference between a Commitment and a Goal?',
    answer:
      'Commitments are one-time tasks with a specific due date (e.g., "Submit design review by Friday"). Goals are recurring daily or weekly practices designed to build long-term consistency (e.g., "Read 20 pages daily", "Gym 3x/week").',
  },
  {
    question: 'How does "Thought → Promise" (AI) work?',
    answer:
      'Type or paste an unstructured brain dump (e.g., "call mom tomorrow at 11pm and workout 4 days a week"). AI automatically decomposes it into clear commitments and recurring goals with accurate due dates. Nothing is created until you review and confirm.',
  },
  {
    question: 'How do Shared Goals work?',
    answer:
      'You can convert any goal into a shared goal and invite partners by searching their Promise email address. Once they accept the in-app invite, all members track shared momentum and check-ins together in a private group room.',
  },
  {
    question: 'Can I use Promise and check in without internet?',
    answer:
      'Yes! Promise is local-first. Today\'s commitments, habit check-ins, and the home screen widgets work instantly offline. Your actions are saved locally and sync quietly to the cloud when connectivity returns.',
  },
  {
    question: 'When does Promise send notifications?',
    answer:
      'Promise only sends quiet, intentional reminders: a morning practice summary, timely alerts when commitments are due, evening streak protection, and shared goal chat messages. You can customize all notification preferences anytime above.',
  },
  {
    question: 'What data is shared with AI?',
    answer:
      'Only the specific thought or prompt you enter for AI refinement is sent securely to Google Gemini. Your private account details, email, and unrelated commitments are never shared or used for model training.',
  },
  {
    question: 'How do I delete my account?',
    answer:
      'Tap "Delete account" at the bottom of this screen. Your account will be permanently anonymized, all active commitments cancelled, and your session securely signed out.',
  },
]

export default function ProfileView({ user }: Props) {
  const navigate = useNavigate()
  const [completedCount, setCompletedCount] = useState<number | null>(null)
  const [activeGoalsCount, setActiveGoalsCount] = useState<number | null>(null)
  const [pendingCount, setPendingCount] = useState<number | null>(null)
  const [sessions, setSessions] = useState<AuthSession[]>([])
  const [loadingSessions, setLoadingSessions] = useState(true)

  // Settings states
  const [selectedTheme, setSelectedTheme] = useState<'amoled' | 'graphite' | 'midnight'>('amoled')
  const [notificationsEnabled, setNotificationsEnabled] = useState(true)
  const [morningSummary, setMorningSummary] = useState(true)
  const [expandedFaq, setExpandedFaq] = useState<number | null>(0)
  const [showDeleteModal, setShowDeleteModal] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)

  useEffect(() => {
    // Fetch live user stats
    backend.commitments
      .list({ status: 'COMPLETED' })
      .then((res) => setCompletedCount(res.count))
      .catch(() => {})

    backend.commitments
      .list({ status: 'PENDING' })
      .then((res) => setPendingCount(res.count))
      .catch(() => {})

    backend.goals
      .list({ status: 'ACTIVE' })
      .then((res) => setActiveGoalsCount(res.count))
      .catch(() => {})

    // Fetch live sessions
    backend.auth
      .sessions()
      .then(setSessions)
      .catch(() => {})
      .finally(() => setLoadingSessions(false))
  }, [])

  async function handleRevoke(sessionId: string) {
    try {
      await backend.auth.revokeSession(sessionId)
      setSessions((prev) => prev.filter((s) => s.id !== sessionId))
    } catch {
      alert('Could not revoke session.')
    }
  }

  async function handleLogout() {
    await backend.auth.logout()
    navigate('/login', { replace: true })
  }

  async function handleDeleteAccount() {
    setIsDeleting(true)
    try {
      await backend.auth.deleteAccount()
      navigate('/login', { replace: true })
    } catch {
      alert('Could not delete account. Please try again.')
      setIsDeleting(false)
      setShowDeleteModal(false)
    }
  }

  return (
    <div className="profile-view">
      {/* Avatar + identity */}
      <motion.div
        className="profile-view__identity"
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5 }}
      >
        <div className="profile-view__avatar" aria-hidden="true">
          {user.name ? user.name.charAt(0).toUpperCase() : 'P'}
        </div>
        <div className="profile-view__user-info">
          <h1 className="profile-view__name">{user.name}</h1>
          <p className="profile-view__email">{user.email}</p>
          <div className="profile-view__badges">
            <span className="profile-view__badge profile-view__badge--accent">Active User</span>
            {user.email_verified && (
              <span className="profile-view__badge profile-view__badge--verified">
                <CheckCircle2 size={11} /> Verified
              </span>
            )}
          </div>
        </div>
      </motion.div>

      {/* Live Stats row */}
      <div className="profile-view__stats">
        <div className="profile-view__stat">
          <span className="profile-view__stat-num">
            {completedCount !== null ? completedCount : '—'}
          </span>
          <span className="profile-view__stat-label">Promises Kept</span>
        </div>
        <div className="profile-view__stat">
          <span className="profile-view__stat-num">
            {pendingCount !== null ? pendingCount : '—'}
          </span>
          <span className="profile-view__stat-label">Pending</span>
        </div>
        <div className="profile-view__stat">
          <span className="profile-view__stat-num">
            {activeGoalsCount !== null ? activeGoalsCount : '—'}
          </span>
          <span className="profile-view__stat-label">Active Goals</span>
        </div>
      </div>

      {/* Appearance / Theme Selector (Matching Android AMOLED/Graphite) */}
      <div className="profile-view__section">
        <div className="profile-view__section-header">
          <Palette size={14} className="profile-view__section-icon" />
          <span className="profile-view__section-label">Appearance & Themes</span>
        </div>
        <div className="profile-view__theme-grid">
          {[
            { id: 'amoled', name: 'AMOLED Pure Black', color: '#000000', border: '#222' },
            { id: 'graphite', name: 'Graphite Slate', color: '#12141a', border: '#2a2d36' },
            { id: 'midnight', name: 'Midnight Obsidian', color: '#0b0f19', border: '#1e293b' },
          ].map((theme) => (
            <button
              key={theme.id}
              className={`profile-view__theme-card ${
                selectedTheme === theme.id ? 'profile-view__theme-card--selected' : ''
              }`}
              onClick={() => setSelectedTheme(theme.id as any)}
            >
              <div
                className="profile-view__theme-swatch"
                style={{ background: theme.color, borderColor: theme.border }}
              />
              <span className="profile-view__theme-name">{theme.name}</span>
            </button>
          ))}
        </div>
      </div>

      {/* Notifications */}
      <div className="profile-view__section">
        <div className="profile-view__section-header">
          <Bell size={14} className="profile-view__section-icon" />
          <span className="profile-view__section-label">Quiet Notifications</span>
        </div>
        <div className="profile-view__card">
          <label className="profile-view__toggle-row">
            <div>
              <span className="profile-view__row-label">Overdue & Due Alerts</span>
              <p className="profile-view__row-hint">Notify only when commitments need attention</p>
            </div>
            <input
              type="checkbox"
              checked={notificationsEnabled}
              onChange={(e) => setNotificationsEnabled(e.target.checked)}
              className="profile-view__checkbox"
            />
          </label>
          <label className="profile-view__toggle-row">
            <div>
              <span className="profile-view__row-label">Morning Ritual Summary</span>
              <p className="profile-view__row-hint">Daily 8:00 AM momentum and practice briefing</p>
            </div>
            <input
              type="checkbox"
              checked={morningSummary}
              onChange={(e) => setMorningSummary(e.target.checked)}
              className="profile-view__checkbox"
            />
          </label>
        </div>
      </div>

      {/* Account Details */}
      <div className="profile-view__section">
        <div className="profile-view__section-header">
          <Shield size={14} className="profile-view__section-icon" />
          <span className="profile-view__section-label">Account Details</span>
        </div>

        <div className="profile-view__card">
          <div className="profile-view__row">
            <span className="profile-view__row-label">
              <Clock size={13} style={{ marginRight: 6 }} /> Timezone
            </span>
            <span className="profile-view__row-value">{user.timezone || 'Asia/Kolkata'}</span>
          </div>

          <div className="profile-view__row">
            <span className="profile-view__row-label">
              <Globe size={13} style={{ marginRight: 6 }} /> Client Platform
            </span>
            <span className="profile-view__row-value">Web Application (Promise Studio)</span>
          </div>

          <div className="profile-view__row">
            <span className="profile-view__row-label">
              <Server size={13} style={{ marginRight: 6 }} /> Backend Engine
            </span>
            <span className="profile-view__row-value">Railway Hikari (Python / Django)</span>
          </div>

          <div className="profile-view__row">
            <span className="profile-view__row-label">
              <Sparkles size={13} style={{ marginRight: 6 }} /> AI Integration
            </span>
            <span className="profile-view__row-value">Google Gemini 2.5 Flash</span>
          </div>
        </div>
      </div>

      {/* Active Sessions */}
      <div className="profile-view__section">
        <div className="profile-view__section-header">
          <Smartphone size={14} className="profile-view__section-icon" />
          <span className="profile-view__section-label">Active Devices & Sessions</span>
        </div>

        <div className="profile-view__card">
          {loadingSessions ? (
            <p className="profile-view__empty-note">Loading sessions…</p>
          ) : sessions.length === 0 ? (
            <div className="profile-view__row">
              <span className="profile-view__row-label">Current Browser Session</span>
              <span className="profile-view__badge profile-view__badge--accent">Active Now</span>
            </div>
          ) : (
            sessions.map((s) => (
              <div key={s.id} className="profile-view__row">
                <div>
                  <span className="profile-view__row-label">
                    {s.device_name || 'Browser Device'} {s.is_current ? '(Current)' : ''}
                  </span>
                  <p className="profile-view__row-sub">
                    {s.ip_address} · {new Date(s.last_used_at).toLocaleDateString()}
                  </p>
                </div>
                {!s.is_current && (
                  <button
                    className="profile-view__revoke-btn"
                    onClick={() => handleRevoke(s.id)}
                  >
                    Revoke
                  </button>
                )}
              </div>
            ))
          )}
        </div>
      </div>

      {/* FAQ Section (Matching Android PROMISE_FAQS) */}
      <div className="profile-view__section">
        <div className="profile-view__section-header">
          <HelpCircle size={14} className="profile-view__section-icon" />
          <span className="profile-view__section-label">Frequently Asked Questions</span>
        </div>

        <div className="profile-view__faq-list">
          {PROMISE_FAQS.map((faq, index) => {
            const isExpanded = expandedFaq === index
            return (
              <div key={index} className="profile-view__faq-item">
                <button
                  className="profile-view__faq-question"
                  onClick={() => setExpandedFaq(isExpanded ? null : index)}
                >
                  <span>{faq.question}</span>
                  {isExpanded ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                </button>
                <AnimatePresence>
                  {isExpanded && (
                    <motion.div
                      className="profile-view__faq-answer"
                      initial={{ opacity: 0, height: 0 }}
                      animate={{ opacity: 1, height: 'auto' }}
                      exit={{ opacity: 0, height: 0 }}
                      transition={{ duration: 0.2 }}
                    >
                      <p>{faq.answer}</p>
                    </motion.div>
                  )}
                </AnimatePresence>
              </div>
            )
          })}
        </div>
      </div>

      {/* Sign out */}
      <div className="profile-view__actions">
        <button className="profile-view__logout-btn" onClick={handleLogout}>
          <LogOut size={16} />
          <span>Sign Out</span>
        </button>

        <button
          className="profile-view__delete-btn"
          onClick={() => setShowDeleteModal(true)}
        >
          <Trash2 size={15} />
          <span>Delete Account</span>
        </button>
      </div>

      <p className="profile-view__footer">
        Promise Studio · v1.0.0 · Local-First Radical Accountability
      </p>

      {/* Delete Confirmation Modal */}
      <AnimatePresence>
        {showDeleteModal && (
          <div className="profile-view__modal-overlay" onClick={() => setShowDeleteModal(false)}>
            <motion.div
              className="profile-view__modal"
              initial={{ scale: 0.95, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.95, opacity: 0 }}
              onClick={(e) => e.stopPropagation()}
            >
              <div className="profile-view__modal-icon-wrap">
                <AlertTriangle size={24} className="profile-view__modal-icon" />
              </div>
              <h2 className="profile-view__modal-title">Delete Account?</h2>
              <p className="profile-view__modal-desc">
                This action is irreversible. All your promises, habits, and streak data will be
                permanently purged.
              </p>
              <div className="profile-view__modal-actions">
                <button
                  className="profile-view__modal-cancel"
                  onClick={() => setShowDeleteModal(false)}
                  disabled={isDeleting}
                >
                  Cancel
                </button>
                <button
                  className="profile-view__modal-confirm"
                  onClick={handleDeleteAccount}
                  disabled={isDeleting}
                >
                  {isDeleting ? 'Deleting…' : 'Yes, Delete Account'}
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </div>
  )
}

