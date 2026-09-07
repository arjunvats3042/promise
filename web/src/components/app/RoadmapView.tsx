import { useState, useMemo } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import {
  Download,
  Calendar,
  Smartphone,
  Monitor,
  Sparkles,
  Check,
  ShieldCheck,
  Zap,
  BatteryCharging,
  Layers,
  X,
  ExternalLink,
} from 'lucide-react'
import { triggerCelebration } from '../ui/celebrate'
import './RoadmapView.css'

type DeviceType = 'phone' | 'desktop'
type BgPalette = 'oled' | 'obsidian' | 'titanium'
type AccentColor = 'blue' | 'amber' | 'emerald' | 'silver'

const PALETTES: Record<BgPalette, { label: string; bg: string; border: string }> = {
  oled: { label: 'OLED Black', bg: '#000000', border: '#222222' },
  obsidian: { label: 'Midnight Obsidian', bg: '#090a0f', border: '#1e2433' },
  titanium: { label: 'Titanium Slate', bg: '#10131d', border: '#252b3d' },
}

const ACCENTS: Record<AccentColor, { label: string; color: string; glow: string }> = {
  blue: { label: 'Electric Blue', color: '#3b82f6', glow: 'rgba(59, 130, 246, 0.5)' },
  amber: { label: 'Cyber Amber', color: '#f59e0b', glow: 'rgba(245, 158, 11, 0.5)' },
  emerald: { label: 'Neon Emerald', color: '#10b981', glow: 'rgba(16, 185, 129, 0.5)' },
  silver: { label: 'Monolith White', color: '#f8fafc', glow: 'rgba(255, 255, 255, 0.5)' },
}

