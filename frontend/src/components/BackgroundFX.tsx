/**
 * Ambient background: drifting colour blooms over a faint grid.
 *
 * Deliberately pure CSS rather than JS-driven animation — these run on the
 * compositor, cost no main-thread time, and keep scrolling smooth even on a
 * mid-range phone. `pointer-events-none` keeps the whole layer inert.
 */
export function BackgroundFX({ variant = 'hero' }: { variant?: 'hero' | 'subtle' }) {
  return (
    <div aria-hidden className="pointer-events-none absolute inset-0 -z-10 overflow-hidden">
      <div className="grid-lines absolute inset-0 opacity-[0.35] [mask-image:radial-gradient(ellipse_at_top,black,transparent_70%)]" />

      <div
        className="absolute -top-40 left-1/2 h-[38rem] w-[70rem] -translate-x-1/2 animate-gradient-drift
                   rounded-full bg-[radial-gradient(closest-side,rgba(52,102,255,0.20),transparent)] blur-3xl"
      />

      {variant === 'hero' && (
        <>
          <div
            className="absolute -left-32 top-32 h-[26rem] w-[26rem] animate-gradient-drift rounded-full
                       bg-[radial-gradient(closest-side,rgba(139,92,246,0.16),transparent)] blur-3xl
                       [animation-delay:-6s]"
          />
          <div
            className="absolute -right-24 top-10 h-[24rem] w-[24rem] animate-gradient-drift rounded-full
                       bg-[radial-gradient(closest-side,rgba(16,185,129,0.14),transparent)] blur-3xl
                       [animation-delay:-12s]"
          />
        </>
      )}

      {/* Fades the effect into the page background so sections butt up cleanly. */}
      <div className="absolute inset-x-0 bottom-0 h-40 bg-gradient-to-b from-transparent to-[rgb(var(--bg))]" />
    </div>
  )
}
