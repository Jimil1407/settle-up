import { Check, Sparkles } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { Reveal, RevealItem, EASE } from '../components/Reveal'

interface Plan {
  name: string
  blurb: string
  monthly: number
  yearly: number
  features: string[]
  cta: string
  featured?: boolean
}

const PLANS: Plan[] = [
  {
    name: 'Free',
    blurb: 'For a flat or a trip.',
    monthly: 0,
    yearly: 0,
    features: [
      'Up to 3 groups',
      'Unlimited expenses',
      'Debt simplification',
      'Equal and exact splits',
      'CSV export',
    ],
    cta: 'Start free',
  },
  {
    name: 'Plus',
    blurb: 'For people who share a lot.',
    monthly: 149,
    yearly: 119,
    features: [
      'Unlimited groups',
      'Recurring expenses',
      'Weighted splits',
      'Receipt attachments',
      'Payment reminders',
      'Priority support',
    ],
    cta: 'Start 14-day trial',
    featured: true,
  },
  {
    name: 'Teams',
    blurb: 'For offices and communities.',
    monthly: 399,
    yearly: 319,
    features: [
      'Everything in Plus',
      'Shared org workspace',
      'Roles and permissions',
      'Audit log export',
      'API access',
      'SSO',
    ],
    cta: 'Talk to us',
  },
]

export function Pricing() {
  const [yearly, setYearly] = useState(true)

  return (
    <section id="pricing" className="relative scroll-mt-24 py-section-sm sm:py-section">
      <div className="container-page">
        <Reveal className="mx-auto max-w-2xl text-center">
          <p className="eyebrow">Pricing</p>
          <h2 className="heading-lg mt-3 text-balance">Free for most people, honestly</h2>
          <p className="lede mx-auto mt-4 max-w-xl text-pretty">
            Share a flat with two friends and you will never need to pay us.
          </p>

          <div className="mt-8 inline-flex items-center gap-3">
            <span className={`text-sm ${!yearly ? 'font-medium' : 'muted'}`}>Monthly</span>
            <button
              type="button"
              role="switch"
              aria-checked={yearly}
              aria-label="Toggle yearly billing"
              onClick={() => setYearly((v) => !v)}
              className={`relative h-6 w-11 rounded-full transition-colors duration-300 ${
                yearly ? 'bg-accent-600' : 'bg-[rgb(var(--border))]'
              }`}
            >
              <motion.span
                layout
                transition={{ type: 'spring', stiffness: 500, damping: 32 }}
                className={`absolute top-0.5 h-5 w-5 rounded-full bg-white shadow-sm ${
                  yearly ? 'left-[1.375rem]' : 'left-0.5'
                }`}
              />
            </button>
            <span className={`text-sm ${yearly ? 'font-medium' : 'muted'}`}>
              Yearly
              <span className="ml-2 rounded-full bg-emerald-500/15 px-2 py-0.5 text-xs font-medium text-emerald-600 dark:text-emerald-400">
                Save 20%
              </span>
            </span>
          </div>
        </Reveal>

        <Reveal as="container" className="mt-12 grid items-start gap-5 lg:grid-cols-3">
          {PLANS.map((plan) => {
            const price = yearly ? plan.yearly : plan.monthly
            return (
              <RevealItem key={plan.name}>
                <div
                  className={`relative flex h-full flex-col rounded-3xl border p-7 transition-all duration-300
                              hover:-translate-y-1 hover:shadow-card-hover ${
                                plan.featured
                                  ? 'border-accent-500/40 bg-[rgb(var(--surface))] shadow-glow lg:scale-[1.03]'
                                  : 'border-[rgb(var(--border))] bg-[rgb(var(--surface))] shadow-card hover:border-accent-400/40'
                              }`}
                >
                  {plan.featured && (
                    <span className="absolute -top-3 left-1/2 -translate-x-1/2 rounded-full
                                     bg-accent-600 px-3 py-1 text-xs font-medium text-white shadow-glow">
                      <Sparkles className="mr-1 inline h-3 w-3" />
                      Most popular
                    </span>
                  )}

                  <h3 className="font-semibold">{plan.name}</h3>
                  <p className="mt-1 text-sm muted">{plan.blurb}</p>

                  <div className="mt-6 flex items-baseline gap-1">
                    <motion.span
                      key={`${plan.name}-${price}`}
                      initial={{ opacity: 0, y: -6 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ duration: 0.25, ease: EASE }}
                      className="text-4xl font-semibold tracking-[-0.03em] tabular-nums"
                    >
                      ₹{price}
                    </motion.span>
                    <span className="text-sm muted">/month</span>
                  </div>
                  {plan.monthly > 0 && (
                    <p className="mt-1 text-xs muted">
                      {yearly ? 'billed annually' : 'billed monthly'}
                    </p>
                  )}

                  <Link
                    to={plan.name === 'Teams' ? '/app?mode=register' : '/app?mode=register'}
                    className={`mt-6 w-full ${plan.featured ? 'btn-primary' : 'btn-secondary'}`}
                  >
                    {plan.cta}
                  </Link>

                  <ul className="mt-7 space-y-3">
                    {plan.features.map((feature) => (
                      <li key={feature} className="flex items-start gap-2.5 text-sm">
                        <Check className="mt-0.5 h-4 w-4 shrink-0 text-accent-500" />
                        <span className="muted">{feature}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              </RevealItem>
            )
          })}
        </Reveal>
      </div>
    </section>
  )
}
