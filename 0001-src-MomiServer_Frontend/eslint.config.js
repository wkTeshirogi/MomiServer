import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import { defineConfig, globalIgnores } from 'eslint/config'

export default defineConfig([
  globalIgnores(['dist', 'output', 'test-results', 'playwright-report', '.playwright-cli', 'src/generated']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      globals: globals.browser,
    },
  },
  {
    // Existing context modules intentionally expose providers and their hooks together.
    // Vite falls back to module invalidation for these mixed-export modules.
    files: [
      'src/contexts/*.tsx',
      'src/components/ui/modal.tsx',
      'src/components/ui/sidebar.tsx',
      'src/pages/Servers/*/service.tsx',
    ],
    rules: { 'react-refresh/only-export-components': 'off' },
  },
])
