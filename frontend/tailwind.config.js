/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // A single accent, used sparingly. Everything else stays neutral so the accent
        // always reads as "this is the action".
        accent: {
          50: '#eef4ff',
          100: '#d9e6ff',
          200: '#bcd3ff',
          300: '#8eb6ff',
          400: '#598eff',
          500: '#3466ff',
          600: '#1f44f5',
          700: '#1a33e1',
          800: '#1c2cb6',
          900: '#1c2b8f',
          950: '#151b57',
        },
      },
      fontFamily: {
        sans: [
          'Inter var',
          'Inter',
          'ui-sans-serif',
          'system-ui',
          '-apple-system',
          'Segoe UI',
          'sans-serif',
        ],
        mono: ['ui-monospace', 'SFMono-Regular', 'Menlo', 'monospace'],
      },
      // Named steps keep vertical rhythm decisions out of component code.
      spacing: {
        section: '7.5rem',
        'section-sm': '5rem',
      },
      borderRadius: {
        xl: '0.75rem',
        '2xl': '1rem',
        '3xl': '1.5rem',
      },
      boxShadow: {
        card: '0 1px 2px rgba(16,24,40,0.04), 0 4px 16px rgba(16,24,40,0.06)',
        'card-hover': '0 2px 4px rgba(16,24,40,0.05), 0 16px 40px rgba(16,24,40,0.12)',
        glow: '0 0 0 1px rgba(52,102,255,0.18), 0 8px 32px rgba(52,102,255,0.28)',
      },
      keyframes: {
        'gradient-drift': {
          '0%, 100%': { transform: 'translate3d(0,0,0) scale(1)' },
          '50%': { transform: 'translate3d(0,-3%,0) scale(1.08)' },
        },
        float: {
          '0%, 100%': { transform: 'translateY(0)' },
          '50%': { transform: 'translateY(-10px)' },
        },
        marquee: {
          from: { transform: 'translateX(0)' },
          to: { transform: 'translateX(-50%)' },
        },
      },
      animation: {
        'gradient-drift': 'gradient-drift 18s ease-in-out infinite',
        float: 'float 6s ease-in-out infinite',
        marquee: 'marquee 40s linear infinite',
      },
    },
  },
  plugins: [],
}
