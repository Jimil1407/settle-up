import { Check } from 'lucide-react'
import { Reveal } from '../components/Reveal'

interface Row {
  eyebrow: string
  title: string
  body: string
  points: string[]
  visual: React.ReactNode
}

function LedgerVisual() {
  const rows = [
    { who: 'Aditi', delta: '+₹900.00', note: 'paid for dinner' },
    { who: 'Aditi', delta: '−₹300.00', note: 'her share' },
    { who: 'Rohan', delta: '−₹300.00', note: 'his share' },
    { who: 'Sana', delta: '−₹300.00', note: 'her share' },
  ]
  return (
    <div className="card overflow-hidden p-0">
      <div className="border-b border-[rgb(var(--border))] bg-[rgb(var(--surface-2))] px-5 py-3">
        <p className="font-mono text-xs muted">balance_entry</p>
      </div>
      <div className="divide-y divide-[rgb(var(--border))]">
        {rows.map((row, i) => (
          <div key={i} className="flex items-center justify-between px-5 py-3">
            <span className="text-sm">
              <span className="font-medium">{row.who}</span>
              <span className="ml-2 text-xs muted">{row.note}</span>
            </span>
            <span
              className={`font-mono text-sm tabular-nums ${
                row.delta.startsWith('+') ? 'text-emerald-500' : 'muted'
              }`}
            >
              {row.delta}
            </span>
          </div>
        ))}
      </div>
      <div className="flex items-center justify-between border-t-2 border-[rgb(var(--border))]
                      bg-emerald-500/[0.07] px-5 py-3">
        <span className="text-sm font-medium text-emerald-700 dark:text-emerald-300">sum</span>
        <span className="font-mono text-sm font-semibold text-emerald-700 dark:text-emerald-300">
          ₹0.00
        </span>
      </div>
    </div>
  )
}

function ConflictVisual() {
  return (
    <div className="space-y-3">
      <div className="card p-5">
        <p className="text-xs muted">Rohan opens settle-up</p>
        <p className="mt-1 font-mono text-sm">version 1 · pays ₹500.00</p>
      </div>
      <div className="card border-amber-500/30 bg-amber-500/[0.06] p-5">
        <p className="text-xs text-amber-700 dark:text-amber-300">Aditi adds an expense</p>
        <p className="mt-1 font-mono text-sm">version → 2</p>
      </div>
      <div className="card border-red-500/30 bg-red-500/[0.06] p-5">
        <p className="font-mono text-xs text-red-600 dark:text-red-400">HTTP 409 Conflict</p>
        <p className="mt-1 text-sm leading-relaxed">
          Plan was computed at version 1 but the group is now at version 2 — re-fetch and try again.
        </p>
      </div>
    </div>
  )
}

function SimplifyVisual() {
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      <div className="card p-5">
        <p className="eyebrow !text-[rgb(var(--text-muted))]">Before</p>
        <ul className="mt-3 space-y-2 text-sm">
          {['Aditi → Rohan', 'Rohan → Sana', 'Sana → Aditi', 'Vikram → Rohan', 'Sana → Vikram'].map(
            (t) => (
              <li key={t} className="flex items-center gap-2 muted">
                <span className="h-1 w-1 rounded-full bg-current" />
                {t}
              </li>
            ),
          )}
        </ul>
      </div>
      <div className="card border-accent-500/30 bg-accent-500/[0.06] p-5">
        <p className="eyebrow">After</p>
        <ul className="mt-3 space-y-2 text-sm">
          {['Sana → Aditi', 'Vikram → Rohan'].map((t) => (
            <li key={t} className="flex items-center gap-2 font-medium">
              <Check className="h-3.5 w-3.5 text-accent-500" />
              {t}
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}

const ROWS: Row[] = [
  {
    eyebrow: 'Simplification',
    title: 'Stop paying five people',
    body: 'Only your net position matters. SettleUp throws away the tangle of who-owes-whom and rebuilds the shortest path back to zero.',
    points: [
      'At most one payment fewer than there are people',
      'Circular debts cancel out entirely',
      'Recomputed the moment anything changes',
    ],
    visual: <SimplifyVisual />,
  },
  {
    eyebrow: 'Correctness',
    title: 'Balances that always sum to zero',
    body: 'No stored totals to drift out of sync. Every expense appends signed entries that net to zero, so the books balance by construction.',
    points: [
      'Append-only — nothing is ever overwritten',
      'Full audit trail from balance back to receipt',
      'Verifiable live, not just in tests',
    ],
    visual: <LedgerVisual />,
  },
  {
    eyebrow: 'Concurrency',
    title: 'Nobody pays against stale numbers',
    body: 'If someone adds an expense while your settle-up screen is open, the amounts on it are fiction. We reject the payment instead of quietly applying it.',
    points: [
      'Every plan carries the version it came from',
      'Conflicting payments are refused, not guessed',
      'The app refreshes and explains what changed',
    ],
    visual: <ConflictVisual />,
  },
]

export function Benefits() {
  return (
    <section className="relative py-section-sm sm:py-section">
      <div className="container-page space-y-20 sm:space-y-28">
        {ROWS.map((row, i) => (
          <Reveal key={row.title}>
            <div className="grid items-center gap-10 lg:grid-cols-2 lg:gap-16">
              {/* Alternating sides on desktop; visual always follows copy on mobile. */}
              <div className={i % 2 === 1 ? 'lg:order-2' : ''}>
                <p className="eyebrow">{row.eyebrow}</p>
                <h2 className="heading-md mt-3 text-balance">{row.title}</h2>
                <p className="lede mt-4 text-pretty">{row.body}</p>
                <ul className="mt-6 space-y-3">
                  {row.points.map((point) => (
                    <li key={point} className="flex items-start gap-3 text-sm">
                      <span className="mt-0.5 grid h-5 w-5 shrink-0 place-items-center rounded-full
                                       bg-accent-500/15 text-accent-600 dark:text-accent-300">
                        <Check className="h-3 w-3" />
                      </span>
                      <span className="muted">{point}</span>
                    </li>
                  ))}
                </ul>
              </div>
              <div className={i % 2 === 1 ? 'lg:order-1' : ''}>{row.visual}</div>
            </div>
          </Reveal>
        ))}
      </div>
    </section>
  )
}
