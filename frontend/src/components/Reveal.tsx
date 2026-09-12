import { motion, useReducedMotion, type Variants } from 'framer-motion'
import type { ReactNode } from 'react'

/**
 * Shared easing. A single curve across the whole site is what makes motion feel
 * designed rather than assembled — mixing eases is the fastest way to look amateur.
 */
export const EASE = [0.22, 1, 0.36, 1] as const

export const fadeUp: Variants = {
  hidden: { opacity: 0, y: 24 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.6, ease: EASE } },
}

export const stagger: Variants = {
  hidden: {},
  visible: { transition: { staggerChildren: 0.08, delayChildren: 0.05 } },
}

interface RevealProps {
  children: ReactNode
  className?: string
  delay?: number
  /** Render as a stagger container so nested <RevealItem>s cascade. */
  as?: 'item' | 'container'
}

/**
 * Scroll-triggered reveal.
 *
 * `once: true` matters: re-animating every time an element scrolls back into view
 * feels clever for one pass and irritating forever after. The negative viewport
 * margin fires slightly before the element is fully visible, so it reads as already
 * in motion by the time you look at it.
 */
export function Reveal({ children, className, delay = 0, as = 'item' }: RevealProps) {
  const reduce = useReducedMotion()

  if (reduce) return <div className={className}>{children}</div>

  return (
    <motion.div
      className={className}
      initial="hidden"
      whileInView="visible"
      viewport={{ once: true, margin: '-80px' }}
      variants={as === 'container' ? stagger : fadeUp}
      transition={delay ? { delay } : undefined}
    >
      {children}
    </motion.div>
  )
}

/** A child of `<Reveal as="container">`, participating in its stagger. */
export function RevealItem({ children, className }: { children: ReactNode; className?: string }) {
  const reduce = useReducedMotion()
  if (reduce) return <div className={className}>{children}</div>
  return (
    <motion.div className={className} variants={fadeUp}>
      {children}
    </motion.div>
  )
}
