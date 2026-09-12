import { AnimatePresence, motion } from 'framer-motion'
import { ArrowRight, Menu, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Logo } from './Logo'
import { ThemeToggle } from './ThemeToggle'
import { EASE } from './Reveal'

const LINKS = [
  { label: 'Features', href: '#features' },
  { label: 'How it works', href: '#demo' },
  { label: 'Pricing', href: '#pricing' },
  { label: 'FAQ', href: '#faq' },
]

export function Navbar({ authed }: { authed: boolean }) {
  const [scrolled, setScrolled] = useState(false)
  const [open, setOpen] = useState(false)

  useEffect(() => {
    // The navbar stays transparent over the hero and only gains its frosted
    // background once content is behind it, so it never competes with the headline.
    const onScroll = () => setScrolled(window.scrollY > 12)
    onScroll()
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  // A fixed backdrop with the mobile sheet open would otherwise scroll behind it.
  useEffect(() => {
    document.body.style.overflow = open ? 'hidden' : ''
    return () => {
      document.body.style.overflow = ''
    }
  }, [open])

  return (
    <header className="fixed inset-x-0 top-0 z-50">
      <motion.div
        initial={{ y: -24, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        transition={{ duration: 0.5, ease: EASE }}
        className={`transition-all duration-300 ${
          scrolled
            ? 'glass border-b shadow-[0_1px_0_rgb(var(--border))]'
            : 'border-b border-transparent bg-transparent'
        }`}
      >
        <nav className="container-page flex h-16 items-center justify-between" aria-label="Main">
          <Link to="/" className="rounded-lg" aria-label="SettleUp home">
            <Logo />
          </Link>

          <div className="hidden items-center gap-1 md:flex">
            {LINKS.map((link) => (
              <a
                key={link.href}
                href={link.href}
                className="rounded-lg px-3 py-2 text-sm text-[rgb(var(--text-muted))]
                           transition-colors hover:text-[rgb(var(--text))]"
              >
                {link.label}
              </a>
            ))}
          </div>

          <div className="flex items-center gap-2">
            <ThemeToggle />
            <Link to="/app" className="btn-ghost hidden sm:inline-flex">
              {authed ? 'Dashboard' : 'Sign in'}
            </Link>
            <Link to="/app?mode=register" className="btn-primary group hidden sm:inline-flex">
              Start free
              <ArrowRight className="h-4 w-4 transition-transform duration-200 group-hover:translate-x-0.5" />
            </Link>

            <button
              type="button"
              onClick={() => setOpen((v) => !v)}
              aria-label={open ? 'Close menu' : 'Open menu'}
              aria-expanded={open}
              className="btn h-9 w-9 rounded-xl border border-[rgb(var(--border))] md:hidden"
            >
              {open ? <X className="h-4 w-4" /> : <Menu className="h-4 w-4" />}
            </button>
          </div>
        </nav>
      </motion.div>

      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0, y: -8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -8 }}
            transition={{ duration: 0.2, ease: EASE }}
            className="glass border-b md:hidden"
          >
            <div className="container-page flex flex-col gap-1 py-4">
              {LINKS.map((link) => (
                <a
                  key={link.href}
                  href={link.href}
                  onClick={() => setOpen(false)}
                  className="rounded-lg px-3 py-2.5 text-sm text-[rgb(var(--text-muted))]
                             hover:bg-[rgb(var(--surface-2))] hover:text-[rgb(var(--text))]"
                >
                  {link.label}
                </a>
              ))}
              <div className="mt-2 flex flex-col gap-2">
                <Link to="/app" className="btn-secondary w-full" onClick={() => setOpen(false)}>
                  {authed ? 'Dashboard' : 'Sign in'}
                </Link>
                <Link
                  to="/app?mode=register"
                  className="btn-primary w-full"
                  onClick={() => setOpen(false)}
                >
                  Start free
                </Link>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </header>
  )
}