export default function RoadmapView() {
  const now = new Date()
  const year = now.getFullYear()
  const startOfYear = new Date(year, 0, 1)
  const isLeapYear = (year % 4 === 0 && year % 100 !== 0) || year % 400 === 0
  const totalDays = isLeapYear ? 366 : 365

  // Calculate day of year (1-based)
  const diffTime = now.getTime() - startOfYear.getTime()
  const currentDayOfYear = Math.min(
    totalDays,
    Math.max(1, Math.floor(diffTime / (1000 * 60 * 60 * 24)) + 1)
  )
  const percentElapsed = ((currentDayOfYear / totalDays) * 100).toFixed(1)
  const daysRemaining = totalDays - currentDayOfYear

  const [hoveredDay, setHoveredDay] = useState<number | null>(currentDayOfYear)

  // Wallpaper studio state
  const [device, setDevice] = useState<DeviceType>('phone')
  const [palette, setPalette] = useState<BgPalette>('obsidian')
  const [accent, setAccent] = useState<AccentColor>('blue')
  const [showAppModal, setShowAppModal] = useState(false)
  const [isExporting, setIsExporting] = useState(false)

  const cols = 14

  const hoveredDetails = useMemo(() => {
    if (!hoveredDay) return null
    const date = new Date(year, 0, hoveredDay)
    const isPast = hoveredDay < currentDayOfYear
    const isToday = hoveredDay === currentDayOfYear
    const statusLabel = isToday ? 'Today (Active)' : isPast ? 'Completed Day' : 'Future Day'
    return {
      dateFormatted: date.toLocaleDateString('en-US', {
        weekday: 'long',
        month: 'long',
        day: 'numeric',
        year: 'numeric',
      }),
      dayNumber: hoveredDay,
      statusLabel,
    }
  }, [hoveredDay, year, currentDayOfYear])

  // Download high-resolution wallpaper (Phone 1440x3200 or Desktop 3840x2160)
  function handleDownloadWallpaper(targetDevice: DeviceType) {
    setIsExporting(true)
    const activePalette = PALETTES[palette]
    const activeAccent = ACCENTS[accent]

    const canvas = document.createElement('canvas')
    const ctx = canvas.getContext('2d')
    if (!ctx) {
      setIsExporting(false)
      return
    }

    if (targetDevice === 'phone') {
      // 9:16 Ultra High-Res Phone Lock Screen (1440 x 3200)
      canvas.width = 1440
      canvas.height = 3200

      // Background
      ctx.fillStyle = activePalette.bg
      ctx.fillRect(0, 0, 1440, 3200)

      // Radial background glow behind dots
      const grad = ctx.createRadialGradient(720, 1600, 100, 720, 1600, 700)
      grad.addColorStop(0, `${activeAccent.color}15`)
      grad.addColorStop(1, 'transparent')
      ctx.fillStyle = grad
      ctx.fillRect(0, 0, 1440, 3200)

      // Title & Percentage (Top Area below Lock Screen Clock space)
      // Lock screen clock is usually at y=300 to y=650
      ctx.fillStyle = activeAccent.color
      ctx.font = 'bold 36px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
      ctx.textAlign = 'center'
      ctx.letterSpacing = '6px'
      ctx.fillText(`${year} LIFE ROADMAP`, 720, 950)

      ctx.fillStyle = '#ffffff'
      ctx.font = 'bold 120px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
      ctx.fillText(`${percentElapsed}%`, 720, 1080)

      ctx.fillStyle = 'rgba(255, 255, 255, 0.65)'
      ctx.font = '500 34px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
      ctx.fillText(
        `Day ${currentDayOfYear} of ${totalDays} · ${daysRemaining} days remaining`,
        720,
        1145
      )

      // 14-column Memento Mori Dots Grid
      const wallCols = 14
      const cellSpacing = 58
      const startX = 720 - (wallCols * cellSpacing) / 2 + cellSpacing / 2
      const startY = 1270

      for (let day = 1; day <= totalDays; day++) {
        const idx = day - 1
        const c = idx % wallCols
        const r = Math.floor(idx / wallCols)
        const x = startX + c * cellSpacing
        const y = startY + r * cellSpacing

        ctx.beginPath()
        if (day < currentDayOfYear) {
          ctx.arc(x, y, 12, 0, Math.PI * 2)
          ctx.fillStyle = 'rgba(255, 255, 255, 0.9)'
          ctx.fill()
        } else if (day === currentDayOfYear) {
          // Outer Glow
          ctx.arc(x, y, 22, 0, Math.PI * 2)
          ctx.fillStyle = activeAccent.glow
          ctx.fill()

          // Inner Dot
          ctx.beginPath()
          ctx.arc(x, y, 16, 0, Math.PI * 2)
          ctx.fillStyle = activeAccent.color
          ctx.fill()
        } else {
          ctx.arc(x, y, 10, 0, Math.PI * 2)
          ctx.fillStyle = 'rgba(255, 255, 255, 0.14)'
          ctx.fill()
        }
      }

      // Philosophical Quote at bottom
      ctx.fillStyle = 'rgba(255, 255, 255, 0.45)'
      ctx.font = 'italic 32px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
      ctx.fillText('“Every day is a dot. Today is yours to fill.”', 720, 2900)

      ctx.fillStyle = 'rgba(255, 255, 255, 0.25)'
      ctx.font = '600 24px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
      ctx.fillText('PROMISE', 720, 2960)

      const link = document.createElement('a')
      link.download = `promise-lockscreen-${year}-${palette}.png`
      link.href = canvas.toDataURL('image/png')
      link.click()
    } else {
      // 16:9 Ultra 4K Desktop Display (3840 x 2160)
      canvas.width = 3840
      canvas.height = 2160

      ctx.fillStyle = activePalette.bg
      ctx.fillRect(0, 0, 3840, 2160)

      // Subtle radial glow
      const grad = ctx.createRadialGradient(1920, 1080, 200, 1920, 1080, 1200)
      grad.addColorStop(0, `${activeAccent.color}15`)
      grad.addColorStop(1, 'transparent')
      ctx.fillStyle = grad
      ctx.fillRect(0, 0, 3840, 2160)

      // Header Telemetry
      ctx.fillStyle = activeAccent.color
      ctx.font = 'bold 36px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
      ctx.textAlign = 'center'
      ctx.fillText(`${year} LIFE ROADMAP`, 1920, 480)

      ctx.fillStyle = '#ffffff'
      ctx.font = 'bold 150px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
      ctx.fillText(`${percentElapsed}%`, 1920, 620)

      ctx.fillStyle = 'rgba(255, 255, 255, 0.65)'
      ctx.font = '500 36px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
      ctx.fillText(
        `Day ${currentDayOfYear} of ${totalDays} · ${daysRemaining} days remaining`,
        1920,
        690
      )

      // 14-column Dots Grid
      const wallCols = 14
      const cellSpacing = 48
      const startX = 1920 - (wallCols * cellSpacing) / 2 + cellSpacing / 2
      const startY = 820

      for (let day = 1; day <= totalDays; day++) {
        const idx = day - 1
        const c = idx % wallCols
        const r = Math.floor(idx / wallCols)
        const x = startX + c * cellSpacing
        const y = startY + r * cellSpacing

        ctx.beginPath()
        if (day < currentDayOfYear) {
          ctx.arc(x, y, 10, 0, Math.PI * 2)
          ctx.fillStyle = 'rgba(255, 255, 255, 0.85)'
          ctx.fill()
        } else if (day === currentDayOfYear) {
          ctx.arc(x, y, 18, 0, Math.PI * 2)
          ctx.fillStyle = activeAccent.glow
          ctx.fill()

          ctx.beginPath()
          ctx.arc(x, y, 14, 0, Math.PI * 2)
          ctx.fillStyle = activeAccent.color
          ctx.fill()
        } else {
          ctx.arc(x, y, 9, 0, Math.PI * 2)
          ctx.fillStyle = 'rgba(255, 255, 255, 0.12)'
          ctx.fill()
        }
      }

      // Quote
      ctx.fillStyle = 'rgba(255, 255, 255, 0.45)'
      ctx.font = 'italic 32px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
      ctx.fillText('“Every day is a dot. Today is yours to fill.”', 1920, 1980)

      const link = document.createElement('a')
      link.download = `promise-desktop-${year}-${palette}.png`
      link.href = canvas.toDataURL('image/png')
      link.click()
    }

    setIsExporting(false)
    triggerCelebration({ particleCount: 35, spread: 55 })
  }

  return (
    <div className="roadmap-view">
      {/* 1. Header Telemetry */}
      <motion.div
        className="roadmap-view__header"
        initial={{ opacity: 0, y: 15 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3 }}
      >
        <div className="roadmap-view__badge">
          <Calendar size={13} />
          <span>{year} LIFE ROADMAP</span>
        </div>

        <h1 className="roadmap-view__metric">{percentElapsed}%</h1>

        <p className="roadmap-view__sub">
          Day <span className="roadmap-view__highlight">{currentDayOfYear}</span> of{' '}
          {totalDays} · <span className="roadmap-view__highlight">{daysRemaining}</span> days
          left in {year}
        </p>
      </motion.div>

      {/* 2. Interactive 365-Dot Constellation Matrix */}
      <motion.div
        className="roadmap-view__canvas-card"
        initial={{ opacity: 0, scale: 0.98 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ duration: 0.35, delay: 0.1 }}
      >
        <div
          className="roadmap-view__grid"
          style={{
            gridTemplateColumns: `repeat(${cols}, 1fr)`,
          }}
        >
          {Array.from({ length: totalDays }).map((_, idx) => {
            const dayNum = idx + 1
            const isPast = dayNum < currentDayOfYear
            const isToday = dayNum === currentDayOfYear
            const isSelected = hoveredDay === dayNum

            let dotClass = 'roadmap-dot--future'
            if (isPast) dotClass = 'roadmap-dot--past'
            if (isToday) dotClass = 'roadmap-dot--today'
            if (isSelected) dotClass += ' roadmap-dot--selected'

            return (
              <div
                key={dayNum}
                className={`roadmap-dot-wrap ${dotClass}`}
                onMouseEnter={() => setHoveredDay(dayNum)}
                onClick={() => {
                  setHoveredDay(dayNum)
                  if (isToday) triggerCelebration({ particleCount: 20, spread: 40 })
                }}
                title={`Day ${dayNum}`}
              >
                <div className="roadmap-dot" />
                {isToday && <div className="roadmap-dot__pulse" />}
              </div>
            )
          })}
        </div>

        {/* Hover Day Telemetry Panel */}
        {hoveredDetails && (
          <motion.div
            className="roadmap-view__inspector"
            key={hoveredDetails.dayNumber}
            initial={{ opacity: 0, y: 4 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.15 }}
          >
            <div className="roadmap-view__inspector-date">
              <Calendar size={13} className="roadmap-view__inspector-icon" />
              <span>{hoveredDetails.dateFormatted}</span>
            </div>
            <div className="roadmap-view__inspector-meta">
              <span className="roadmap-view__inspector-pill">
                Day {hoveredDetails.dayNumber} of {totalDays}
              </span>
              <span className="roadmap-view__inspector-status">
                {hoveredDetails.statusLabel}
              </span>
            </div>
          </motion.div>
        )}
      </motion.div>

      {/* 3. Flagship Native Android App Showcase Banner */}
      <motion.div
        className="roadmap-view__app-banner"
        initial={{ opacity: 0, y: 15 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.2 }}
      >
        <div className="roadmap-view__app-banner-glow" />
        <div className="roadmap-view__app-banner-content">
          <div className="roadmap-view__app-banner-tag">
            <Smartphone size={13} />
            <span>NATIVE ANDROID ENGINE</span>
          </div>

          <h2 className="roadmap-view__app-banner-heading">
            Want this wallpaper to update automatically every day?
          </h2>

          <p className="roadmap-view__app-banner-desc">
            Web wallpapers are static downloads. In the native <strong>Promise Android app</strong>,
            the 365 Roadmap connects directly to Android’s Wallpaper API. Every night at{' '}
            <strong>12:05 AM midnight</strong>, a native background worker illuminates your next
            day’s dot right on your Lock Screen and Home Screen — completely automated with zero
            battery drain.
          </p>

          <div className="roadmap-view__app-features">
            <div className="roadmap-view__app-feature-pill">
              <Zap size={14} className="roadmap-view__app-pill-icon" />
              <span>12:05 AM Midnight Sync</span>
            </div>
            <div className="roadmap-view__app-feature-pill">
              <ShieldCheck size={14} className="roadmap-view__app-pill-icon" />
              <span>Hardware Wallpaper API</span>
            </div>
            <div className="roadmap-view__app-feature-pill">
              <BatteryCharging size={14} className="roadmap-view__app-pill-icon" />
              <span>0% Battery Impact</span>
            </div>
          </div>
        </div>

        <div className="roadmap-view__app-banner-action">
          <button
            className="roadmap-view__app-btn"
            onClick={() => setShowAppModal(true)}
          >
            <Smartphone size={15} />
            <span>Get Native Android App</span>
          </button>
        </div>
      </motion.div>

      {/* 4. Realistic Wallpaper Studio & Device Mockup Section */}
      <motion.div
        className="roadmap-studio"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.25 }}
      >
        <div className="roadmap-studio__header">
          <div className="roadmap-studio__header-text">
            <div className="roadmap-studio__tag">
              <Layers size={13} />
              <span>WALLPAPER STUDIO</span>
            </div>
            <h2 className="roadmap-studio__title">High-Resolution Device Mockup</h2>
            <p className="roadmap-studio__subtitle">
              Export 4K Memento Mori wallpapers tailored for modern smartphone lock screens and
              desktop monitors.
            </p>
          </div>

          {/* Quick Actions */}
          <div className="roadmap-studio__download-btns">
            <button
              className="roadmap-studio__dl-btn roadmap-studio__dl-btn--primary"
              onClick={() => handleDownloadWallpaper(device)}
              disabled={isExporting}
            >
              <Download size={14} />
              <span>
                Download {device === 'phone' ? 'Phone Lock Screen (4K)' : 'Desktop 4K Wallpaper'}
              </span>
            </button>
          </div>
        </div>

        {/* Studio Controls: Device Switcher & Color Customizer */}
        <div className="roadmap-studio__controls">
          {/* Device toggle */}
          <div className="roadmap-studio__control-group">
            <span className="roadmap-studio__control-label">Target Format</span>
            <div className="roadmap-studio__segmented">
              <button
                className={`roadmap-studio__seg-btn ${
                  device === 'phone' ? 'roadmap-studio__seg-btn--active' : ''
                }`}
                onClick={() => setDevice('phone')}
              >
                <Smartphone size={14} />
                <span>Phone (9:16)</span>
              </button>
              <button
                className={`roadmap-studio__seg-btn ${
                  device === 'desktop' ? 'roadmap-studio__seg-btn--active' : ''
                }`}
                onClick={() => setDevice('desktop')}
              >
                <Monitor size={14} />
                <span>Desktop (16:9)</span>
              </button>
            </div>
          </div>

          {/* Theme Palette */}
          <div className="roadmap-studio__control-group">
            <span className="roadmap-studio__control-label">Background</span>
            <div className="roadmap-studio__palette-chips">
              {(Object.keys(PALETTES) as BgPalette[]).map((key) => {
                const item = PALETTES[key]
                const isSelected = palette === key
                return (
                  <button
                    key={key}
                    className={`roadmap-studio__palette-btn ${
                      isSelected ? 'roadmap-studio__palette-btn--active' : ''
                    }`}
                    onClick={() => setPalette(key)}
                  >
                    <span
                      className="roadmap-studio__palette-swatch"
                      style={{ background: item.bg, border: `1px solid ${item.border}` }}
                    />
                    <span>{item.label}</span>
                  </button>
                )
              })}
            </div>
          </div>

          {/* Accent Color */}
          <div className="roadmap-studio__control-group">
            <span className="roadmap-studio__control-label">Dot Accent</span>
            <div className="roadmap-studio__accent-chips">
              {(Object.keys(ACCENTS) as AccentColor[]).map((key) => {
                const item = ACCENTS[key]
                const isSelected = accent === key
                return (
                  <button
                    key={key}
                    className={`roadmap-studio__accent-btn ${
                      isSelected ? 'roadmap-studio__accent-btn--active' : ''
                    }`}
                    onClick={() => setAccent(key)}
                  >
                    <span
                      className="roadmap-studio__accent-swatch"
                      style={{ background: item.color }}
                    />
                    <span>{item.label}</span>
                  </button>
                )
              })}
            </div>
          </div>
        </div>

        {/* 5. Live Realistic Mockup Frame */}
        <div className="roadmap-studio__preview-stage">
          <div className="roadmap-studio__preview-container">
            {device === 'phone' ? (
              /* Realistic Phone Mockup */
              <div
                className="mockup-phone"
                style={{
                  background: PALETTES[palette].bg,
                  borderColor: PALETTES[palette].border,
                }}
              >
                {/* Dynamic Island */}
                <div className="mockup-phone__island" />

                {/* Status Bar */}
                <div className="mockup-phone__status-bar">
                  <span>9:41</span>
                  <div className="mockup-phone__status-icons">
                    <span>5G</span>
                    <div className="mockup-phone__battery" />
                  </div>
                </div>

                {/* Lock Screen Clock & Date */}
                <div className="mockup-phone__lock-header">
                  <span className="mockup-phone__date">
                    {now.toLocaleDateString('en-US', {
                      weekday: 'long',
                      month: 'short',
                      day: 'numeric',
                    })}
                  </span>
                  <span className="mockup-phone__clock">12:05</span>
                </div>

                {/* Centered Roadmap Canvas Content */}
                <div className="mockup-phone__screen-body">
                  <span
                    className="mockup-phone__tag"
                    style={{ color: ACCENTS[accent].color }}
                  >
                    {year} LIFE ROADMAP
                  </span>
                  <span className="mockup-phone__metric">{percentElapsed}%</span>
                  <span className="mockup-phone__sub">
                    Day {currentDayOfYear} of {totalDays}
                  </span>

                  {/* Micro 14-col Grid */}
                  <div className="mockup-phone__grid">
                    {Array.from({ length: totalDays }).map((_, idx) => {
                      const day = idx + 1
                      const isPast = day < currentDayOfYear
                      const isToday = day === currentDayOfYear

                      return (
                        <div
                          key={day}
                          className="mockup-phone__dot"
                          style={{
                            backgroundColor: isToday
                              ? ACCENTS[accent].color
                              : isPast
                              ? 'rgba(255, 255, 255, 0.85)'
                              : 'rgba(255, 255, 255, 0.12)',
                            boxShadow: isToday
                              ? `0 0 6px ${ACCENTS[accent].color}`
                              : 'none',
                          }}
                        />
                      )
                    })}
                  </div>

                  <p className="mockup-phone__quote">
                    “Every day is a dot. Today is yours to fill.”
                  </p>
                </div>

                {/* Home Indicator Bar */}
                <div className="mockup-phone__home-indicator" />
              </div>
            ) : (
              /* Realistic Desktop Monitor Mockup */
              <div className="mockup-desktop">
                <div
                  className="mockup-desktop__screen"
                  style={{
                    background: PALETTES[palette].bg,
                    borderColor: PALETTES[palette].border,
                  }}
                >
                  <div className="mockup-desktop__content">
                    <span
                      className="mockup-desktop__tag"
                      style={{ color: ACCENTS[accent].color }}
                    >
                      {year} LIFE ROADMAP
                    </span>
                    <h1 className="mockup-desktop__metric">{percentElapsed}%</h1>
                    <p className="mockup-desktop__sub">
                      Day {currentDayOfYear} of {totalDays} · {daysRemaining} days remaining
                    </p>

                    <div className="mockup-desktop__grid">
                      {Array.from({ length: totalDays }).map((_, idx) => {
                        const day = idx + 1
                        const isPast = day < currentDayOfYear
                        const isToday = day === currentDayOfYear

                        return (
                          <div
                            key={day}
                            className="mockup-desktop__dot"
                            style={{
                              backgroundColor: isToday
                                ? ACCENTS[accent].color
                                : isPast
                                ? 'rgba(255, 255, 255, 0.85)'
                                : 'rgba(255, 255, 255, 0.12)',
                              boxShadow: isToday
                                ? `0 0 8px ${ACCENTS[accent].color}`
                                : 'none',
                            }}
                          />
                        )
                      })}
                    </div>

                    <p className="mockup-desktop__quote">
                      “Every day is a dot. Today is yours to fill.”
                    </p>
                  </div>
                </div>
                {/* Desktop Stand */}
                <div className="mockup-desktop__neck" />
                <div className="mockup-desktop__base" />
              </div>
            )}
          </div>
        </div>
      </motion.div>

      {/* 6. Native App Explanation Modal */}
      <AnimatePresence>
        {showAppModal && (
          <div className="roadmap-modal-overlay" onClick={() => setShowAppModal(false)}>
            <motion.div
              className="roadmap-modal"
              onClick={(e) => e.stopPropagation()}
              initial={{ opacity: 0, scale: 0.94, y: 16 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.94, y: 16 }}
              transition={{ duration: 0.25, ease: [0.16, 1, 0.3, 1] }}
            >
              <div className="roadmap-modal__header">
                <div className="roadmap-modal__icon-wrap">
                  <Smartphone size={20} className="roadmap-modal__icon" />
                </div>
                <div>
                  <h3 className="roadmap-modal__title">Promise for Android</h3>
                  <p className="roadmap-modal__sub">
                    Automatic midnight lock screen wallpapers & daily live rhythm
                  </p>
                </div>
                <button
                  className="roadmap-modal__close"
                  onClick={() => setShowAppModal(false)}
                >
                  <X size={18} />
                </button>
              </div>

              <div className="roadmap-modal__body">
                <div className="roadmap-modal__card">
                  <div className="roadmap-modal__card-top">
                    <Sparkles size={16} className="roadmap-modal__card-sparkle" />
                    <span>THE MIDNIGHT ENGINE</span>
                  </div>
                  <p className="roadmap-modal__card-text">
                    On Android, Promise is not just an app — it’s a quiet daily ritual.
                    The app hooks natively into Android's <code>WallpaperManager</code> API.
                    At exactly <strong>12:05 AM every single night</strong>, an offline
                    WorkManager job paints the new day’s illuminated dot directly onto your
                    phone screen without waking your device or draining battery.
                  </p>
                </div>

                <div className="roadmap-modal__list">
                  <div className="roadmap-modal__list-item">
                    <Check size={16} className="roadmap-modal__list-check" />
                    <div>
                      <strong>Lock & Home Screen Sync</strong>
                      <p>Pick lock screen, home screen, or seamless consistency across both.</p>
                    </div>
                  </div>
                  <div className="roadmap-modal__list-item">
                    <Check size={16} className="roadmap-modal__list-check" />
                    <div>
                      <strong>Offline Outbox Architecture</strong>
                      <p>
                        Mark promises and check into goals offline; updates seamlessly sync
                        whenever internet returns.
                      </p>
                    </div>
                  </div>
                  <div className="roadmap-modal__list-item">
                    <Check size={16} className="roadmap-modal__list-check" />
                    <div>
                      <strong>Interactive Home Screen Widgets</strong>
                      <p>Glance widgets for daily rituals, voice capture, and your 365 roadmap.</p>
                    </div>
                  </div>
                </div>

                <div className="roadmap-modal__download-box">
                  <div className="roadmap-modal__dl-info">
                    <span className="roadmap-modal__dl-tag">LOCAL BUILD / DISTRIBUTION</span>
                    <p className="roadmap-modal__dl-title">Promise Native Android APK</p>
                    <span className="roadmap-modal__dl-sub">
                      Packaged with Kotlin 2.1, Compose Material 3, & Android 15 support
                    </span>
                  </div>
                  <button
                    className="roadmap-modal__action-btn"
                    onClick={() => {
                      alert(
                        'The Android build is available in the repository at android/app. You can run ./gradlew assembleDebug or install directly to an Android device or emulator!'
                      )
                    }}
                  >
                    <span>Installation Instructions</span>
                    <ExternalLink size={14} />
                  </button>
                </div>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </div>
  )
}
