import { useState, useRef, useEffect } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Sparkles, X, Send, Bot, ArrowUpRight } from 'lucide-react'
import { backend } from '../../lib/backend'
import './FloatingPromiseConciergeOrb.css'

interface FloatingPromiseConciergeOrbProps {
  onNavigateTab: (tab: 'home' | 'commitments' | 'roadmap' | 'goals' | 'profile') => void
  onOpenCreatePromise: () => void
}

interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
}

const DEFAULT_CHIPS = [
  'What should I focus on today?',
  'Analyze my weekly consistency',
  'How do I create a shared circle?',
  'Help me refine a commitment',
]

export default function FloatingPromiseConciergeOrb({
  onNavigateTab,
  onOpenCreatePromise,
}: FloatingPromiseConciergeOrbProps) {
  const [isOpen, setIsOpen] = useState(false)
  const [messages, setMessages] = useState<Message[]>([
    {
      id: 'welcome',
      role: 'assistant',
      content:
        'Hi! I am your Promise AI Concierge. I can help you prioritize today’s focus, refine commitments, or analyze your consistency.',
    },
  ])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const messagesEndRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (isOpen) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
    }
  }, [messages, isOpen])

  async function handleSend(text?: string) {
    const query = text || input
    if (!query.trim() || loading) return

    const userMsg: Message = {
      id: 'user-' + Date.now(),
      role: 'user',
      content: query.trim(),
    }

    setMessages((prev) => [...prev, userMsg])
    setInput('')
    setLoading(true)

    try {
      const history = messages.map((m) => ({ role: m.role, content: m.content }))
      const res = await backend.ai.support(query.trim(), history)
      const assistantMsg: Message = {
        id: 'bot-' + Date.now(),
        role: 'assistant',
        content: res.answer,
      }
      setMessages((prev) => [...prev, assistantMsg])
    } catch {
      setMessages((prev) => [
        ...prev,
        {
          id: 'bot-err-' + Date.now(),
          role: 'assistant',
          content: 'I could not connect to Gemini right now. Check your network or try again.',
        },
      ])
    } finally {
      setLoading(false)
    }
  }

  return (
    <>
      {/* Floating Glowing Iridescent Orb (Matching Android FloatingPromiseConciergeOrb) */}
      <motion.button
        className="concierge-orb"
        onClick={() => setIsOpen(!isOpen)}
        whileHover={{ scale: 1.12 }}
        whileTap={{ scale: 0.88 }}
        aria-label="Open AI Concierge"
        title="Promise AI Concierge"
      >
        <div className="concierge-orb__glow" />
        <div className="concierge-orb__core">
          {isOpen ? <X size={18} /> : <Sparkles size={18} className="concierge-orb__icon" />}
        </div>
      </motion.button>

      {/* Concierge Zoom Sheet Modal (Matching Android PromiseSupportChatSheet) */}
      <AnimatePresence>
        {isOpen && (
          <div className="concierge-sheet__overlay" onClick={() => setIsOpen(false)}>
            <motion.div
              className="concierge-sheet__card"
              onClick={(e) => e.stopPropagation()}
              initial={{ opacity: 0, scale: 0.4, x: 80, y: 80, transformOrigin: 'bottom right' }}
              animate={{ opacity: 1, scale: 1, x: 0, y: 0 }}
              exit={{ opacity: 0, scale: 0.4, x: 80, y: 80 }}
              transition={{ type: 'spring', stiffness: 420, damping: 28 }}
            >
              {/* Header */}
              <div className="concierge-sheet__header">
                <div className="concierge-sheet__title-row">
                  <div className="concierge-sheet__avatar">
                    <Bot size={15} />
                  </div>
                  <div>
                    <h3 className="concierge-sheet__title">Promise Concierge</h3>
                    <p className="concierge-sheet__sub">Powered by Gemini 3.7 Intelligence</p>
                  </div>
                </div>
                <button
                  className="concierge-sheet__close"
                  onClick={() => setIsOpen(false)}
                  aria-label="Close"
                >
                  <X size={15} />
                </button>
              </div>

              {/* Chat Thread */}
              <div className="concierge-sheet__feed">
                {messages.map((m) => (
                  <div
                    key={m.id}
                    className={`concierge-msg concierge-msg--${m.role}`}
                  >
                    <p className="concierge-msg__text">{m.content}</p>
                  </div>
                ))}
                {loading && (
                  <div className="concierge-msg concierge-msg--assistant">
                    <div className="concierge-msg__typing">
                      <span />
                      <span />
                      <span />
                    </div>
                  </div>
                )}
                <div ref={messagesEndRef} />
              </div>

              {/* Quick Action Chips */}
              <div className="concierge-sheet__chips">
                <button
                  className="concierge-chip concierge-chip--action"
                  onClick={() => {
                    setIsOpen(false)
                    onOpenCreatePromise()
                  }}
                >
                  <span>+ New Promise</span>
                </button>
                <button
                  className="concierge-chip concierge-chip--action"
                  onClick={() => {
                    setIsOpen(false)
                    onNavigateTab('commitments')
                  }}
                >
                  <span>Commitments</span>
                </button>
                <button
                  className="concierge-chip concierge-chip--action"
                  onClick={() => {
                    setIsOpen(false)
                    onNavigateTab('roadmap')
                  }}
                >
                  <span>Roadmap</span>
                </button>
                <button
                  className="concierge-chip concierge-chip--action"
                  onClick={() => {
                    setIsOpen(false)
                    onNavigateTab('goals')
                  }}
                >
                  <span>Goals</span>
                </button>
                {DEFAULT_CHIPS.map((chip, i) => (
                  <button
                    key={i}
                    className="concierge-chip"
                    onClick={() => handleSend(chip)}
                  >
                    <span>{chip}</span>
                    <ArrowUpRight size={11} />
                  </button>
                ))}
              </div>

              {/* Input Bar */}
              <form
                onSubmit={(e) => {
                  e.preventDefault()
                  handleSend()
                }}
                className="concierge-sheet__input-form"
              >
                <input
                  type="text"
                  className="concierge-sheet__input"
                  placeholder="Ask Gemini anything about your promises…"
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                />
                <button
                  type="submit"
                  className="concierge-sheet__send-btn"
                  disabled={!input.trim() || loading}
                >
                  <Send size={14} />
                </button>
              </form>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </>
  )
}
