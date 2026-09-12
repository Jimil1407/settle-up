import { Reveal, RevealItem } from '../components/Reveal'

const TESTIMONIALS = [
  {
    quote:
      'We used to have a 40-message thread every month arguing about rent and the electricity bill. Now it is two taps and nobody talks about money.',
    name: 'Ananya Iyer',
    role: 'Shares a flat in Bengaluru',
    initials: 'AI',
  },
  {
    quote:
      'Eleven bookings across four people on a Goa trip. It told us three payments and we were done before the flight home.',
    name: 'Karthik Menon',
    role: 'Organises too many trips',
    initials: 'KM',
  },
  {
    quote:
      'The thing that sold me: I added an expense while my flatmate was mid-payment and it caught it instead of silently taking the wrong amount.',
    name: 'Priya Nair',
    role: 'Product designer',
    initials: 'PN',
  },
  {
    quote:
      'Every other app I tried rounded badly and left a rupee floating around. This one just balances.',
    name: 'Dev Sharma',
    role: 'Engineer, ex-spreadsheet user',
    initials: 'DS',
  },
  {
    quote:
      'Rent posts itself on the first. I have not thought about it once since setting it up.',
    name: 'Meera Raghavan',
    role: 'Shares a place in Pune',
    initials: 'MR',
  },
  {
    quote:
      'The CSV export ended a six-month argument in about four minutes.',
    name: 'Rahul Bose',
    role: 'Group treasurer by default',
    initials: 'RB',
  },
]

export function Testimonials() {
  return (
    <section className="relative py-section-sm sm:py-section">
      <div className="container-page">
        <Reveal className="mx-auto max-w-2xl text-center">
          <p className="eyebrow">Loved by messy group chats</p>
          <h2 className="heading-lg mt-3 text-balance">People stopped arguing about money</h2>
        </Reveal>

        <Reveal
          as="container"
          className="mt-12 gap-4 space-y-4 sm:columns-2 sm:space-y-0 lg:columns-3"
        >
          {TESTIMONIALS.map((t) => (
            <RevealItem key={t.name} className="mb-4 break-inside-avoid">
              <figure
                className="card p-6 transition-all duration-300 hover:-translate-y-1
                           hover:border-accent-400/40 hover:shadow-card-hover"
              >
                <blockquote className="text-sm leading-relaxed text-pretty">"{t.quote}"</blockquote>
                <figcaption className="mt-5 flex items-center gap-3">
                  <span
                    className="grid h-9 w-9 place-items-center rounded-full bg-gradient-to-br
                               from-accent-400/20 to-accent-600/20 text-[11px] font-semibold
                               text-accent-600 dark:text-accent-300"
                    aria-hidden
                  >
                    {t.initials}
                  </span>
                  <span>
                    <span className="block text-sm font-medium">{t.name}</span>
                    <span className="block text-xs muted">{t.role}</span>
                  </span>
                </figcaption>
              </figure>
            </RevealItem>
          ))}
        </Reveal>
      </div>
    </section>
  )
}
