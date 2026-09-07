import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import { Mail, Lock, User, ArrowRight, Eye, EyeOff } from 'lucide-react'
import { backend } from '../lib/backend'
import { BorderBeam } from '../components/ui/BorderBeam'
import { AmbientAurora } from '../components/ui/AmbientAurora'
import './Login.css'

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (config: object) => void
          prompt: () => void
          renderButton: (el: HTMLElement, opts: object) => void
        }
      }
    }
  }
}

const BENEFITS = [
  'Build daily habits without the noise',
  'Keep every promise you make to yourself',
  'Never lose track of what matters most',
  'Stay accountable with quiet daily clarity',
  'Celebrate progress, one day at a time',
]

const GOOGLE_CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID || '547289698615-huc17692on1292athsn3ph80fiaagm9p.apps.googleusercontent.com'

function getGreeting(): string {
  const h = new Date().getHours()
  if (h < 5)  return 'Still up? Time to make a new promise.'
  if (h < 12) return 'Good morning. What will you keep today?'
  if (h < 17) return 'Good afternoon. Stay on track.'
  if (h < 21) return 'Good evening. How did today go?'
  return 'Good night. Review, reflect, rest.'
}

function extractAuthErrorMessage(err: unknown): string {
  const data = (err as any)?.response?.data
  if (!data) {
    if (err instanceof Error) return err.message
    return 'Authentication failed. Please check your network and try again.'
  }
  if (typeof data === 'string') return data
  if (data.detail) return data.detail
  if (data.error?.message) return data.error.message
  if (data.non_field_errors) {
    return Array.isArray(data.non_field_errors) ? data.non_field_errors.join(' ') : String(data.non_field_errors)
  }
  if (data.email) {
    return Array.isArray(data.email) ? data.email.join(' ') : String(data.email)
  }
  if (data.password) {
    return Array.isArray(data.password) ? data.password.join(' ') : String(data.password)
  }
  if (data.name) {
    return Array.isArray(data.name) ? data.name.join(' ') : String(data.name)
  }
  if (typeof data === 'object') {
    const firstKey = Object.keys(data)[0]
    if (firstKey) {
      const val = data[firstKey]
      return Array.isArray(val) ? val.join(' ') : String(val)
    }
  }
  return 'Authentication failed. Please verify your details.'
}

function TypewriterCarousel() {
  const [index, setIndex] = useState(0)
  const [displayed, setDisplayed] = useState('')
  const [phase, setPhase] = useState<'typing' | 'holding' | 'deleting'>('typing')

  useEffect(() => {
    let timeout: ReturnType<typeof setTimeout>
    const phrase = BENEFITS[index]

    if (phase === 'typing') {
      if (displayed.length < phrase.length) {
        timeout = setTimeout(() => {
          setDisplayed(phrase.slice(0, displayed.length + 1))
        }, 40)
      } else {
        timeout = setTimeout(() => setPhase('holding'), 2400)
      }
    } else if (phase === 'holding') {
      timeout = setTimeout(() => setPhase('deleting'), 100)
    } else {
      if (displayed.length > 0) {
        timeout = setTimeout(() => {
          setDisplayed(displayed.slice(0, -1))
        }, 18)
      } else {
        timeout = setTimeout(() => {
          setIndex((i) => (i + 1) % BENEFITS.length)
          setPhase('typing')
        }, 280)
      }
    }

    return () => clearTimeout(timeout)
  }, [displayed, phase, index])

  return (
    <div className="login__typewriter">
      <div className="login__typewriter-label">
        <span className="login__dot" />
        <span>WHY PROMISE</span>
      </div>
      <p className="login__typewriter-text">
        {displayed}
        <span className="login__cursor" aria-hidden="true">|</span>
      </p>
    </div>
  )
}

