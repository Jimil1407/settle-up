/**
 * The same greedy debt simplification the backend runs, ported for the live demo.
 *
 * Keeping it identical in behaviour matters: the landing page claims a specific
 * result ("collapses to at most n−1 payments"), so the demo has to actually
 * produce that rather than approximate it.
 */

export interface Transfer {
  from: string
  to: string
  amountPaise: number
}

/** Largest-remainder split, so shares always sum back to the exact total. */
export function splitEvenly(totalPaise: number, people: string[]): Record<string, number> {
  const ordered = [...people].sort()
  const n = ordered.length
  const remainder = ((totalPaise % n) + n) % n
  const base = (totalPaise - remainder) / n

  const shares: Record<string, number> = {}
  ordered.forEach((person, i) => {
    shares[person] = base + (i < remainder ? 1 : 0)
  })
  return shares
}

/**
 * Match the largest creditor against the largest debtor until everyone is square.
 * Each transfer zeroes out at least one person, so this emits at most n−1 payments.
 */
export function simplify(balances: Record<string, number>): Transfer[] {
  const creditors = Object.entries(balances)
    .filter(([, v]) => v > 0)
    .map(([name, amount]) => ({ name, amount }))
  const debtors = Object.entries(balances)
    .filter(([, v]) => v < 0)
    .map(([name, amount]) => ({ name, amount: -amount }))

  const byLargest = (a: { amount: number; name: string }, b: { amount: number; name: string }) =>
    b.amount - a.amount || a.name.localeCompare(b.name)

  creditors.sort(byLargest)
  debtors.sort(byLargest)

  const transfers: Transfer[] = []
  let ci = 0
  let di = 0

  while (ci < creditors.length && di < debtors.length) {
    const creditor = creditors[ci]
    const debtor = debtors[di]
    const amount = Math.min(creditor.amount, debtor.amount)

    transfers.push({ from: debtor.name, to: creditor.name, amountPaise: amount })

    creditor.amount -= amount
    debtor.amount -= amount
    if (creditor.amount === 0) ci++
    if (debtor.amount === 0) di++
  }

  return transfers
}

export function formatPaise(paise: number): string {
  const sign = paise < 0 ? '−' : ''
  const abs = Math.abs(paise)
  const rupees = Math.floor(abs / 100)
  const paisa = String(abs % 100).padStart(2, '0')
  return `${sign}₹${rupees.toLocaleString('en-IN')}.${paisa}`
}

export function rupeesToPaise(value: string): number {
  const parsed = Number.parseFloat(value)
  if (!Number.isFinite(parsed)) return 0
  // Round rather than truncate, so float representation error cannot lose a paisa.
  return Math.round(parsed * 100)
}
