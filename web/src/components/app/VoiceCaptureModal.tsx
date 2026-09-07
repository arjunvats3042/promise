import { useState, useEffect, useRef } from 'react'
import { motion } from 'framer-motion'
import { Mic, MicOff, X, Sparkles, RefreshCw } from 'lucide-react'
import './VoiceCaptureModal.css'

interface VoiceCaptureModalProps {
  isOpen: boolean
  onClose: () => void
  onThoughtCaptured: (thought: string) => void
}

export default function VoiceCaptureModal({
  isOpen,
  onClose,
  onThoughtCaptured,
}: VoiceCaptureModalProps) {
  const [isRecording, setIsRecording] = useState(false)
  const [transcript, setTranscript] = useState('')
  const [isSupported, setIsSupported] = useState(true)
  const recognitionRef = useRef<any>(null)

  useEffect(() => {
    if (!isOpen) {
      if (recognitionRef.current) {
        try {
          recognitionRef.current.stop()
        } catch {}
      }
      setIsRecording(false)
      setTranscript('')
      return
    }

    // Check for Web Speech API
    const SpeechRecognition =
      (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition

    if (!SpeechRecognition) {
      setIsSupported(false)
      return
    }

    try {
      const recognition = new SpeechRecognition()
      recognition.continuous = true
      recognition.interimResults = true
      recognition.lang = 'en-US'

      recognition.onstart = () => {
        setIsRecording(true)
      }

      recognition.onresult = (event: any) => {
        let current = ''
        for (let i = 0; i < event.results.length; i++) {
          current += event.results[i][0].transcript
        }
        setTranscript(current)
      }

      recognition.onerror = () => {
        setIsRecording(false)
      }

      recognition.onend = () => {
        setIsRecording(false)
      }

      recognitionRef.current = recognition
      recognition.start()
    } catch {
      setIsSupported(false)
    }

    return () => {
      if (recognitionRef.current) {
        try {
          recognitionRef.current.stop()
        } catch {}
      }
    }
  }, [isOpen])

  if (!isOpen) return null

  function toggleRecording() {
    if (!isSupported) return
    if (isRecording) {
      if (recognitionRef.current) {
        try {
          recognitionRef.current.stop()
        } catch {}
      }
      setIsRecording(false)
    } else {
      if (recognitionRef.current) {
        try {
          recognitionRef.current.start()
          setIsRecording(true)
        } catch {}
      }
    }
  }

  function handleConfirm() {
    if (!transcript.trim()) return
    onThoughtCaptured(transcript.trim())
    onClose()
  }

  return (
    <div className="voice-modal__overlay" onClick={onClose}>
      <motion.div
        className="voice-modal__card"
        onClick={(e) => e.stopPropagation()}
        initial={{ opacity: 0, scale: 0.95, y: 20 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.95, y: 20 }}
        transition={{ type: 'spring', stiffness: 420, damping: 30 }}
      >
        <button className="voice-modal__close" onClick={onClose} aria-label="Close">
          <X size={16} />
        </button>

        {/* Pulsing Glowing Audio Orb */}
        <div className="voice-modal__orb-wrap">
          <motion.div
            className={`voice-modal__wave ${isRecording ? 'voice-modal__wave--active' : ''}`}
            animate={
              isRecording
                ? {
                    scale: [1, 1.25, 1.05, 1.3, 1],
                    opacity: [0.6, 0.2, 0.7, 0.15, 0.6],
                  }
                : {}
            }
            transition={{ duration: 2, repeat: Infinity, ease: 'easeInOut' }}
          />

          <motion.button
            className={`voice-modal__orb ${isRecording ? 'voice-modal__orb--recording' : ''}`}
            onClick={toggleRecording}
            whileHover={{ scale: 1.06 }}
            whileTap={{ scale: 0.94 }}
          >
            {isRecording ? <Mic size={32} /> : <MicOff size={30} />}
          </motion.button>
        </div>

        <h3 className="voice-modal__title">
          {!isSupported
            ? 'Voice input unavailable'
            : isRecording
            ? 'Listening to your thought…'
            : 'Tap microphone to speak'}
        </h3>
        <p className="voice-modal__sub">
          {!isSupported
            ? 'Web Speech API is not supported in this browser. You can type your thought directly below.'
            : isRecording
            ? 'Speak freely — e.g. "Call mom tomorrow at 4pm and read 20 pages daily"'
            : 'Press the orb to start recording, or type below.'}
        </p>

        {/* Live Transcript / Input */}
        <div className="voice-modal__transcript-wrap">
          <textarea
            className="voice-modal__transcript-input"
            rows={3}
            placeholder="Your spoken thought will appear here in real time…"
            value={transcript}
            onChange={(e) => setTranscript(e.target.value)}
          />
        </div>

        {/* Action Controls */}
        <div className="voice-modal__actions">
          {transcript && (
            <button
              className="voice-modal__clear-btn"
              onClick={() => setTranscript('')}
            >
              <RefreshCw size={13} />
              <span>Clear</span>
            </button>
          )}

          <motion.button
            className="voice-modal__parse-btn"
            disabled={!transcript.trim()}
            onClick={handleConfirm}
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
          >
            <Sparkles size={15} />
            <span>Parse with Gemini AI</span>
          </motion.button>
        </div>
      </motion.div>
    </div>
  )
}
