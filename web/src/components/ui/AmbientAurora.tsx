import './AmbientAurora.css'

export function AmbientAurora() {
  return (
    <div className="ambient-aurora" aria-hidden="true">
      <div className="ambient-aurora__orb ambient-aurora__orb--1" />
      <div className="ambient-aurora__orb ambient-aurora__orb--2" />
      <div className="ambient-aurora__orb ambient-aurora__orb--3" />
      <div className="ambient-aurora__noise" />
    </div>
  )
}
