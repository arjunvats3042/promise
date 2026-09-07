import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import {
  Home,
  CheckSquare,
  CalendarDays,
  Flag,
  User as UserIcon,
  Search,
} from 'lucide-react'
import {
  backend,
  type UserProfile,
  type Goal,
  type GoalInvitePreview,
} from '../lib/backend'
import HomeView from '../components/app/HomeView'
import CommitmentsView from '../components/app/CommitmentsView'
import RoadmapView from '../components/app/RoadmapView'
import GoalsView from '../components/app/GoalsView'
import ProfileView from '../components/app/ProfileView'
import FloatingPromiseConciergeOrb from '../components/app/FloatingPromiseConciergeOrb'
import { CreateCommitmentModal } from '../components/app/CreateCommitmentModal'
import GoalDetailModal from '../components/app/GoalDetailModal'
import CountCheckInModal from '../components/app/CountCheckInModal'
import SearchModal from '../components/app/SearchModal'
import GoalInviteModal from '../components/app/GoalInviteModal'
import { AmbientAurora } from '../components/ui/AmbientAurora'
import './AppPage.css'

export type Tab = 'home' | 'commitments' | 'roadmap' | 'goals' | 'profile'

const TABS: { id: Tab; label: string; icon: React.ComponentType<{ size?: number }> }[] = [
  { id: 'home', label: 'Home', icon: Home },
  { id: 'commitments', label: 'Commitments', icon: CheckSquare },
  { id: 'roadmap', label: 'Roadmap', icon: CalendarDays },
  { id: 'goals', label: 'Goals', icon: Flag },
  { id: 'profile', label: 'Profile', icon: UserIcon },
]

