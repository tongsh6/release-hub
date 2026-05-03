/**
 * Pact Consumer Test — Release Window List API
 * Defines the contract for GET /api/v1/release-windows
 * (verifies ReleaseWindowView shape; GET by {id} shares the same DTO)
 */
import { PactV3, MatchersV3 } from '@pact-foundation/pact'

const { string, boolean, eachLike } = MatchersV3

const provider = new PactV3({
  consumer: 'releasehub-frontend',
  provider: 'releasehub-backend',
  dir: '../frontend/pacts',
})

describe('Release Window List API', () => {
  it('returns a list of release windows', () => {
    provider
      .given('a release window exists')
      .uponReceiving('a request to list all release windows')
      .withRequest({
        method: 'GET',
        path: '/api/v1/release-windows',
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
          }),
        },
      })

    return provider.executeTest(async (mockServer) => {
      const axios = (await import('axios')).default
      const res = await axios.get(`${mockServer.url}/api/v1/release-windows`, {
        headers: { Accept: 'application/json' },
      })
      expect(res.status).toBe(200)
      expect(res.data.data).toBeInstanceOf(Array)
    })
  })
})
