import { ArrowRight } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Reveal } from '../components/Reveal'

export function CTA() {
  return (
    <section className="relative py-section-sm sm:py-section">
      <div className="container-page">
        <Reveal>
          <div className="relative isolate overflow-hidden rounded-3xl border border-[rgb(var(--border))]
                          bg-[rgb(var(--surface))] px-6 py-16 text-center sm:px-16 sm:py-20">
            <div aria-hidden className="pointer-events-none absolute inset-0 -z-10">
              <div className="grid-lines absolute inset-0 opacity-30" />
              <div
                className="absolute left-1/2 top-0 h-[26rem] w-[46rem] -translate-x-1/2 animate-gradient-drift
                           rounded-full bg-[radial-gradient(closest-side,rgba(52,102,255,0.22),transparent)] blur-3xl"
              />
            </div>

            <h2 className="heading-lg mx-auto max-w-2xl text-balance">
              Settle the group chat tonight
            </h2>
            <p className="lede mx-auto mt-5 max-w-lg text-pretty">
              Free for up to three groups. No card, no trial countdown, no nagging.
            </p>

            <div className="mt-9 flex flex-col items-center justify-center gap-3 sm:flex-row">
              <Link to="/app?mode=register" className="btn-primary group w-full px-6 py-3 sm:w-auto">
                Create your first group
                <ArrowRight className="h-4 w-4 transition-transform duration-200 group-hover:translate-x-1" />
              </Link>
              <a href="#demo" className="btn-secondary w-full px-6 py-3 sm:w-auto">
                Try the demo first
              </a>
            </div>
          </div>
        </Reveal>
      </div>
    </section>
  )
}
