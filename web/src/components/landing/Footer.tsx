import { Link } from 'react-router-dom'
import './Footer.css'

export default function Footer() {
  return (
    <footer className="footer" aria-label="Site footer">
      <div className="container footer__inner">
        <div className="footer__brand">
          <Link to="/" className="footer__wordmark" aria-label="Promise home">Promise</Link>
          <p className="footer__tagline">Keep what you say.</p>
          <p className="footer__copy">© {new Date().getFullYear()} Promise Studio. All rights reserved.</p>
        </div>

        <nav className="footer__links" aria-label="Footer navigation">
          <div className="footer__col">
            <span className="footer__col-label">Product</span>
            <a href="#features" className="footer__link">Features</a>
            <a href="#ai" className="footer__link">Gemini AI</a>
            <a href="#goals" className="footer__link">Goals</a>
            <a href="#shared" className="footer__link">Shared Goals</a>
          </div>
          <div className="footer__col">
            <span className="footer__col-label">App</span>
            <Link to="/login" className="footer__link">Sign in</Link>
            <Link to="/login" className="footer__link">Get started</Link>
          </div>
          <div className="footer__col">
            <span className="footer__col-label">Values</span>
            <span className="footer__text">No ads. No notifications by default. No public feed. No dark patterns.</span>
          </div>
        </nav>
      </div>

      <div className="footer__bottom">
        <div className="container footer__bottom-inner">
          <span className="footer__promise-rule" aria-hidden="true" />
          <span className="footer__made">Crafted for clarity and focus</span>
          <span className="footer__promise-rule" aria-hidden="true" />
        </div>
      </div>
    </footer>
  )
}
