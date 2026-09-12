import { Bike, Home, Plane, PartyPopper, Users, Utensils } from 'lucide-react'
import { Reveal, RevealItem } from '../components/Reveal'

const CASES = [
  { icon: Home, title: 'Flatmates', body: 'Rent, wifi, the gas cylinder nobody remembers paying for.' },
  { icon: Plane, title: 'Trips', body: 'Four people, eleven bookings, one settlement at the end.' },
  { icon: Utensils, title: 'Dinners', body: 'One card at the table, everyone squared up before dessert.' },
  { icon: Users, title: 'Couples', body: 'Shared costs without a spreadsheet or a conversation.' },
  { icon: PartyPopper, title: 'Events', body: 'Birthdays and weddings where twelve people chip in.' },
  { icon: Bike, title: 'Commutes', body: 'Daily cab pools that add up quietly over a month.' },
]

export function UseCases() {
  return (
    <section className="relative py-section-sm sm:py-section">
      <div className="container-page">
        <Reveal className="mx-auto max-w-2xl text-center">
          <p className="eyebrow">Use cases</p>
          <h2 className="heading-lg mt-3 text-balance">Wherever money gets shared</h2>
        </Reveal>

        <Reveal as="container" className="mt-12 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {CASES.map((item) => (
            <RevealItem key={item.title}>
              <div
                className="group flex h-full items-start gap-4 rounded-2xl border
                           border-[rgb(var(--border))] bg-[rgb(var(--surface))] p-5
                           transition-all duration-300 hover:-translate-y-0.5
                           hover:border-accent-400/40 hover:shadow-card-hover"
              >
                <span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl
                                 bg-[rgb(var(--surface-2))] text-accent-600
                                 transition-colors group-hover:bg-accent-500/10 dark:text-accent-300">
                  <item.icon className="h-4 w-4" />
                </span>
                <span>
                  <h3 className="text-sm font-semibold">{item.title}</h3>
                  <p className="mt-1 text-sm leading-relaxed muted">{item.body}</p>
                </span>
              </div>
            </RevealItem>
          ))}
        </Reveal>
      </div>
    </section>
  )
}
