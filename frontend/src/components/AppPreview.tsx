import { motion } from 'framer-motion'
import { ArrowRight, ShieldCheck } from 'lucide-react'

const MEMBERS = [
  { name: 'Aditi', amount: '+₹1,200.51', positive: true, initials: 'AD' },
  { name: 'Rohan', amount: '−₹50.25', positive: false, initials: 'RO' },
  { name: 'Sana', amount: '−₹1,150.26', positive: false, initials: 'SA' },
]

const EXPENSES = [
  { title: 'Villa, 3 nights', payer: 'Aditi', amount: '₹12,000.00' },
  { title: 'Airport cab', payer: 'Rohan', amount: '₹3,200.00' },
  { title: 'Groceries', payer: 'Sana', amount: '₹2,450.75' },
]

/**
 * Static product mockup for the hero.
 *
 * Rendered as real DOM rather than a screenshot: it stays crisp at any density,
 * responds to dark mode for free, and weighs a fraction of an image.
 */
export function AppPreview() {
  return (
    <div className="card overflow-hidden rounded-3xl p-0 shadow-card-hover">
      {/* Window chrome — a small cue that this is the product, not a diagram. */}
      <div className="flex items-center gap-2 border-b border-[rgb(var(--border))] bg-[rgb(var(--surface-2))] px-4 py-3">
        <span className="flex gap-1.5" aria-hidden>
          <span className="h-2.5 w-2.5 rounded-full bg-red-400/70" />
          <span className="h-2.5 w-2.5 rounded-full bg-amber-400/70" />
          <span className="h-2.5 w-2.5 rounded-full bg-emerald-400/70" />
        </span>
        <span className="mx-auto rounded-md bg-[rgb(var(--surface))] px-3 py-1 text-[11px] muted">
          settleup.app/groups/goa-trip
        </span>
      </div>

      <div className="grid gap-6 p-5 sm:p-7 lg:grid-cols-[1.15fr_1fr]">
        <div>
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-lg font-semibold tracking-[-0.01em]">Goa trip</h3>
              <p className="text-xs muted">3 members · 3 expenses</p>
            </div>
            <span className="pill">v3</span>
          </div>

          <div className="mt-5 space-y-1">
            {MEMBERS.map((m, i) => (
              <motion.div
                key={m.name}
                initial={{ opacity: 0, x: -8 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: 0.6 + i * 0.1, duration: 0.5 }}
                className="flex items-center justify-between rounded-xl px-2.5 py-2.5
                           transition-colors hover:bg-[rgb(var(--surface-2))]"
              >
                <span className="flex items-center gap-3">
                  <span className="grid h-8 w-8 place-items-center rounded-full bg-gradient-to-br
                                   from-accent-400/20 to-accent-600/20 text-[11px] font-semibold
                                   text-accent-600 dark:text-accent-300">
                    {m.initials}
                  </span>
                  <span className="text-sm font-medium">{m.name}</span>
                </span>
                <span
                  className={`text-sm font-medium tabular-nums ${
                    m.positive ? 'text-emerald-500' : 'text-[rgb(var(--text-muted))]'
                  }`}
                >
                  {m.amount}
                </span>
              </motion.div>
            ))}
          </div>

          <div className="mt-5 flex items-center gap-2 rounded-xl border border-emerald-500/20
                          bg-emerald-500/[0.07] px-3 py-2.5">
            <ShieldCheck className="h-4 w-4 shrink-0 text-emerald-500" />
            <p className="text-xs text-emerald-700 dark:text-emerald-300">
              Ledger balanced — entries sum to ₹0.00
            </p>
          </div>
        </div>

        <div className="space-y-4">
          <div className="rounded-2xl border border-accent-500/25 bg-accent-500/[0.06] p-4">
            <p className="eyebrow">Settle up</p>
            <p className="mt-2 text-sm muted">
              3 people out of balance, simplified to <span className="font-semibold text-[rgb(var(--text))]">2 payments</span>
            </p>

            <div className="mt-3 space-y-2">
              {[
                { from: 'Sana', to: 'Aditi', amount: '₹1,150.26' },
                { from: 'Rohan', to: 'Aditi', amount: '₹50.25' },
              ].map((t, i) => (
                <motion.div
                  key={t.from}
                  initial={{ opacity: 0, y: 6 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 0.95 + i * 0.12, duration: 0.5 }}
                  className="flex items-center justify-between rounded-xl border border-[rgb(var(--border))]
                             bg-[rgb(var(--surface))] px-3 py-2.5"
                >
                  <span className="flex items-center gap-1.5 text-xs">
                    <span className="font-medium">{t.from}</span>
                    <ArrowRight className="h-3 w-3 muted" />
                    <span className="font-medium">{t.to}</span>
                  </span>
                  <span className="text-xs font-semibold tabular-nums">{t.amount}</span>
                </motion.div>
              ))}
            </div>
          </div>

          <div>
            <p className="mb-2 text-xs font-medium muted">Recent</p>
            <div className="space-y-1">
              {EXPENSES.map((e) => (
                <div key={e.title} className="flex items-center justify-between px-1 py-1.5">
                  <span className="min-w-0">
                    <span className="block truncate text-xs font-medium">{e.title}</span>
                    <span className="text-[11px] muted">{e.payer} paid</span>
                  </span>
                  <span className="text-xs tabular-nums muted">{e.amount}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
