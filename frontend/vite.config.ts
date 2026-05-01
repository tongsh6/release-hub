/// <reference types="vitest" />
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd())
  const proxyTarget = env.VITE_PROXY_TARGET
  
  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    server: {
      proxy: proxyTarget
        ? {
            '/api': {
              target: proxyTarget,
              changeOrigin: true
            }
          }
        : undefined
    },
    test: {
      environment: 'jsdom',
      globals: true,
      include: ['src/**/*.{test,spec}.ts', 'src/**/__tests__/**/*.ts'],
      exclude: ['e2e/**']
    }
  }
})
