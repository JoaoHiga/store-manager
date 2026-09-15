import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: './src/setupTests.js',
    globals: true,
    // Playwright owns `e2e/**` (its own test runner/syntax, Phase B of
    // automated-e2e-testing) — Vitest's default glob would otherwise also
    // pick those files up and fail on `test.describe`.
    exclude: ['**/node_modules/**', 'e2e/**'],
  },
})
