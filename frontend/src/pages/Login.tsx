import { motion } from 'framer-motion'
import { ArrowLeft } from 'lucide-react'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api, setToken } from '../api'
import { BackgroundFX } from '../components/BackgroundFX'
import { Logo } from '../components/Logo'
import { EASE } from '../components/Reveal'
import { ThemeToggle } from '../components/ThemeToggle'
import type { AuthResponse } from '../types'

export function Login({ onAuth }: { onAuth: (auth: AuthResponse) => void }) {
  const [params] = useSearchParams()
  // Deep links from the landing page CTAs decide which form you land on.
  const [mode, setMode] = useState<'login' | 'register'>(
    params.get('mode') === 'register' ? 'register' : 'login',
  )
  const [email, setEmail] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      const auth =
        mode === 'login'
          ? await api.login(email, password)
          : await api.register(email, displayName, password)
      setToken(auth.token)
      onAuth(auth)
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="relative isolate grid min-h-screen place-items-center overflow-hidden px-6 py-16">
      <BackgroundFX variant="hero" />

      <div className="absolute inset-x-0 top-0">
        <div className="container-page flex h-16 items-center justify-between">
          <Link to="/" className="rounded-lg">
            <Logo />
          </Link>
          <div className="flex items-center gap-2">
            <ThemeToggle />
            <Link to="/" className="btn-ghost">
              <ArrowLeft className="h-4 w-4" />
              Home
            </Link>
          </div>
        </div>
      </div>

      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.6, ease: EASE }}
        className="w-full max-w-[26rem]"
      >
        <div className="card rounded-3xl p-8">
          <h1 className="heading-md">{mode === 'login' ? 'Welcome back' : 'Create your account'}</h1>
          <p className="mt-2 text-sm muted">
            {mode === 'login'
              ? 'Pick up where your group left off.'
              : 'Free for up to three groups. No card needed.'}
          </p>

          {error && (
            <motion.div
              initial={{ opacity: 0, y: -6 }}
              animate={{ opacity: 1, y: 0 }}
              role="alert"
              className="mt-5 rounded-xl border border-red-500/30 bg-red-500/[0.08] px-3.5 py-2.5
                         text-sm text-red-600 dark:text-red-400"
            >
              {error}
            </motion.div>
          )}

          <form onSubmit={submit} className="mt-6 space-y-4">
            <div>
              <label className="label" htmlFor="email">
                Email
              </label>
              <input
                id="email"
                className="input"
                type="email"
                autoComplete="email"
                value={email}
                required
                onChange={(e) => setEmail(e.target.value)}
              />
            </div>

            {mode === 'register' && (
              <div>
                <label className="label" htmlFor="name">
                  Name
                </label>
                <input
                  id="name"
                  className="input"
                  autoComplete="name"
                  value={displayName}
                  required
                  minLength={2}
                  onChange={(e) => setDisplayName(e.target.value)}
                />
              </div>
            )}

            <div>
              <label className="label" htmlFor="password">
                Password
              </label>
              <input
                id="password"
                className="input"
                type="password"
                autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                value={password}
                required
                minLength={8}
                onChange={(e) => setPassword(e.target.value)}
              />
              {mode === 'register' && (
                <p className="mt-1.5 text-xs muted">At least 8 characters.</p>
              )}
            </div>

            <button type="submit" className="btn-primary w-full py-3" disabled={busy}>
              {busy ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}
            </button>
          </form>

          <p className="mt-6 text-center text-sm muted">
            {mode === 'login' ? "Don't have an account? " : 'Already registered? '}
            <button
              type="button"
              className="font-medium text-accent-600 underline-offset-4 hover:underline dark:text-accent-400"
              onClick={() => {
                setMode(mode === 'login' ? 'register' : 'login')
                setError(null)
              }}
            >
              {mode === 'login' ? 'Sign up' : 'Sign in'}
            </button>
          </p>
        </div>
      </motion.div>
    </div>
  )
}
