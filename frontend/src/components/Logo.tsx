export function Logo({ className = '' }: { className?: string }) {
  return (
    <span className={`inline-flex items-center gap-2.5 ${className}`}>
      <span
        aria-hidden
        className="grid h-8 w-8 place-items-center rounded-[10px] bg-gradient-to-br from-accent-400
                   to-accent-600 shadow-[0_4px_12px_rgba(52,102,255,0.35)]"
      >
        {/* Three bars collapsing to one: many debts reduced to a single payment.
            Bars rather than strokes so the mark stays legible down to 16px. */}
        <svg viewBox="0 0 24 24" className="h-[18px] w-[18px]" fill="currentColor">
          <g className="text-white">
            <rect x="5" y="5.6" width="14" height="2.8" rx="1.4" fill="#fff" />
            <rect x="5" y="10.6" width="9.5" height="2.8" rx="1.4" fill="#fff" fillOpacity="0.85" />
            <rect x="5" y="15.6" width="5" height="2.8" rx="1.4" fill="#fff" fillOpacity="0.7" />
          </g>
        </svg>
      </span>
      <span className="text-[15px] font-semibold tracking-[-0.01em]">SettleUp</span>
    </span>
  )
}
