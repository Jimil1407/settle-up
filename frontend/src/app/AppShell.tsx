import { motion } from 'framer-motion'
import { LogOut } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Logo } from '../components/Logo'
import { ThemeToggle } from '../components/ThemeToggle'
import { EASE } from '../components/Reveal'
import type { AuthResponse } from '../types'

interface Props {
  user: AuthResponse
  onSignOut: () => void
  children: React.ReactNode
}

export function AppShell({ user, onSignOut, children }: Props) {
  const initials = user.displayName
    .split(' ')
    .map((part) => part[0])
    .slice(0, 2)
    .join('')
    .toUpperCase()

  return (
    <div className="relative min-h-screen">
      <header className="glass sticky top-0 z-40 border-b">
        <div className="container-page flex h-16 items-center justify-between">
          <Link to="/" className="rounded-lg" aria-label="SettleUp home">
            <Logo />
          </Link>

          <div className="flex items-center gap-2">
            <ThemeToggle />
            <span className="hidden items-center gap-2.5 rounded-xl border border-[rgb(var(--border))]
                             bg-[rgb(var(--surface))] py-1.5 pl-1.5 pr-3 sm:flex">
              <span
                aria-hidden
                className="grid h-7 w-7 place-items-center rounded-lg bg-gradient-to-br
                           from-accent-400/20 to-accent-600/20 text-[11px] font-semibold
                           text-accent-600 dark:text-accent-300"
              >
                {initials}
              </span>
              <span className="text-sm font-medium">{user.displayName}</span>
            </span>
            <button type="button" onClick={onSignOut} className="btn-ghost" aria-label="Sign out">
              <LogOut className="h-4 w-4" />
              <span className="hidden sm:inline">Sign out</span>
            </button>
          </div>
        </div>
      </header>

      <motion.main
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, ease: EASE }}
        className="container-page py-10"
      >
        {children}
      </motion.main>
    </div>
  )
}
