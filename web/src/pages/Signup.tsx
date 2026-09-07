import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'

// Promise uses Google-only auth — signup and login are the exact same flow.
export default function Signup() {
  const navigate = useNavigate()
  useEffect(() => {
    navigate('/login', { replace: true })
  }, [navigate])
  return null
}
