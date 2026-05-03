/**
 * Pact Consumer Test — Release Window Paged List API
 * Defines the contract for GET /api/v1/release-windows/paged
 */
import { PactV3, MatchersV3 } from '@pact-foundation/pact'

const { string, boolean, integer, eachLike } = MatchersV3

const provider = new PactV3({
  consumer: 'releasehub-frontend',
  provider: 'releasehub-backend',
  dir: '../frontend/pacts',
})

describe('Release Window Paged API', () => {
  it('returns a paged list of release windows', () => {
    provider
      .given('release windows exist')
      .uponReceiving('a request to list release windows with pagination')
      .withRequest({
        method: 'GET',
        path: '/api/v1/release-windows/paged',
        query: { page: '1', size: '20' },
        headers: { Accept: 'application/json' },
      })
      .willRespondWith({
        status: 200,
        headers: { 'Content-Type': 'application/json' },
        body: {
          code: string('OK'),
          message: string('OK'),
          data: eachLike({
            id: string('rw-001'),
            windowKey: string('RW-2026-Q1'),
            name: string('Q1 2026 Release'),
            description: string('Q1 release window'),
            plannedReleaseAt: string('2026-03-31T00:00:00Z'),
            groupCode: string('root'),
            status: string('DRAFT'),
            createdAt: string('2026-01-01T00:00:00Z'),
            updatedAt: string('2026-01-01T00:00:00Z'),
            frozen: boolean(false),
            publishedAt: string(null),
          }),
          page: {
            page: integer(1),
            size: integer(20),
            total: integer(1),
            totalPages: integer(1),
            hasNext: boolean(false),
            hasPrevious: boolean(false),
          },
        },
      })

    return provider.executeTest(async (mockServer) => {
      const axios = (await import('axios')).default
      const res = await axios.get(`${mockServer.url}/api/v1/release-windows/paged`, {
        params: { page: 1, size: 20 },
        headers: { Accept: 'application/json' },
      })
      expect(res.status).toBe(200)
      expect(res.data.data).toBeInstanceOf(Array)
      expect(res.data.page).toBeTruthy()
      expect(res.data.page.total).toBeGreaterThanOrEqual(0)
    })
  })
})
