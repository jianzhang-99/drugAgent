/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        // 主色：indigo 色系 - 专业、克制、可信
        primary: {
          50:  '#eef2ff',
          100: '#e0e7ff',
          200: '#c7d2fe',
          300: '#a5b4fc',
          400: '#818cf8',
          500: '#6366f1',
          600: '#4f46e5',  // 主色调
          700: '#4338ca',
          800: '#3730a3',
          900: '#312e81',
        },
        // 高中低风险色 - 统一风险表达
        risk: {
          high:   { bg: '#fef2f2', text: '#dc2626', border: '#fecaca' },
          medium: { bg: '#fffbeb', text: '#d97706', border: '#fde68a' },
          low:    { bg: '#ecfdf5', text: '#059669', border: '#a7f3d0' },
        },
        // 中性色板（医疗监管工作台用）
        slate: {
          50:  '#f8fafc',
          100: '#f1f5f9',
          200: '#e2e8f0',
          300: '#cbd5e1',
          400: '#94a3b8',
          500: '#64748b',
          600: '#475569',
          700: '#334155',
          800: '#1e293b',
          900: '#0f172a',
        }
      },
      borderRadius: {
        'xl':  '0.75rem',  // 12px
        '2xl': '1rem',     // 16px
        '3xl': '1.5rem',   // 24px
      },
      boxShadow: {
        'sm':  '0 1px 3px rgba(31, 42, 68, 0.06), 0 1px 2px rgba(31, 42, 68, 0.04)',
        'md':  '0 4px 12px rgba(31, 42, 68, 0.08), 0 2px 4px rgba(31, 42, 68, 0.04)',
        'lg':  '0 12px 36px rgba(31, 42, 68, 0.10), 0 4px 8px rgba(31, 42, 68, 0.06)',
        'xl':  '0 24px 64px rgba(31, 42, 68, 0.12), 0 8px 16px rgba(31, 42, 68, 0.08)',
        'card': '0 4px 12px rgba(31, 42, 68, 0.04)',
      },
      fontSize: {
        '2xs': ['0.625rem', { lineHeight: '0.875rem' }],
        'xs':  ['0.75rem',  { lineHeight: '1rem'   }],
        'sm':  ['0.875rem', { lineHeight: '1.25rem'}],
        'base':['1rem',     { lineHeight: '1.5rem'  }],
        'lg':  ['1.125rem', { lineHeight: '1.75rem' }],
        'xl':  ['1.25rem',  { lineHeight: '1.75rem' }],
        '2xl': ['1.5rem',   { lineHeight: '2rem'    }],
        '3xl': ['1.875rem', { lineHeight: '2.25rem' }],
      },
      animation: {
        'fade-in':   'fadeIn 0.3s ease-out',
        'slide-up':  'slideUp 0.3s ease-out',
        'pulse-soft': 'pulseSoft 2s ease-in-out infinite',
        'skeleton': 'skeleton 1.5s ease-in-out infinite',
      },
      keyframes: {
        fadeIn: {
          '0%': { opacity: '0', transform: 'translateY(8px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        slideUp: {
          '0%': { opacity: '0', transform: 'translateY(10px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        pulseSoft: {
          '0%, 100%': { opacity: '1' },
          '50%': { opacity: '0.7' },
        },
        skeleton: {
          '0%': { backgroundPosition: '200% 0' },
          '100%': { backgroundPosition: '-200% 0' },
        },
      },
    },
  },
  plugins: [
    require('@tailwindcss/forms'),
  ],
}
