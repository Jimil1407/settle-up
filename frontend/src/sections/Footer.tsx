import { Link } from 'react-router-dom'
import { Logo } from '../components/Logo'

const COLUMNS = [
  {
    title: 'Product',
    links: [
      { label: 'Features', href: '#features' },
      { label: 'How it works', href: '#demo' },
      { label: 'Pricing', href: '#pricing' },
      { label: 'FAQ', href: '#faq' },
    ],
  },
  {
    title: 'Company',
    links: [
      { label: 'About', href: '#' },
      { label: 'Blog', href: '#' },
      { label: 'Careers', href: '#' },
      { label: 'Contact', href: 'mailto:hello@settleup.app' },
    ],
  },
  {
    title: 'Legal',
    links: [
      { label: 'Privacy', href: '#' },
      { label: 'Terms', href: '#' },
      { label: 'Security', href: '#' },
      { label: 'Status', href: '#' },
    ],
  },
]

export function Footer() {
  return (
    <footer className="relative border-t border-[rgb(var(--border))] bg-[rgb(var(--surface-2))]">
      <div className="container-page py-16">
        <div className="grid gap-12 lg:grid-cols-[1.4fr_2fr]">
          <div>
            <Logo />
            <p className="mt-4 max-w-xs text-sm leading-relaxed text-pretty muted">
              Shared expenses that actually add up. Built for flats, trips, and every group chat
              that has ever argued about money.
            </p>
          </div>

          <div className="grid grid-cols-2 gap-8 sm:grid-cols-3">
            {COLUMNS.map((column) => (
              <div key={column.title}>
                <h3 className="text-xs font-semibold uppercase tracking-[0.12em] muted">
                  {column.title}
                </h3>
                <ul className="mt-4 space-y-2.5">
                  {column.links.map((link) => (
                    <li key={link.label}>
                      <a
                        href={link.href}
                        className="text-sm muted transition-colors hover:text-[rgb(var(--text))]"
                      >
                        {link.label}
                      </a>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
        </div>

        <div className="mt-14 flex flex-col items-center justify-between gap-4 border-t
                        border-[rgb(var(--border))] pt-8 sm:flex-row">
          <p className="text-xs muted">
            © {new Date().getFullYear()} SettleUp. Every paisa accounted for.
          </p>
          <Link
            to="/app"
            className="text-xs font-medium text-accent-600 underline-offset-4 hover:underline dark:text-accent-400"
          >
            Open the app →
          </Link>
        </div>
      </div>
    </footer>
  )
}