export default function AppPage() {
  const navigate = useNavigate()
  const [user, setUser] = useState<UserProfile | null>(null)
  const [loading, setLoading] = useState(true)
  const [tab, setTab] = useState<Tab>('home')
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [isSearchOpen, setIsSearchOpen] = useState(false)
  const [detailGoal, setDetailGoal] = useState<Goal | null>(null)
  const [countGoal, setCountGoal] = useState<Goal | null>(null)
  const [invites, setInvites] = useState<GoalInvitePreview[]>([])
  const [isInviteOpen, setIsInviteOpen] = useState(false)
  const [viewRefreshKey, setViewRefreshKey] = useState(0)

  useEffect(() => {
    const token = localStorage.getItem('access_token')
    if (!token) {
      navigate('/login', { replace: true })
      return
    }
    backend.auth
      .me()
      .then(setUser)
      .catch(() => {
        navigate('/login', { replace: true })
      })
      .finally(() => setLoading(false))
  }, [navigate])

  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault()
        setIsSearchOpen((prev) => !prev)
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [])

  useEffect(() => {
    backend.goals
      .listInvites()
      .then((res) => {
        if (res && res.length > 0) {
          setInvites(res)
          setIsInviteOpen(true)
        }
      })
      .catch(() => {})
  }, [viewRefreshKey])

  if (loading) {
    return (
      <div className="app-page__loading">
        <div className="app-page__spinner" aria-label="Loading…" />
      </div>
    )
  }

  if (!user) return null

  return (
    <div className="app-page">
      <AmbientAurora />

      {/* Top Floating Glass Header */}
      <header className="app-page__header">
        <div className="app-page__header-inner">
          <motion.div
            className="app-page__wordmark-wrap"
            onClick={() => setTab('home')}
            whileHover={{ scale: 1.03 }}
            whileTap={{ scale: 0.97 }}
          >
            <span className="app-page__wordmark">Promise</span>
            <span className="app-page__submark">DESKTOP</span>
          </motion.div>

          <div className="app-page__header-right">
            {/* Spotlight Search Chip (Matching Android SearchRoute / Cmd+K) */}
            <motion.button
              className="app-page__search-chip"
              onClick={() => setIsSearchOpen(true)}
              whileHover={{ scale: 1.04 }}
              whileTap={{ scale: 0.96 }}
              title="Spotlight Search (Cmd+K)"
            >
              <Search size={13} className="app-page__search-icon" />
              <span>Search</span>
              <kbd className="app-page__search-kbd">⌘K</kbd>
            </motion.button>

            {/* Avatar */}
            <motion.button
              className="app-page__avatar"
              aria-label={user.name}
              onClick={() => setTab('profile')}
              whileHover={{ scale: 1.08 }}
              whileTap={{ scale: 0.92 }}
            >
              <span className="app-page__avatar-inner">
                {user.name ? user.name.charAt(0).toUpperCase() : 'P'}
              </span>
            </motion.button>
          </div>
        </div>
      </header>

      {/* Main Content Area */}
      <main className="app-page__main">
        <AnimatePresence mode="wait">
          <motion.div
            key={tab}
            className="app-page__view"
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -8 }}
            transition={{ duration: 0.25, ease: [0.16, 1, 0.3, 1] }}
          >
            {tab === 'home' && (
              <HomeView
                key={`home-${viewRefreshKey}`}
                user={user}
                onNavigateToCommitments={() => setTab('commitments')}
                onNavigateToGoals={() => setTab('goals')}
                onOpenGoalDetail={(g) => setDetailGoal(g)}
                onOpenCountCheckIn={(g) => setCountGoal(g)}
                onOpenSearch={() => setIsSearchOpen(true)}
                onOpenInvites={() => setIsInviteOpen(true)}
              />
            )}
            {tab === 'commitments' && <CommitmentsView key={`cmts-${viewRefreshKey}`} />}
            {tab === 'roadmap' && <RoadmapView />}
            {tab === 'goals' && (
              <GoalsView
                key={`goals-${viewRefreshKey}`}
                onOpenGoalDetail={(g) => setDetailGoal(g)}
                onOpenCountCheckIn={(g) => setCountGoal(g)}
              />
            )}
            {tab === 'profile' && <ProfileView user={user} />}
          </motion.div>
        </AnimatePresence>
      </main>

      {/* Apple Floating Island Bottom Dock */}
      <div className="app-page__dock-wrapper">
        <nav className="app-page__dock" aria-label="Main navigation">
          {TABS.map((t) => {
            const Icon = t.icon
            const isActive = tab === t.id
            return (
              <button
                key={t.id}
                className={`app-page__dock-btn ${
                  isActive ? 'app-page__dock-btn--active' : ''
                }`}
                onClick={() => setTab(t.id)}
                aria-current={isActive ? 'page' : undefined}
                aria-label={t.label}
              >
                {/* Sliding active pill indicator */}
                {isActive && (
                  <motion.div
                    className="app-page__dock-pill"
                    layoutId="activeDockPill"
                    transition={{ type: 'spring', stiffness: 450, damping: 35 }}
                  />
                )}
                <span className="app-page__dock-icon">
                  <Icon size={17} />
                </span>
                <span className="app-page__dock-label">{t.label}</span>
              </button>
            )
          })}
        </nav>
      </div>

      {/* Floating Promise AI Concierge Orb matching Android MainShell */}
      <FloatingPromiseConciergeOrb
        onNavigateTab={(t) => setTab(t)}
        onOpenCreatePromise={() => setIsCreateOpen(true)}
      />

      {/* Global Modals */}

      <CreateCommitmentModal
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
        onCreated={() => {
          setViewRefreshKey((k) => k + 1)
          setTab('commitments')
        }}
      />

      {/* Spotlight Global Search */}
      <SearchModal
        isOpen={isSearchOpen}
        onClose={() => setIsSearchOpen(false)}
        onSelectCommitment={() => setTab('commitments')}
        onSelectGoal={(g) => setDetailGoal(g)}
      />

      {/* Goal Detail Modal */}
      <GoalDetailModal
        goal={detailGoal}
        isOpen={Boolean(detailGoal)}
        onClose={() => setDetailGoal(null)}
        onCheckIn={async (id) => {
          await backend.goals.checkIn(id, { status: 'COMPLETED' })
          setViewRefreshKey((k) => k + 1)
        }}
        onCountCheckIn={(g) => setCountGoal(g)}
        onUpdated={() => setViewRefreshKey((k) => k + 1)}
      />

      {/* Count Habit Check-In Sheet */}
      <CountCheckInModal
        goal={countGoal}
        isOpen={Boolean(countGoal)}
        onClose={() => setCountGoal(null)}
        onSuccess={() => setViewRefreshKey((k) => k + 1)}
      />

      {/* Shared Goal Invite Modal */}
      <GoalInviteModal
        invites={invites}
        isOpen={isInviteOpen}
        onClose={() => setIsInviteOpen(false)}
        onAccepted={() => {
          setViewRefreshKey((k) => k + 1)
        }}
      />
    </div>
  )
}

