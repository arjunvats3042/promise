import { useState, useEffect } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import { DynamicIsland } from '../ui/DynamicIsland'
import './Nav.css'

export default function Nav() {
  const [scrolled, setScrolled] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const location = useLocation()
  const isLanding = location.pathname === '/'

  useEffect(() => {
    const handleScroll = () => setScrolled(window.scrollY > 40)
    window.addEventListener('scroll', handleScroll, { passive: true })
    return () => window.removeEventListener('scroll', handleScroll)
  }, [])

  useEffect(() => {
    setMenuOpen(false)
  }, [location])

  return (
    <motion.nav
      className={`nav ${scrolled ? 'nav--scrolled' : ''} ${isLanding && !scrolled ? 'nav--transparent' : ''}`}
      initial={{ y: -60, opacity: 0 }}
      animate={{ y: 0, opacity: 1 }}
      transition={{ duration: 0.6, ease: [0.16, 1, 0.3, 1] }}
    >
      <div className="nav__inner">
        <Link to="/" className="nav__wordmark">
          <motion.span
            className="nav__wordmark-promise"
            whileHover={{ scale: 1.05 }}
            transition={{ type: 'spring', stiffness: 400, damping: 20 }}
          >
            Promise
          </motion.span>
        </Link>

        {/* Dynamic Island in center on desktop */}
        <div className="nav__island-container">
          <DynamicIsland />
        </div>

        <ul className="nav__links">
          <li><a href="#features" className="nav__link">Features</a></li>
          <li><a href="#ai" className="nav__link">AI</a></li>
          <li><a href="#goals" className="nav__link">Goals</a></li>
          <li><a href="#shared" className="nav__link">Community</a></li>
        </ul>

        <div className="nav__actions">
          <motion.div whileHover={{ scale: 1.03 }} whileTap={{ scale: 0.97 }}>
            <Link to="/login" className="btn btn--primary btn--sm nav__cta">
              Get Started
            </Link>
          </motion.div>
        </div>

        <button
          className={`nav__hamburger ${menuOpen ? 'nav__hamburger--open' : ''}`}
          onClick={() => setMenuOpen(!menuOpen)}
          aria-label="Toggle menu"
          aria-expanded={menuOpen}
        >
          <span />
          <span />
          <span />
        </button>
      </div>

      <AnimatePresence>
        {menuOpen && (
          <motion.div
            className="nav__mobile-menu"
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            transition={{ duration: 0.3, ease: [0.16, 1, 0.3, 1] }}
          >
            <a href="#features" className="nav__mobile-link">Features</a>
            <a href="#ai" className="nav__mobile-link">AI</a>
            <a href="#goals" className="nav__mobile-link">Goals</a>
            <a href="#shared" className="nav__mobile-link">Community</a>
            <div className="nav__mobile-actions">
              <Link to="/login" className="btn btn--primary">Get Started</Link>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </motion.nav>
  )
}

