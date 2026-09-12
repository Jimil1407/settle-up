import { AnimatePresence, motion } from 'framer-motion'
import { ArrowRight, Plus, RotateCcw, Trash2 } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Reveal } from '../components/Reveal'
import { EASE } from '../components/Reveal'
import { formatPaise, rupeesToPaise, simplify, splitEvenly } from '../lib/settle'

const PEOPLE = ['Aditi', 'Rohan', 'Sana', 'Vikram']

interface DemoExpense {
  id: number
  label: string
  payer: string
  paise: number
}

const SEED: DemoExpense[] = [
  { id: 1, label: 'Villa, 3 nights', payer: 'Aditi', paise: 1_200_000 },
  { id: 2, label: 'Airport cab', payer: 'Rohan', paise: 320_000 },
  { id: 3, label: 'Groceries', payer: 'Sana', paise: 245_075 },
]

/**
 * Live product demo.
 *
 * Runs the real simplification algorithm in the browser, so the numbers on the
 * page are genuinely computed rather than hard-coded marketing copy. Add an
 * expense and watch the payment plan recompute.
 */
export function InteractiveDemo() {
  const [expenses, setExpenses] = useState<DemoExpense[]>(SEED)
  const [label, setLabel] = useState('')
  const [amount, setAmount] = useState('')
  const [payer, setPayer] = useState(PEOPLE[0])

  const { balances, transfers, total } = useMemo(() => {
    const balances: Record<string, number> = Object.fromEntries(PEOPLE.map((p) => [p, 0]))

    for (const expense of expenses) {
      balances[expense.payer] += expense.paise
      const shares = splitEvenly(expense.paise, PEOPLE)
      for (const person of PEOPLE) balances[person] -= shares[person]
    }

    return {
      balances,
      transfers: simplify(balances),
      total: expenses.reduce((sum, e) => sum + e.paise, 0),
    }
  }, [expenses])

  const outOfBalance = Object.values(balances).filter((v) => v !== 0).length
  const imbalance = Object.values(balances).reduce((a, b) => a + b, 0)

  function addExpense(event: React.FormEvent) {
    event.preventDefault()
    const paise = rupeesToPaise(amount)
    if (!label.trim() || paise <= 0) return
    setExpenses((prev) => [...prev, { id: Date.now(), label: label.trim(), payer, paise }])
    setLabel('')
    setAmount('')
  }

  return (
    <section id="demo" className="relative scroll-mt-24 py-section-sm sm:py-section">
      <div className="container-page">
        <Reveal className="mx-auto max-w-2xl text-center">
          <p className="eyebrow">Try it yourself</p>
          <h2 className="heading-lg mt-3 text-balance">Watch the mess collapse</h2>
          <p className="lede mx-auto mt-4 max-w-xl text-pretty">
            This is the real algorithm, running right here in your browser. Add an expense and the
            payment plan recomputes instantly.
          </p>
        </Reveal>

        <Reveal className="mt-12">
          <div className="card overflow-hidden rounded-3xl">
            <div className="grid lg:grid-cols-[1.1fr_1fr]">
              {/* ---- Left: inputs ---- */}
              <div className="border-b border-[rgb(var(--border))] p-6 sm:p-8 lg:border-b-0 lg:border-r">
                <div className="flex items-center justify-between">
                  <h3 className="font-semibold">Expenses</h3>
                  <button
                    type="button"
                    onClick={() => setExpenses(SEED)}
                    className="btn-ghost text-xs"
                  >
                    <RotateCcw className="h-3.5 w-3.5" />
                    Reset
                  </button>
                </div>

                <ul className="mt-4 space-y-2">
                  <AnimatePresence initial={false}>
                    {expenses.map((expense) => (
                      <motion.li
                        key={expense.id}
                        layout
                        initial={{ opacity: 0, y: -8 }}
                        animate={{ opacity: 1, y: 0 }}
                        exit={{ opacity: 0, x: -12 }}
                        transition={{ duration: 0.25, ease: EASE }}
                        className="group flex items-center justify-between rounded-xl border
                                   border-[rgb(var(--border))] bg-[rgb(var(--surface-2))] px-3.5 py-3"
                      >
                        <span className="min-w-0">
                          <span className="block truncate text-sm font-medium">{expense.label}</span>
                          <span className="text-xs muted">{expense.payer} paid</span>
                        </span>
                        <span className="flex items-center gap-2">
                          <span className="text-sm tabular-nums">{formatPaise(expense.paise)}</span>
                          <button
                            type="button"
                            aria-label={`Remove ${expense.label}`}
                            onClick={() =>
                              setExpenses((prev) => prev.filter((e) => e.id !== expense.id))
                            }
                            className="rounded-lg p-1.5 text-[rgb(var(--text-muted))] opacity-0
                                       transition hover:bg-red-500/10 hover:text-red-500
                                       focus-visible:opacity-100 group-hover:opacity-100"
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                          </button>
                        </span>
                      </motion.li>
                    ))}
                  </AnimatePresence>
                </ul>

                <form onSubmit={addExpense} className="mt-5 space-y-3">
                  <div className="grid gap-3 sm:grid-cols-2">
                    <div>
                      <label className="label" htmlFor="demo-label">
                        Description
                      </label>
                      <input
                        id="demo-label"
                        className="input"
                        value={label}
                        placeholder="Dinner"
                        onChange={(e) => setLabel(e.target.value)}
                      />
                    </div>
                    <div>
                      <label className="label" htmlFor="demo-amount">
                        Amount (₹)
                      </label>
                      <input
                        id="demo-amount"
                        className="input"
                        inputMode="decimal"
                        value={amount}
                        placeholder="1200.00"
                        onChange={(e) => setAmount(e.target.value)}
                      />
                    </div>
                  </div>
                  <div className="flex items-end gap-3">
                    <div className="flex-1">
                      <label className="label" htmlFor="demo-payer">
                        Paid by
                      </label>
                      <select
                        id="demo-payer"
                        className="input"
                        value={payer}
                        onChange={(e) => setPayer(e.target.value)}
                      >
                        {PEOPLE.map((p) => (
                          <option key={p} value={p}>
                            {p}
                          </option>
                        ))}
                      </select>
                    </div>
                    <button type="submit" className="btn-primary">
                      <Plus className="h-4 w-4" />
                      Add
                    </button>
                  </div>
                </form>
              </div>

              {/* ---- Right: computed output ---- */}
              <div className="bg-[rgb(var(--surface-2))] p-6 sm:p-8">
                <div className="flex items-baseline justify-between">
                  <h3 className="font-semibold">Balances</h3>
                  <span className="text-xs muted tabular-nums">{formatPaise(total)} total</span>
                </div>

                <div className="mt-4 space-y-1">
                  {PEOPLE.map((person) => {
                    const value = balances[person]
                    return (
                      <div key={person} className="flex items-center justify-between py-1.5">
                        <span className="text-sm">{person}</span>
                        <motion.span
                          key={value}
                          initial={{ opacity: 0.4 }}
                          animate={{ opacity: 1 }}
                          transition={{ duration: 0.3 }}
                          className={`text-sm font-medium tabular-nums ${
                            value > 0
                              ? 'text-emerald-500'
                              : value < 0
                                ? 'text-amber-500'
                                : 'text-[rgb(var(--text-muted))]'
                          }`}
                        >
                          {value > 0 ? 'gets ' : value < 0 ? 'owes ' : ''}
                          {formatPaise(Math.abs(value))}
                        </motion.span>
                      </div>
                    )
                  })}
                </div>

                <div className="my-5 rule" />

                <div className="flex items-baseline justify-between">
                  <h3 className="font-semibold">Payment plan</h3>
                  <span className="pill">
                    {outOfBalance} → {transfers.length}
                  </span>
                </div>

                <div className="mt-4 space-y-2">
                  <AnimatePresence initial={false} mode="popLayout">
                    {transfers.map((t) => (
                      <motion.div
                        key={`${t.from}-${t.to}`}
                        layout
                        initial={{ opacity: 0, scale: 0.96 }}
                        animate={{ opacity: 1, scale: 1 }}
                        exit={{ opacity: 0, scale: 0.96 }}
                        transition={{ duration: 0.25, ease: EASE }}
                        className="flex items-center justify-between rounded-xl border
                                   border-accent-500/25 bg-accent-500/[0.07] px-3.5 py-3"
                      >
                        <span className="flex items-center gap-2 text-sm">
                          <span className="font-medium">{t.from}</span>
                          <ArrowRight className="h-3.5 w-3.5 muted" />
                          <span className="font-medium">{t.to}</span>
                        </span>
                        <span className="text-sm font-semibold tabular-nums">
                          {formatPaise(t.amountPaise)}
                        </span>
                      </motion.div>
                    ))}
                  </AnimatePresence>

                  {transfers.length === 0 && (
                    <p className="rounded-xl border border-dashed border-[rgb(var(--border))]
                                  px-3.5 py-6 text-center text-sm muted">
                      Everyone is square.
                    </p>
                  )}
                </div>

                {/* The invariant, surfaced live — it must read ₹0.00 no matter what you add. */}
                <div className="mt-5 flex items-center justify-between rounded-xl border
                                border-emerald-500/20 bg-emerald-500/[0.07] px-3.5 py-2.5">
                  <span className="text-xs text-emerald-700 dark:text-emerald-300">
                    Ledger check
                  </span>
                  <span className="font-mono text-xs text-emerald-700 dark:text-emerald-300">
                    sum = {formatPaise(imbalance)}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </Reveal>
      </div>
    </section>
  )
}
