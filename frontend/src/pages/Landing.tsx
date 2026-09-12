import { Navbar } from '../components/Navbar'
import { Benefits } from '../sections/Benefits'
import { CTA } from '../sections/CTA'
import { FAQ } from '../sections/FAQ'
import { Features } from '../sections/Features'
import { Footer } from '../sections/Footer'
import { Hero } from '../sections/Hero'
import { InteractiveDemo } from '../sections/InteractiveDemo'
import { Pricing } from '../sections/Pricing'
import { Stats } from '../sections/Stats'
import { Testimonials } from '../sections/Testimonials'
import { UseCases } from '../sections/UseCases'

export function Landing({ authed }: { authed: boolean }) {
  return (
    <div className="relative min-h-screen overflow-x-clip">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-[60]
                   focus:rounded-lg focus:bg-accent-600 focus:px-4 focus:py-2 focus:text-sm focus:text-white"
      >
        Skip to content
      </a>

      <Navbar authed={authed} />

      <main id="main">
        <Hero />
        <Stats />
        <Features />
        <InteractiveDemo />
        <Benefits />
        <UseCases />
        <Testimonials />
        <Pricing />
        <FAQ />
        <CTA />
      </main>

      <Footer />
    </div>
  )
}
