import { createContext, useContext, useEffect, useState } from 'react'

// ────────────────────────────────────────────────
// 型定義
// ────────────────────────────────────────────────
export type Theme = 'dark' | 'light'

type ThemeContextValue = {
  theme: Theme
  toggleTheme: () => void
  setTheme: (theme: Theme) => void
}

// ────────────────────────────────────────────────
// Context
// ────────────────────────────────────────────────
const ThemeContext = createContext<ThemeContextValue | null>(null)

// ────────────────────────────────────────────────
// Provider
// ────────────────────────────────────────────────
type ThemeProviderProps = {
  children: React.ReactNode
  defaultTheme?: Theme
}

export function ThemeProvider({ children, defaultTheme = 'dark' }: ThemeProviderProps) {
  const [theme, setTheme] = useState<Theme>(() => {
    // localStorage から復元、なければ defaultTheme
    if (typeof window !== 'undefined') {
      const stored = localStorage.getItem('theme')
      return stored === 'light' || stored === 'dark' ? stored : defaultTheme
    }
    return defaultTheme
  })

  // <html> の class と localStorage を同期
  useEffect(() => {
    const root = document.documentElement
    if (theme === 'dark') {
      root.classList.add('dark')
    } else {
      root.classList.remove('dark')
    }
    localStorage.setItem('theme', theme)
  }, [theme])

  const toggleTheme = () => setTheme(t => (t === 'dark' ? 'light' : 'dark'))

  return (
    <ThemeContext.Provider value={{ theme, toggleTheme, setTheme }}>
      {children}
    </ThemeContext.Provider>
  )
}

// ────────────────────────────────────────────────
// Hook
// ────────────────────────────────────────────────
export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext)
  if (!ctx) {
    throw new Error('useTheme は ThemeProvider の中で使用してください')
  }
  return ctx
}