export default function Login() {
  const navigate = useNavigate()
  const googleBtnRef = useRef<HTMLDivElement>(null)
  
  // Real authentication states
  const [authMode, setAuthMode] = useState<'signin' | 'register'>('signin')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [name, setName] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [gisReady, setGisReady] = useState(false)

  // Redirect if already logged in with active token
  useEffect(() => {
    if (localStorage.getItem('access_token')) {
      navigate('/app', { replace: true })
    }
  }, [navigate])

  // Load Google Identity Services script
  useEffect(() => {
    if (document.getElementById('gis-script')) {
      setGisReady(true)
      return
    }
    const script = document.createElement('script')
    script.id = 'gis-script'
    script.src = 'https://accounts.google.com/gsi/client'
    script.async = true
    script.defer = true
    script.onload = () => setGisReady(true)
    document.head.appendChild(script)
  }, [])

  // Initialize Google Sign-In once GIS is loaded
  useEffect(() => {
    if (!gisReady || !window.google || !googleBtnRef.current) return

    window.google.accounts.id.initialize({
      client_id: GOOGLE_CLIENT_ID,
      callback: async (response: { credential: string }) => {
        setLoading(true)
        setError(null)
        try {
          await backend.auth.google(response.credential)
          navigate('/app')
        } catch (err: unknown) {
          setError(extractAuthErrorMessage(err))
          setLoading(false)
        }
      },
      auto_select: false,
      cancel_on_tap_outside: true,
    })

    window.google.accounts.id.renderButton(googleBtnRef.current, {
      theme: 'filled_black',
      size: 'large',
      shape: 'rectangular',
      width: Math.min(googleBtnRef.current.offsetWidth || 340, 360),
      text: 'continue_with',
      logo_alignment: 'left',
    })
  }, [gisReady, navigate])

  function handleFallbackGoogleClick() {
    if (window.google?.accounts?.id) {
      window.google.accounts.id.prompt()
    } else {
      setError('Google Sign-In is initializing. Please wait a moment...')
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!email.trim() || !password || loading) return
    if (authMode === 'register' && !name.trim()) {
      setError('Please provide your name.')
      return
    }

    setLoading(true)
    setError(null)

    try {
      if (authMode === 'signin') {
        await backend.auth.login(email.trim(), password)
      } else {
        await backend.auth.register({
          email: email.trim(),
          password,
          name: name.trim(),
        })
      }
      navigate('/app')
    } catch (err: unknown) {
      setError(extractAuthErrorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  const greeting = getGreeting()

  return (
    <div className="login">
      <AmbientAurora />

      {/* Atmospheric background glows */}
      <div className="login__glow login__glow--tr" aria-hidden="true" />
      <div className="login__glow login__glow--bl" aria-hidden="true" />

      <motion.div
        className="login__card"
        initial={{ opacity: 0, y: 24 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.6, ease: [0.16, 1, 0.3, 1] }}
      >
        {/* Live pill */}
        <div className="login__live-pill">
          <span className="login__live-dot" aria-hidden="true" />
          ONE DAY AT A TIME
        </div>

        {/* Logo mark */}
        <Link to="/" className="login__logo" aria-label="Promise home">
          <svg width="48" height="48" viewBox="0 0 48 48" fill="none" aria-hidden="true">
            <rect width="48" height="48" rx="14" fill="var(--color-accent)" />
            <path d="M14 24 L24 14 L34 24 L24 34 Z" fill="none" stroke="white" strokeWidth="2.5" strokeLinejoin="round"/>
            <circle cx="24" cy="24" r="3.5" fill="white"/>
          </svg>
        </Link>

        {/* Accent pill */}
        <div className="badge badge--accent login__badge">
          INTEGRITY · MOMENTUM · FOCUS
        </div>

        {/* Heading */}
        <h1 className="login__heading">Promise</h1>
        <p className="login__subheading">Keep every promise you make to yourself.</p>

        {/* Greeting */}
        <div className="login__greeting">
          <span>{greeting}</span>
        </div>

        {/* Typewriter benefit carousel */}
        <TypewriterCarousel />

        {/* Sign-in card */}
        <div className="login__signin-card" style={{ position: 'relative', overflow: 'hidden' }}>
          <BorderBeam size={180} duration={10} borderWidth={1.5} colorFrom="#5B6AF0" colorTo="#9D7BFF" />
          
          <h2 className="login__signin-title">
            {authMode === 'signin' ? 'Sign In to Promise' : 'Create an Account'}
          </h2>
          <p className="login__signin-sub">
            {authMode === 'signin'
              ? 'Enter your credentials to access your daily commitments.'
              : 'Join Promise to build focus and lasting consistency.'}
          </p>

          {/* Mode Switcher */}
          <div className="login__auth-tabs">
            <button
              type="button"
              className={`login__auth-tab ${authMode === 'signin' ? 'login__auth-tab--active' : ''}`}
              onClick={() => {
                setAuthMode('signin')
                setError(null)
              }}
            >
              Sign In
            </button>
            <button
              type="button"
              className={`login__auth-tab ${authMode === 'register' ? 'login__auth-tab--active' : ''}`}
              onClick={() => {
                setAuthMode('register')
                setError(null)
              }}
            >
              Create Account
            </button>
          </div>

          {/* Real Auth Form */}
          <form className="login__form" onSubmit={handleSubmit}>
            {authMode === 'register' && (
              <div className="login__input-group">
                <label className="login__input-label" htmlFor="register-name">Full Name</label>
                <div className="login__input-wrap">
                  <User className="login__input-icon" size={16} />
                  <input
                    id="register-name"
                    type="text"
                    className="login__input"
                    placeholder="Jane Doe"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    required
                    autoComplete="name"
                    disabled={loading}
                  />
                </div>
              </div>
            )}

            <div className="login__input-group">
              <label className="login__input-label" htmlFor="auth-email">Email Address</label>
              <div className="login__input-wrap">
                <Mail className="login__input-icon" size={16} />
                <input
                  id="auth-email"
                  type="email"
                  className="login__input"
                  placeholder="you@promise.app"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                  autoComplete="email"
                  disabled={loading}
                />
              </div>
            </div>

            <div className="login__input-group">
              <label className="login__input-label" htmlFor="auth-password">Password</label>
              <div className="login__input-wrap">
                <Lock className="login__input-icon" size={16} />
                <input
                  id="auth-password"
                  type={showPassword ? 'text' : 'password'}
                  className="login__input"
                  placeholder={authMode === 'register' ? 'At least 8 characters' : 'Enter your password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  autoComplete={authMode === 'register' ? 'new-password' : 'current-password'}
                  minLength={authMode === 'register' ? 8 : undefined}
                  disabled={loading}
                />
                <button
                  type="button"
                  className="login__password-toggle"
                  onClick={() => setShowPassword(!showPassword)}
                  tabIndex={-1}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? <EyeOff size={15} /> : <Eye size={15} />}
                </button>
              </div>
            </div>

            <motion.button
              type="submit"
              className="btn btn--accent login__submit-btn"
              whileHover={{ scale: 1.01 }}
              whileTap={{ scale: 0.99 }}
              disabled={loading}
            >
              {loading ? (
                <div className="login__btn-loading">
                  <span className="login__spinner" />
                  <span>{authMode === 'signin' ? 'Signing in…' : 'Creating account…'}</span>
                </div>
              ) : (
                <>
                  <span>{authMode === 'signin' ? 'Sign In' : 'Create Account'}</span>
                  <ArrowRight size={16} />
                </>
              )}
            </motion.button>
          </form>

          {/* Divider */}
          <div className="login__divider">
            <span>OR CONTINUE WITH</span>
          </div>

          {/* Google Sign In */}
          <div className="login__google-btn-wrap" ref={googleBtnRef} id="google-signin-btn">
            {!gisReady && (
              <button
                className="login__google-fallback"
                onClick={handleFallbackGoogleClick}
                type="button"
                disabled={loading}
              >
                <GoogleIcon />
                <span>Continue with Google</span>
              </button>
            )}
          </div>

          {/* Error message */}
          <AnimatePresence>
            {error && (
              <motion.div
                className="login__error"
                initial={{ opacity: 0, y: -8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -8 }}
              >
                {error}
              </motion.div>
            )}
          </AnimatePresence>
        </div>

        {/* Footer */}
        <div className="login__footer">
          <span className="login__footer-studio">PROMISE STUDIO</span>
          <span className="login__footer-tagline">Crafted for clarity and focus</span>
        </div>
      </motion.div>
    </div>
  )
}

function GoogleIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" aria-hidden="true">
      <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/>
      <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
      <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/>
      <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/>
    </svg>
  )
}
