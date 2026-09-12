import {
  Calculator,
  GitBranch,
  Lock,
  Receipt,
  RefreshCw,
  ShieldCheck,
} from 'lucide-react'
import { Reveal, RevealItem } from '../components/Reveal'

const FEATURES = [
  {
    icon: GitBranch,
    title: 'Fewest possible payments',
    body: 'Nets everyone out and rebuilds the smallest payment set from scratch. Fifteen IOUs become four transfers.',
  },
  {
    icon: Calculator,
    title: 'Correct to the paisa',
    body: 'Every amount is whole paise, never a float. ₹100 split three ways is 33.34 / 33.33 / 33.33 — and it still sums to ₹100.',
  },
  {
    icon: Lock,
    title: 'Safe when everyone types at once',
    body: 'Three flatmates adding expenses simultaneously cannot corrupt a balance. Writes are serialised per group.',
  },
  {
    icon: RefreshCw,
    title: 'Retry-proof',
    body: 'Tapped Add twice on patchy metro signal? The second request returns the first result instead of charging you again.',
  },
  {
    icon: Receipt,
    title: 'Rent on autopilot',
    body: 'Standing charges post themselves monthly. Runs twice, restarts mid-job — still charged exactly once.',
  },
  {
    icon: ShieldCheck,
    title: 'Every paisa auditable',
    body: 'An append-only ledger. Nothing is ever overwritten, so every balance traces back to what caused it.',
  },
]

export function Features() {
  return (
    <section id="features" className="relative scroll-mt-24 py-section-sm sm:py-section">
      <div className="container-page">
        <Reveal className="mx-auto max-w-2xl text-center">
          <p className="eyebrow">Built properly</p>
          <h2 className="heading-lg mt-3 text-balance">
            The boring guarantees nobody else bothers with
          </h2>
          <p className="lede mx-auto mt-4 max-w-xl text-pretty">
            Splitting a bill is easy. Keeping a shared ledger correct while four people edit it from
            three time zones is the actual problem.
          </p>
        </Reveal>

        <Reveal as="container" className="mt-14 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {FEATURES.map((feature) => (
            <RevealItem key={feature.title}>
              <article
                className="group card h-full p-6 transition-all duration-300
                           hover:-translate-y-1 hover:border-accent-400/40 hover:shadow-card-hover"
              >
                <span
                  className="inline-grid h-10 w-10 place-items-center rounded-xl border
                             border-accent-500/20 bg-accent-500/10 text-accent-600
                             transition-transform duration-300 group-hover:scale-105
                             dark:text-accent-300"
                >
                  <feature.icon className="h-5 w-5" />
                </span>
                <h3 className="mt-4 font-semibold tracking-[-0.01em]">{feature.title}</h3>
                <p className="mt-2 text-sm leading-relaxed muted">{feature.body}</p>
              </article>
            </RevealItem>
          ))}
        </Reveal>
      </div>
    </section>
  )
}
