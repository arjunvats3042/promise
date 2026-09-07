import { useState, useEffect, useRef } from 'react'
import { motion } from 'framer-motion'
import {
  X,
  Send,
  Sparkles,
  Users,
  TrendingUp,
  MessageSquare,
} from 'lucide-react'
import { backend, type Goal } from '../../lib/backend'
import { triggerCelebration } from '../ui/celebrate'
import './GoalChatModal.css'

interface GoalChatModalProps {
  goal: Goal | null
  isOpen: boolean
  onClose: () => void
}

interface MessageItem {
  id: string
  goal_id: string
  sender_user_id: string
  sender_name: string
  sender_avatar_url?: string | null
  body: string
  created_at: string
  is_mine?: boolean
}

export default function GoalChatModal({ goal, isOpen, onClose }: GoalChatModalProps) {
  const [messages, setMessages] = useState<MessageItem[]>([])
  const [inputText, setInputText] = useState('')
  const [sending, setSending] = useState(false)
  const [activeTab, setActiveTab] = useState<'chat' | 'reflection'>('chat')
  const [reflection, setReflection] = useState<{
    period_start: string
    period_end: string
    completed: number
    expected: number
    percentage: number
    reflection_text: string
    trend_text: string
  } | null>(null)
  const [loadingReflection, setLoadingReflection] = useState(false)
  const [chatSummary, setChatSummary] = useState<{ summary: string; key_points?: string[] } | null>(null)
  const [loadingSummary, setLoadingSummary] = useState(false)

  const messagesEndRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!isOpen || !goal) return

    // Load initial messages
    backend.goals.chatMessages(goal.id).then((msgs) => {
      setMessages(msgs)
      scrollToBottom()
    })
  }, [isOpen, goal])

  function scrollToBottom() {
    setTimeout(() => {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
    }, 100)
  }

  async function handleSend(e: React.FormEvent) {
    e.preventDefault()
    if (!inputText.trim() || !goal || sending) return

    const tempText = inputText.trim()
    setInputText('')
    setSending(true)

    // Optimistic message
    const optimisticMsg: MessageItem = {
      id: 'opt-' + Date.now(),
      goal_id: goal.id,
      sender_user_id: 'me',
      sender_name: 'You',
      body: tempText,
      created_at: new Date().toISOString(),
      is_mine: true,
    }
    setMessages((prev) => [...prev, optimisticMsg])
    scrollToBottom()

    try {
      await backend.goals.sendChatMessage(goal.id, tempText)
    } catch {
      // Keep optimistic message or retry
    } finally {
      setSending(false)
    }
  }

  async function loadWeeklyReflection() {
    if (!goal || loadingReflection) return
    setLoadingReflection(true)
    try {
      const data = await backend.goals.weeklyReflection(goal.id)
      setReflection(data)
      triggerCelebration({ particleCount: 24, spread: 45 })
    } catch {
      alert('Could not load weekly reflection.')
    } finally {
      setLoadingReflection(false)
    }
  }

  async function loadChatSummary() {
    if (!goal || loadingSummary) return
    setLoadingSummary(true)
    try {
      const data = await backend.goals.chatSummary(goal.id)
      setChatSummary(data)
      triggerCelebration({ particleCount: 20, spread: 35 })
    } catch {
      alert('Could not generate chat summary.')
    } finally {
      setLoadingSummary(false)
    }
  }

  if (!isOpen || !goal) return null

  return (
    <div className="goal-chat__overlay" onClick={onClose}>
      <motion.div
        className="goal-chat__card"
        onClick={(e) => e.stopPropagation()}
        initial={{ opacity: 0, scale: 0.95, y: 15 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.95, y: 15 }}
        transition={{ type: 'spring', stiffness: 400, damping: 30 }}
      >
        {/* Modal Header */}
        <div className="goal-chat__header">
          <div className="goal-chat__title-wrap">
            <div className="goal-chat__badge-row">
              <span className="goal-chat__circle-badge">
                <Users size={12} />
                <span>Accountability Circle</span>
              </span>
              <span className="goal-chat__streak-badge">
                🔥 {goal.current_streak} days
              </span>
            </div>
            <h2 className="goal-chat__title">{goal.title}</h2>
          </div>

          <button className="goal-chat__close-btn" onClick={onClose} aria-label="Close modal">
            <X size={16} />
          </button>
        </div>

        {/* Navigation Tabs: Live Chat vs AI Reflection */}
        <div className="goal-chat__tabs">
          <button
            className={`goal-chat__tab ${activeTab === 'chat' ? 'goal-chat__tab--active' : ''}`}
            onClick={() => setActiveTab('chat')}
          >
            <MessageSquare size={14} />
            <span>Circle Feed</span>
          </button>

          <button
            className={`goal-chat__tab ${activeTab === 'reflection' ? 'goal-chat__tab--active' : ''}`}
            onClick={() => {
              setActiveTab('reflection')
              if (!reflection) loadWeeklyReflection()
            }}
          >
            <Sparkles size={14} />
            <span>AI Weekly Reflection</span>
          </button>
        </div>

        {/* Tab 1: Live Chat Thread */}
        {activeTab === 'chat' && (
          <div className="goal-chat__content">
            {/* AI Summary Banner */}
            {chatSummary ? (
              <div className="goal-chat__summary-box">
                <div className="goal-chat__summary-head">
                  <Sparkles size={13} className="goal-chat__sparkle-icon" />
                  <span>GEMINI CIRCLE SUMMARY</span>
                </div>
                <p className="goal-chat__summary-body">{chatSummary.summary}</p>
                {chatSummary.key_points && chatSummary.key_points.length > 0 && (
                  <ul className="goal-chat__summary-points">
                    {chatSummary.key_points.map((pt, i) => (
                      <li key={i}>{pt}</li>
                    ))}
                  </ul>
                )}
              </div>
            ) : (
              <div className="goal-chat__summary-prompt">
                <span>Catch up on group check-in discussions with Gemini.</span>
                <button
                  className="goal-chat__ai-summary-btn"
                  onClick={loadChatSummary}
                  disabled={loadingSummary}
                >
                  <Sparkles size={12} />
                  <span>{loadingSummary ? 'Analyzing…' : 'Summarize Chat'}</span>
                </button>
              </div>
            )}

            {/* Messages Feed */}
            <div className="goal-chat__messages">
              {messages.length === 0 ? (
                <div className="goal-chat__empty-msg">
                  <p>No messages yet in this accountability circle.</p>
                  <span>Say hello or share your check-in progress!</span>
                </div>
              ) : (
                messages.map((m) => {
                  const isMine = m.is_mine || m.sender_name === 'You'
                  return (
                    <div
                      key={m.id}
                      className={`goal-chat__bubble-wrap ${
                        isMine ? 'goal-chat__bubble-wrap--mine' : ''
                      }`}
                    >
                      {!isMine && (
                        <div className="goal-chat__bubble-sender">
                          <span className="goal-chat__avatar">
                            {m.sender_name.charAt(0).toUpperCase()}
                          </span>
                          <span className="goal-chat__sender-name">{m.sender_name}</span>
                        </div>
                      )}
                      <div
                        className={`goal-chat__bubble ${
                          isMine ? 'goal-chat__bubble--mine' : ''
                        }`}
                      >
                        <p className="goal-chat__bubble-text">{m.body}</p>
                        <span className="goal-chat__bubble-time">
                          {new Date(m.created_at).toLocaleTimeString('en-US', {
                            hour: 'numeric',
                            minute: '2-digit',
                          })}
                        </span>
                      </div>
                    </div>
                  )
                })
              )}
              <div ref={messagesEndRef} />
            </div>

            {/* Input Bar */}
            <form onSubmit={handleSend} className="goal-chat__input-bar">
              <input
                type="text"
                className="goal-chat__input"
                placeholder="Message the circle…"
                value={inputText}
                onChange={(e) => setInputText(e.target.value)}
              />
              <button
                type="submit"
                className="goal-chat__send-btn"
                disabled={!inputText.trim() || sending}
                aria-label="Send message"
              >
                <Send size={15} />
              </button>
            </form>
          </div>
        )}

        {/* Tab 2: AI Weekly Reflection */}
        {activeTab === 'reflection' && (
          <div className="goal-chat__reflection-tab">
            {loadingReflection ? (
              <div className="goal-chat__reflection-loading">
                <Sparkles size={28} className="goal-chat__spin-sparkle" />
                <p>Generating deep weekly practice analysis with Gemini…</p>
              </div>
            ) : reflection ? (
              <div className="goal-chat__reflection-card">
                <div className="goal-chat__reflection-top">
                  <div className="goal-chat__reflection-metric">
                    <span className="goal-chat__metric-num">{reflection.percentage}%</span>
                    <span className="goal-chat__metric-lbl">Target Adherence</span>
                  </div>
                  <div className="goal-chat__reflection-metric">
                    <span className="goal-chat__metric-num">
                      {reflection.completed}/{reflection.expected}
                    </span>
                    <span className="goal-chat__metric-lbl">Completed Days</span>
                  </div>
                </div>

                <div className="goal-chat__reflection-section">
                  <span className="goal-chat__section-lbl">Weekly Synthesis</span>
                  <p className="goal-chat__section-text">{reflection.reflection_text}</p>
                </div>

                <div className="goal-chat__reflection-section goal-chat__reflection-section--trend">
                  <TrendingUp size={16} />
                  <span>{reflection.trend_text}</span>
                </div>

                <button
                  className="goal-chat__refresh-btn"
                  onClick={loadWeeklyReflection}
                >
                  <Sparkles size={13} />
                  <span>Regenerate Insights</span>
                </button>
              </div>
            ) : (
              <div className="goal-chat__reflection-empty">
                <p>No reflection report found yet for this practice.</p>
                <button
                  className="goal-chat__refresh-btn"
                  onClick={loadWeeklyReflection}
                >
                  <Sparkles size={13} />
                  <span>Generate Report</span>
                </button>
              </div>
            )}
          </div>
        )}
      </motion.div>
    </div>
  )
}
