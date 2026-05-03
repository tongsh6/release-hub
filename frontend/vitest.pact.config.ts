import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: {
    environment: 'node',
    globals: true,
    include: [
      'src/**/*.pact.{test,spec}.ts',
      'tests/pact/**/*.{test,spec}.ts',
    ],
    testTimeout: 30_000,
    fileParallelism: false,
    pool: 'forks',
    server: {
      deps: {
        external: [
          '@pact-foundation/pact',
          '@pact-foundation/pact-core',
        ],
      },
    },
    deps: {
      optimizer: {
        ssr: {
          exclude: [
            '@pact-foundation/pact',
            '@pact-foundation/pact-core',
          ],
        },
      },
    },
  },
})
