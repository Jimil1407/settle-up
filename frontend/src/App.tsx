import { Suspense, lazy, useState } from 'react'
import { Navigate, Route, Routes, useNavigate } from 'react-router-dom'
import { getToken, setToken } from './api'
import { Landing } from './pages/Landing'
import type { AuthResponse } from './types'

// The dashboard is only reachable behind sign-in, so it is split out of the main
// bundle: a first-time visitor landing on the marketing page should not download
// the app shell, group views and their dependencies before seeing the hero.
const Dashboard = lazy(() =>
  import('./pages/Dashboard').then((m) => ({ default: m.Dashboard })),
)
const Login = lazy(() => import('./pages/Login').then((m) => ({ default: m.Login })))

const USER_KEY = 'settleup.user'

function storedUser(): AuthResponse | null {
  const raw = localStorage.getItem(USER_KEY)
  // Both must be present: a stale user object without a token would render a
  // dashboard whose every request immediately 401s.
  if (!raw || !getToken()) return null
  try {
    return JSON.parse(raw) as AuthResponse
  } catch {
    return null
  }
}

/** Neutral placeholder while a split chunk loads; avoids a flash of empty page. */
function RouteFallback() {
  return (
    <div className="grid min-h-screen place-items-center">
      <div className="h-8 w-8 animate-spin rounded-full border-2 border-[rgb(var(--border))] border-t-accent-500" />
    </div>
  )
}

export default function App() {
  const [user, setUser] = useState<AuthResponse | null>(storedUser)
  const navigate = useNavigate()

  function onAuth(auth: AuthResponse) {
    localStorage.setItem(USER_KEY, JSON.stringify(auth))
    setUser(auth)
  }

  function signOut() {
    setToken(null)
    localStorage.removeItem(USER_KEY)
    setUser(null)
    navigate('/')
  }

  return (
    <Suspense fallback={<RouteFallback />}>
      <Routes>
        <Route path="/" element={<Landing authed={user !== null} />} />
        <Route
          path="/app"
          element={user ? <Dashboard user={user} onSignOut={signOut} /> : <Login onAuth={onAuth} />}
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  )
}
