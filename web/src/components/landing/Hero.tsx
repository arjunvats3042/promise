import { useRef } from 'react'
import { Link } from 'react-router-dom'
import { motion, useScroll, useTransform, useSpring } from 'framer-motion'
import PhoneMockup from './PhoneMockup'
import './Hero.css'

const today = new Date().toLocaleDateString('en-US', {
  weekday: 'long', month: 'long', day: 'numeric',
})

export default function Hero() {
  const containerRef = useRef<HTMLElement>(null)

  const { scrollYProgress } = useScroll({
    target: containerRef,
    offset: ['start start', 'end start'],
  })

  const smoothProgress = useSpring(scrollYProgress, { stiffness: 100, damping: 24 })
  const wordmarkY = useTransform(smoothProgress, [0, 1], [0, -120])
  const wordmarkOpacity = useTransform(smoothProgress, [0, 0.55], [1, 0])
  const phoneY = useTransform(smoothProgress, [0, 1], [0, -80])
  const phoneScale = useTransform(smoothProgress, [0, 0.8], [1, 1.05])
  const phoneRotateX = useTransform(smoothProgress, [0, 0.8], [0, 8])
  const ruleScaleY = useTransform(smoothProgress, [0, 0.5], [1, 0.7])

  return (
    <section className="hero" ref={containerRef} aria-label="Promise — Keep what you say">
      {/* Background grid texture */}
      <div className="hero__grid" aria-hidden="true" />

      {/* Ambient glow */}
      <div className="hero__glow hero__glow--left" aria-hidden="true" />
      <div className="hero__glow hero__glow--right" aria-hidden="true" />

      <div className="hero__layout">
        {/* LEFT — Typography lockup */}
        <motion.div
          className="hero__copy"
          style={{ y: wordmarkY, opacity: wordmarkOpacity }}
        >
          {/* Glowing Apple pill badge */}
          <motion.div
            className="hero__badge"
            initial={{ opacity: 0, scale: 0.9, y: -10 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            transition={{ duration: 0.6, ease: [0.16, 1, 0.3, 1] }}
          >
            <span className="hero__badge-pulse" />
            <span className="hero__badge-text">The Quiet Practice of Follow-Through</span>
            <span className="hero__badge-date">· {today}</span>
          </motion.div>

          {/* Wordmark & Keynote Tagline */}
          <div className="hero__title-lockup">
            <h1 className="hero__heading">
              <span className="hero__heading-main">Keep what you say.</span>
              <span className="hero__heading-gradient"> Without the noise.</span>
            </h1>
          </div>

          {/* Sub-description */}
          <motion.p
            className="hero__description"
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.7, delay: 0.3, ease: [0.16, 1, 0.3, 1] }}
          >
            A high-craft commitment tracker built on quiet integrity.
            Discrete promises, daily practices, shared accountability, and advisory
            Gemini AI — designed to help you execute, not entertain.
          </motion.p>

          {/* CTAs */}
          <motion.div
            className="hero__ctas"
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.7, delay: 0.45, ease: [0.16, 1, 0.3, 1] }}
          >
            <motion.div whileHover={{ scale: 1.03 }} whileTap={{ scale: 0.97 }}>
              <Link to="/login" className="btn btn--primary btn--lg hero__cta-primary">
                <span>Experience Promise</span>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <path d="M5 12h14M12 5l7 7-7 7" />
                </svg>
              </Link>
            </motion.div>

            <motion.div whileHover={{ scale: 1.03 }} whileTap={{ scale: 0.97 }}>
              <a href="#features" className="btn btn--ghost btn--lg hero__cta-secondary">
                Explore Disciplines
              </a>
            </motion.div>
          </motion.div>

          {/* Trust signal badges */}
          <motion.div
            className="hero__trust"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.8, delay: 0.6 }}
          >
            <span className="hero__trust-item">
              <span className="hero__trust-dot" aria-hidden="true" />
              Free to use
            </span>
            <span className="hero__trust-sep">/</span>
            <span className="hero__trust-item">Google Sign-In only</span>
            <span className="hero__trust-sep">/</span>
            <span className="hero__trust-item">Zero ads or tracking</span>
          </motion.div>
        </motion.div>

        {/* Swiss vertical rule */}
        <motion.div
          className="hero__rule"
          style={{ scaleY: ruleScaleY }}
          initial={{ scaleY: 0, opacity: 0 }}
          animate={{ scaleY: 1, opacity: 1 }}
          transition={{ duration: 0.9, delay: 0.3, ease: [0.16, 1, 0.3, 1] }}
          aria-hidden="true"
        />

        {/* RIGHT — Phone mockup */}
        <motion.div
          className="hero__phone-wrap"
          style={{
            y: phoneY,
            scale: phoneScale,
            rotateX: phoneRotateX,
            perspective: 1200,
          }}
          initial={{ opacity: 0, x: 40, y: 20 }}
          animate={{ opacity: 1, x: 0, y: 0 }}
          transition={{ duration: 0.9, delay: 0.4, ease: [0.16, 1, 0.3, 1] }}
        >
          <PhoneMockup scrollProgress={smoothProgress} />
        </motion.div>
      </div>

      {/* Scroll indicator */}
      <motion.div
        className="hero__scroll-hint"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 1.5, duration: 0.6 }}
        aria-hidden="true"
      >
        <div className="hero__scroll-line" />
        <span>scroll</span>
      </motion.div>
    </section>
  )
}
