import { AnimatePresence, motion } from 'framer-motion'
import { Plus } from 'lucide-react'
import { useState } from 'react'
import { Reveal, EASE } from '../components/Reveal'

const FAQS = [
  {
    q: 'How is this different from splitting the bill in a notes app?',
    a: 'A notes app records what happened. SettleUp works out what should happen next — it nets everyone out and tells you the fewest payments that square the group. It also refuses to let rounding quietly lose money, which spreadsheets happily do.',
  },
  {
    q: 'What happens if two of us add an expense at the same time?',
    a: 'Nothing bad. Writes to a group are serialised, so neither can overwrite the other. If you were partway through settling up when someone changed the totals, we reject that payment and refresh your plan rather than applying an amount that is no longer correct.',
  },
  {
    q: 'Does it handle amounts that do not divide evenly?',
    a: 'Yes, and this is where most apps quietly fail. ₹100 across three people becomes 33.34 / 33.33 / 33.33 — the leftover paisa is assigned deliberately, so the shares always add back to exactly ₹100.',
  },
  {
    q: 'Can I split unevenly?',
    a: 'Three ways: equally, by weight (a couple counting as two shares), or by exact amounts you type yourself. The only rule on exact splits is that they add up to the total.',
  },
  {
    q: 'Does it move real money?',
    a: 'Not yet. SettleUp tells you exactly who should pay whom and how much, and records it when you confirm. You settle through UPI or whatever you already use.',
  },
  {
    q: 'What if I leave a group?',
    a: 'Your balance stays on the ledger until it is settled, so leaving cannot make a debt disappear. Historic expenses stay intact and auditable.',
  },
  {
    q: 'Can I get my data out?',
    a: 'Any group exports to CSV with one click — every expense, who paid, and each person\u2019s share. No lock-in.',
  },
  {
    q: 'Is it free?',
    a: 'For up to three groups, permanently. Most flats and trips never outgrow that.',
  },
]

export function FAQ() {
  const [open, setOpen] = useState<number | null>(0)

  return (
    <section id="faq" className="relative scroll-mt-24 py-section-sm sm:py-section">
      <div className="container-page">
        <div className="grid gap-12 lg:grid-cols-[0.8fr_1.2fr] lg:gap-16">
          <Reveal>
            <p className="eyebrow">FAQ</p>
            <h2 className="heading-lg mt-3 text-balance">Questions worth asking</h2>
            <p className="lede mt-4 text-pretty">
              Still unsure about something?{' '}
              <a
                href="mailto:hello@settleup.app"
                className="font-medium text-accent-600 underline-offset-4 hover:underline dark:text-accent-400"
              >
                Ask us directly
              </a>
              .
            </p>
          </Reveal>

          <Reveal>
            <dl className="divide-y divide-[rgb(var(--border))] border-y border-[rgb(var(--border))]">
              {FAQS.map((faq, i) => {
                const isOpen = open === i
                return (
                  <div key={faq.q}>
                    <dt>
                      <button
                        type="button"
                        onClick={() => setOpen(isOpen ? null : i)}
                        aria-expanded={isOpen}
                        className="flex w-full items-start justify-between gap-6 py-5 text-left
                                   transition-colors hover:text-accent-600 dark:hover:text-accent-400"
                      >
                        <span className="text-sm font-medium sm:text-base">{faq.q}</span>
                        <motion.span
                          animate={{ rotate: isOpen ? 45 : 0 }}
                          transition={{ duration: 0.25, ease: EASE }}
                          className="mt-0.5 shrink-0 text-[rgb(var(--text-muted))]"
                        >
                          <Plus className="h-4 w-4" />
                        </motion.span>
                      </button>
                    </dt>
                    <AnimatePresence initial={false}>
                      {isOpen && (
                        <motion.dd
                          initial={{ height: 0, opacity: 0 }}
                          animate={{ height: 'auto', opacity: 1 }}
                          exit={{ height: 0, opacity: 0 }}
                          transition={{ duration: 0.3, ease: EASE }}
                          className="overflow-hidden"
                        >
                          <p className="pb-5 pr-10 text-sm leading-relaxed text-pretty muted">
                            {faq.a}
                          </p>
                        </motion.dd>
                      )}
                    </AnimatePresence>
                  </div>
                )
              })}
            </dl>
          </Reveal>
        </div>
      </div>
    </section>
  )
}
