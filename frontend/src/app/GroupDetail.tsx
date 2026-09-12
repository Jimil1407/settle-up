import { AnimatePresence, motion } from 'framer-motion'
import { ArrowLeft, ArrowRight, Check, Download, Plus, ShieldCheck, UserPlus } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { ApiError, api, newIdempotencyKey } from '../api'
import { EASE } from '../components/Reveal'
import type { Expense, Group, GroupBalances, SettlementPlan, Transfer } from '../types'

interface Props {
  groupId: number
  currentUserId: number
  onBack: () => void
}

export function GroupDetail({ groupId, currentUserId, onBack }: Props) {
  const [group, setGroup] = useState<Group | null>(null)
  const [balances, setBalances] = useState<GroupBalances | null>(null)
  const [expenses, setExpenses] = useState<Expense[]>([])
  const [plan, setPlan] = useState<SettlementPlan | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const [description, setDescription] = useState('')
  const [amount, setAmount] = useState('')
  const [paidBy, setPaidBy] = useState<number>(currentUserId)
  const [inviteEmail, setInviteEmail] = useState('')

  const load = useCallback(async () => {
    try {
      const [g, b, e, p] = await Promise.all([
        api.getGroup(groupId),
        api.balances(groupId),
        api.listExpenses(groupId),
        api.settlementPlan(groupId),
      ])
      setGroup(g)
      setBalances(b)
      setExpenses(e)
      setPlan(p)
    } catch (err) {
      setError((err as Error).message)
    }
  }, [groupId])

  useEffect(() => {
    void load()
  }, [load])

  async function addExpense(event: React.FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      await api.addExpense(
        groupId,
        { description, amount, paidByUserId: paidBy, splitType: 'EQUAL' },
        // One key per submitted form. If the network drops and the user hits Add
        // again, the retry carries the same key and cannot create a second expense.
        newIdempotencyKey(),
      )
      setDescription('')
      setAmount('')
      await load()
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setBusy(false)
    }
  }

  async function invite(event: React.FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api.addMember(groupId, inviteEmail.trim())
      setInviteEmail('')
      await load()
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setBusy(false)
    }
  }

  async function settle(transfer: Transfer) {
    if (!plan) return
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      await api.recordSettlement(
        groupId,
        {
          fromUserId: transfer.fromUserId,
          toUserId: transfer.toUserId,
          amount: transfer.amountRupees,
          // Echoing the plan's version lets the server reject this if somebody
          // added an expense while the plan was on screen.
          expectedVersion: plan.version,
        },
        newIdempotencyKey(),
      )
      await load()
      setNotice('Payment recorded.')
    } catch (err) {
      if (err instanceof ApiError && err.isStalePlan) {
        // Exactly the race this app is built to handle: refresh and let the user
        // re-confirm against real numbers rather than silently paying a stale amount.
        await load()
        setNotice('Someone changed this group while you were looking — the plan has been refreshed.')
      } else {
        setError((err as Error).message)
      }
    } finally {
      setBusy(false)
    }
  }

  async function downloadCsv() {
    setBusy(true)
    setError(null)
    try {
      const blob = await api.exportCsv(groupId)
      // Object URLs are revoked immediately after the click so the blob can be garbage collected
      // rather than pinned for the lifetime of the tab.
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `${group?.name ?? 'group'}-expenses.csv`
      link.click()
      URL.revokeObjectURL(url)
    } catch {
      setError('Could not export this group right now.')
    } finally {
      setBusy(false)
    }
  }

  if (!group || !balances) {
    return (
      <>
        <button type="button" onClick={onBack} className="btn-ghost -ml-3">
          <ArrowLeft className="h-4 w-4" />
          All groups
        </button>
        {error ? (
          <div role="alert" className="mt-6 rounded-xl border border-red-500/30 bg-red-500/[0.08]
                                       px-3.5 py-2.5 text-sm text-red-600 dark:text-red-400">
            {error}
          </div>
        ) : (
          <div className="mt-6 space-y-3">
            <div className="h-32 animate-pulse rounded-2xl bg-[rgb(var(--surface-2))]" />
            <div className="h-64 animate-pulse rounded-2xl bg-[rgb(var(--surface-2))]" />
          </div>
        )}
      </>
    )
  }

  return (
    <>
      <button type="button" onClick={onBack} className="btn-ghost -ml-3">
        <ArrowLeft className="h-4 w-4" />
        All groups
      </button>

      <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="heading-md">{group.name}</h1>
          <p className="mt-1 text-sm muted">
            {group.members.length} members · {expenses.length} expenses
          </p>
        </div>
        <div className="flex items-center gap-2">
          <span className="pill">version {balances.version}</span>
          <button type="button" onClick={downloadCsv} className="btn-secondary" disabled={busy}>
            <Download className="h-4 w-4" />
            <span className="hidden sm:inline">Export</span>
          </button>
        </div>
      </div>

      <AnimatePresence>
        {error && (
          <motion.div
            initial={{ opacity: 0, y: -6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
            role="alert"
            className="mt-5 rounded-xl border border-red-500/30 bg-red-500/[0.08] px-3.5 py-2.5
                       text-sm text-red-600 dark:text-red-400"
          >
            {error}
          </motion.div>
        )}
        {notice && (
          <motion.div
            initial={{ opacity: 0, y: -6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
            role="status"
            className="mt-5 rounded-xl border border-accent-500/30 bg-accent-500/[0.08] px-3.5 py-2.5
                       text-sm text-accent-700 dark:text-accent-300"
          >
            {notice}
          </motion.div>
        )}
      </AnimatePresence>

      <div className="mt-6 grid gap-4 lg:grid-cols-[1.15fr_1fr]">
        {/* ---- Balances ---- */}
        <section className="card p-6">
          <h2 className="font-semibold">Balances</h2>
          {balances.settled ? (
            <div className="mt-4 flex items-center gap-2.5 rounded-xl border border-emerald-500/20
                            bg-emerald-500/[0.07] px-3.5 py-3">
              <ShieldCheck className="h-4 w-4 shrink-0 text-emerald-500" />
              <p className="text-sm text-emerald-700 dark:text-emerald-300">
                Everyone is square. Nothing to settle.
              </p>
            </div>
          ) : (
            <div className="mt-3 divide-y divide-[rgb(var(--border))]">
              {balances.balances.map((b) => (
                <div key={b.userId} className="flex items-center justify-between py-3">
                  <span className="text-sm">
                    {b.displayName}
                    {b.userId === currentUserId && <span className="ml-1.5 text-xs muted">(you)</span>}
                  </span>
                  <span
                    className={`text-sm font-medium tabular-nums ${
                      b.balancePaise > 0
                        ? 'text-emerald-500'
                        : b.balancePaise < 0
                          ? 'text-amber-500'
                          : 'muted'
                    }`}
                  >
                    {b.balancePaise > 0 ? 'gets back ' : b.balancePaise < 0 ? 'owes ' : ''}
                    {b.formatted.replace('-', '')}
                  </span>
                </div>
              ))}
            </div>
          )}
        </section>

        {/* ---- Settle up ---- */}
        <section
          className={`card p-6 ${
            plan && plan.transfers.length > 0 ? 'border-accent-500/30 bg-accent-500/[0.04]' : ''
          }`}
        >
          <h2 className="font-semibold">Settle up</h2>
          {plan && plan.transfers.length > 0 ? (
            <>
              <p className="mt-1 text-sm muted">
                {plan.rawDebtCount} out of balance, simplified to{' '}
                <span className="font-medium text-[rgb(var(--text))]">
                  {plan.simplifiedTransferCount} payment
                  {plan.simplifiedTransferCount === 1 ? '' : 's'}
                </span>
              </p>
              <div className="mt-4 space-y-2">
                {plan.transfers.map((t, i) => (
                  <motion.div
                    key={`${t.fromUserId}-${t.toUserId}-${i}`}
                    initial={{ opacity: 0, y: 6 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ duration: 0.3, ease: EASE, delay: i * 0.05 }}
                    className="flex items-center justify-between gap-3 rounded-xl border
                               border-[rgb(var(--border))] bg-[rgb(var(--surface))] px-3.5 py-3"
                  >
                    <span className="flex min-w-0 items-center gap-2 text-sm">
                      <span className="truncate font-medium">{t.fromName}</span>
                      <ArrowRight className="h-3.5 w-3.5 shrink-0 muted" />
                      <span className="truncate font-medium">{t.toName}</span>
                      <span className="shrink-0 tabular-nums muted">{t.formatted}</span>
                    </span>
                    <button
                      type="button"
                      className="btn-secondary shrink-0 px-3 py-1.5 text-xs"
                      disabled={busy}
                      onClick={() => settle(t)}
                    >
                      <Check className="h-3.5 w-3.5" />
                      Mark paid
                    </button>
                  </motion.div>
                ))}
              </div>
            </>
          ) : (
            <p className="mt-3 text-sm muted">No payments needed right now.</p>
          )}
        </section>
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        {/* ---- Add expense ---- */}
        <form onSubmit={addExpense} className="card p-6">
          <h2 className="font-semibold">Add an expense</h2>
          <div className="mt-4 space-y-3">
            <div>
              <label className="label" htmlFor="desc">
                Description
              </label>
              <input
                id="desc"
                className="input"
                value={description}
                required
                placeholder="Dinner, groceries…"
                onChange={(e) => setDescription(e.target.value)}
              />
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <div>
                <label className="label" htmlFor="amt">
                  Amount (₹)
                </label>
                <input
                  id="amt"
                  className="input"
                  value={amount}
                  required
                  inputMode="decimal"
                  placeholder="1200.00"
                  onChange={(e) => setAmount(e.target.value)}
                />
              </div>
              <div>
                <label className="label" htmlFor="payer">
                  Paid by
                </label>
                <select
                  id="payer"
                  className="input"
                  value={paidBy}
                  onChange={(e) => setPaidBy(Number(e.target.value))}
                >
                  {group.members.map((m) => (
                    <option key={m.userId} value={m.userId}>
                      {m.displayName}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          </div>
          <button type="submit" className="btn-primary mt-4 w-full" disabled={busy}>
            <Plus className="h-4 w-4" />
            Add expense
          </button>
          <p className="mt-2 text-xs muted">
            Split equally across all {group.members.length} members.
          </p>
        </form>

        {/* ---- Members ---- */}
        <section className="card p-6">
          <h2 className="font-semibold">Members</h2>
          <div className="mt-3 divide-y divide-[rgb(var(--border))]">
            {group.members.map((m) => (
              <div key={m.userId} className="flex items-center justify-between gap-3 py-2.5">
                <span className="text-sm">{m.displayName}</span>
                <span className="truncate text-xs muted">{m.email}</span>
              </div>
            ))}
          </div>

          <form onSubmit={invite} className="mt-5">
            <label className="label" htmlFor="invite">
              Add someone by email
            </label>
            <div className="flex gap-2">
              <input
                id="invite"
                className="input"
                type="email"
                value={inviteEmail}
                placeholder="rohan@example.com"
                onChange={(e) => setInviteEmail(e.target.value)}
              />
              <button
                type="submit"
                className="btn-secondary shrink-0"
                disabled={busy || !inviteEmail.trim()}
              >
                <UserPlus className="h-4 w-4" />
              </button>
            </div>
          </form>
        </section>
      </div>

      {/* ---- History ---- */}
      <section className="card mt-4 p-6">
        <h2 className="font-semibold">History</h2>
        {expenses.length === 0 ? (
          <p className="mt-3 text-sm muted">No expenses yet.</p>
        ) : (
          <div className="mt-3 divide-y divide-[rgb(var(--border))]">
            {expenses.map((e) => (
              <div key={e.id} className="flex items-center justify-between gap-4 py-3">
                <span className="min-w-0">
                  <span className="flex items-center gap-2">
                    <span className="truncate text-sm font-medium">{e.description}</span>
                    {e.recurring && <span className="pill shrink-0">recurring</span>}
                  </span>
                  <span className="mt-0.5 block text-xs muted">
                    {e.paidByName} paid · split {e.splits.length} ways
                  </span>
                </span>
                <span className="shrink-0 text-sm tabular-nums">{e.formattedTotal}</span>
              </div>
            ))}
          </div>
        )}
      </section>
    </>
  )
}
