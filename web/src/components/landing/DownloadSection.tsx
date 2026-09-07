import { motion, useInView, useScroll, useTransform, useSpring } from 'framer-motion'
import { Link } from 'react-router-dom'
import { useRef } from 'react'
import { TextBlurReveal } from '../ui/TextBlurReveal'
import './DownloadSection.css'

export default function DownloadSection() {
  const sectionRef = useRef<HTMLElement>(null)
  const inView = useInView(sectionRef, { once: true, margin: '-80px' })

  // Scroll drift on massive watermark text
  const { scrollYProgress } = useScroll({
    target: sectionRef,
    offset: ['start end', 'end start'],
  })
  const smoothProgress = useSpring(scrollYProgress, { stiffness: 80, damping: 24 })
  const bgTextX = useTransform(smoothProgress, [0, 1], ['-5%', '5%'])
  const bgScale = useTransform(smoothProgress, [0, 0.5, 1], [0.96, 1.04, 0.96])

  return (
    <section className="dl" ref={sectionRef} aria-labelledby="dl-heading">
      <div className="dl__inner">
        {/* Background text — massive PROMISE with scroll drift */}
        <motion.div
          className="dl__bg-text"
          style={{ x: bgTextX, scale: bgScale }}
          aria-hidden="true"
        >
          PROMISE
        </motion.div>

        <div className="dl__content">
          <motion.p
            className="dl__eyebrow"
            initial={{ opacity: 0, y: 16 }}
            animate={inView ? { opacity: 1, y: 0 } : {}}
            transition={{ duration: 0.6 }}
          >
            Start today
          </motion.p>

          <TextBlurReveal
            text="Make one. Keep it."
            as="h2"
            className="dl__heading"
          />

          <motion.p
            className="dl__sub"
            initial={{ opacity: 0, y: 16 }}
            animate={inView ? { opacity: 1, y: 0 } : {}}
            transition={{ duration: 0.6, delay: 0.2 }}
          >
            No onboarding maze. Sign in with Google and make your first commitment in under 30 seconds.
          </motion.p>

          <motion.div
            className="dl__ctas"
            initial={{ opacity: 0, y: 16 }}
            animate={inView ? { opacity: 1, y: 0 } : {}}
            transition={{ duration: 0.6, delay: 0.3 }}
          >
            <Link to="/login" className="btn dl__btn-primary">
              Get started — it's free
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M5 12h14M12 5l7 7-7 7" />
              </svg>
            </Link>
            <a
              href="https://play.google.com/store"
              target="_blank"
              rel="noopener noreferrer"
              className="dl__android-btn"
            >
              <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                <path d="M17.523 15.3414C17.523 15.3414 17.523 15.3414 17.523 15.3414L19.5 11.9986L17.523 8.65582C17.0893 7.90308 16.5232 7.42857 15.8232 7.42857H8.17679C7.47679 7.42857 6.91071 7.90308 6.47701 8.65582L4.5 11.9986L6.47701 15.3414C6.91071 16.0941 7.47679 16.5686 8.17679 16.5686H15.8232C16.5232 16.5714 17.0893 16.0941 17.523 15.3414ZM12 14.5714C10.6607 14.5714 9.57143 13.4821 9.57143 12.1429C9.57143 10.8036 10.6607 9.71429 12 9.71429C13.3393 9.71429 14.4286 10.8036 14.4286 12.1429C14.4286 13.4821 13.3393 14.5714 12 14.5714Z"/>
              </svg>
              <span>Android app available</span>
            </a>
          </motion.div>

          <motion.div
            className="dl__trust"
            initial={{ opacity: 0 }}
            animate={inView ? { opacity: 1 } : {}}
            transition={{ duration: 0.6, delay: 0.5 }}
          >
            {['No credit card', 'No notifications by default', 'No public feed'].map((t, i) => (
              <span key={i} className="dl__trust-item">
                <span className="dl__trust-check" aria-hidden="true">✓</span>
                {t}
              </span>
            ))}
          </motion.div>
        </div>
      </div>
    </section>
  )
}
