import { Reveal } from '../components/Reveal'

const STATS = [
  { value: 'n−1', label: 'Payments, at most', detail: 'for a group of n people' },
  { value: '0.00', label: 'Rounding drift', detail: 'balances always sum to zero' },
  { value: '200', label: 'Concurrent writes', detail: 'tested against one group' },
  { value: '3', label: 'Free groups', detail: 'forever, no card' },
]

export function Stats() {
  return (
    <section className="relative">
      <div className="container-page">
        <div className="rule" />
        <Reveal as="container" className="grid gap-8 py-14 sm:grid-cols-2 lg:grid-cols-4">
          {STATS.map((stat) => (
            <div key={stat.label} className="text-center">
              <p className="text-3xl font-semibold tracking-[-0.03em] tabular-nums sm:text-4xl">
                {stat.value}
              </p>
              <p className="mt-2 text-sm font-medium">{stat.label}</p>
              <p className="mt-0.5 text-xs muted">{stat.detail}</p>
            </div>
          ))}
        </Reveal>
        <div className="rule" />
      </div>
    </section>
  )
}
