import React, { useRef, useState, useCallback } from 'react'
import { motion, useMotionValue, useSpring, useTransform } from 'framer-motion'
import './SpotlightCard.css'

interface SpotlightCardProps extends React.HTMLAttributes<HTMLDivElement> {
  children: React.ReactNode
  className?: string
  spotlightColor?: string
  spotlightSize?: number
  enableTilt?: boolean
  tiltMaxAngle?: number
}

export function SpotlightCard({
  children,
  className = '',
  spotlightColor = 'rgba(91, 106, 240, 0.18)',
  spotlightSize = 350,
  enableTilt = true,
  tiltMaxAngle = 10,
  ...props
}: SpotlightCardProps) {
  const cardRef = useRef<HTMLDivElement>(null)
  const [isHovered, setIsHovered] = useState(false)

  // Mouse coords relative to card
  const mouseX = useMotionValue(0)
  const mouseY = useMotionValue(0)

  // Spring physics for smooth tilt
  const springConfig = { stiffness: 260, damping: 24 }
  const smoothX = useSpring(mouseX, springConfig)
  const smoothY = useSpring(mouseY, springConfig)

  // Map mouse coordinate normalized (-0.5 to 0.5) to tilt angles
  const rotateX = useTransform(smoothY, [-0.5, 0.5], [tiltMaxAngle, -tiltMaxAngle])
  const rotateY = useTransform(smoothX, [-0.5, 0.5], [-tiltMaxAngle, tiltMaxAngle])

  const [mousePos, setMousePos] = useState({ x: 0, y: 0 })

  const handleMouseMove = useCallback(
    (e: React.MouseEvent<HTMLDivElement>) => {
      if (!cardRef.current) return
      const rect = cardRef.current.getBoundingClientRect()
      const x = e.clientX - rect.left
      const y = e.clientY - rect.top

      setMousePos({ x, y })

      if (enableTilt) {
        const normX = (x / rect.width) - 0.5
        const normY = (y / rect.height) - 0.5
        mouseX.set(normX)
        mouseY.set(normY)
      }
    },
    [enableTilt, mouseX, mouseY]
  )

  const handleMouseEnter = () => setIsHovered(true)

  const handleMouseLeave = () => {
    setIsHovered(false)
    if (enableTilt) {
      mouseX.set(0)
      mouseY.set(0)
    }
  }

  return (
    <motion.div
      ref={cardRef}
      className={`spotlight-card ${className}`}
      onMouseMove={handleMouseMove}
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
      style={{
        perspective: enableTilt ? 1000 : undefined,
        rotateX: enableTilt ? rotateX : 0,
        rotateY: enableTilt ? rotateY : 0,
        transformStyle: enableTilt ? 'preserve-3d' : undefined,
      }}
      whileHover={{ y: -4 }}
      transition={{ duration: 0.3, ease: [0.16, 1, 0.3, 1] }}
      {...(props as any)}
    >
      {/* Specular Radial Spotlight Layer */}
      <div
        className="spotlight-card__spotlight"
        style={{
          opacity: isHovered ? 1 : 0,
          background: `radial-gradient(${spotlightSize}px circle at ${mousePos.x}px ${mousePos.y}px, ${spotlightColor}, transparent 80%)`,
        }}
        aria-hidden="true"
      />

      {/* Glass border sheen overlay */}
      <div
        className="spotlight-card__border-sheen"
        style={{
          opacity: isHovered ? 0.8 : 0,
          background: `radial-gradient(${spotlightSize * 0.8}px circle at ${mousePos.x}px ${mousePos.y}px, rgba(255, 255, 255, 0.22), transparent 70%)`,
        }}
        aria-hidden="true"
      />

      <div className="spotlight-card__content">
        {children}
      </div>
    </motion.div>
  )
}
