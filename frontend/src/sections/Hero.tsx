import { motion } from 'framer-motion'
import { ArrowRight, Check, Sparkles } from 'lucide-react'
import { Link } from 'react-router-dom'
import { BackgroundFX } from '../components/BackgroundFX'
import { EASE } from '../components/Reveal'
import { AppPreview } from '../components/AppPreview'

const PROOF = ['No card required', 'Free for small groups', 'Export anytime']

export function Hero() {
  return (
    <section className="relative isolate overflow-hidden pb-16 pt-32 sm:pb-24 sm:pt-40">
      <BackgroundFX variant="hero" />

      <div className="container-page">
        <motion.div
          initial="hidden"
          animate="visible"
          variants={{ visible: { transition: { staggerChildren: 0.09 } } }}
          className="mx-auto max-w-3xl text-center"
        >
          <motion.div
            variants={{ hidden: { opacity: 0, y: 16 }, visible: { opacity: 1, y: 0 } }}
            transition={{ duration: 0.6, ease: EASE }}
          >
            <span className="glass inline-flex items-center gap-2 rounded-full px-3.5 py-1.5 text-xs font-medium">
              <Sparkles className="h-3.5 w-3.5 text-accent-500" />
              Settle a whole trip in one tap
            </span>
          </motion.div>

          <motion.h1
            variants={{ hidden: { opacity: 0, y: 20 }, visible: { opacity: 1, y: 0 } }}
            transition={{ duration: 0.7, ease: EASE }}
            className="heading-xl mt-6 text-balance"
          >
            Shared expenses that
            <br className="hidden sm:block" />{' '}
            <span className="bg-gradient-to-br from-accent-400 via-accent-500 to-violet-500 bg-clip-text text-transparent">
              actually add up
            </span>
          </motion.h1>

          <motion.p
            variants={{ hidden: { opacity: 0, y: 20 }, visible: { opacity: 1, y: 0 } }}
            transition={{ duration: 0.7, ease: EASE }}
            className="lede mx-auto mt-6 max-w-xl text-pretty"
          >
            Six people paid for different things and nobody knows who owes whom. SettleUp works out
            everyone's net position and collapses the mess into the fewest possible payments — down
            to the last paisa.
          </motion.p>

          <motion.div
            variants={{ hidden: { opacity: 0, y: 20 }, visible: { opacity: 1, y: 0 } }}
            transition={{ duration: 0.7, ease: EASE }}
            className="mt-9 flex flex-col items-center justify-center gap-3 sm:flex-row"
          >
            <Link to="/app?mode=register" className="btn-primary group w-full px-6 py-3 sm:w-auto">
              Start for free
              <ArrowRight className="h-4 w-4 transition-transform duration-200 group-hover:translate-x-1" />
            </Link>
            <a href="#demo" className="btn-secondary w-full px-6 py-3 sm:w-auto">
              See how it works
            </a>
          </motion.div>

          <motion.ul
            variants={{ hidden: { opacity: 0 }, visible: { opacity: 1 } }}
            transition={{ duration: 0.8, ease: EASE, delay: 0.2 }}
            className="mt-8 flex flex-wrap items-center justify-center gap-x-6 gap-y-2"
          >
            {PROOF.map((item) => (
              <li key={item} className="flex items-center gap-1.5 text-sm muted">
                <Check className="h-4 w-4 text-emerald-500" />
                {item}
              </li>
            ))}
          </motion.ul>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 48, scale: 0.97 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          transition={{ duration: 0.9, ease: EASE, delay: 0.25 }}
          className="relative mx-auto mt-16 max-w-5xl sm:mt-20"
        >
          {/* Glow pooled under the mockup so it sits on the page rather than floating on it. */}
          <div
            aria-hidden
            className="absolute -inset-x-8 -top-6 bottom-0 -z-10 rounded-[2rem]
                       bg-[radial-gradient(60%_50%_at_50%_0%,rgba(52,102,255,0.28),transparent)] blur-2xl"
          />
          <AppPreview />
        </motion.div>
      </div>
    </section>
  )
}
