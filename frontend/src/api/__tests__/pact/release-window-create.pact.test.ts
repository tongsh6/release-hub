/**
 * Pact Consumer Test — Release Window Create API
 * Defines the contract for POST /api/v1/release-windows
 */
import { PactV3, MatchersV3 } from '@pact-foundation/pact'

const { string, boolean } = MatchersV3

const provider = new PactV3({
  consumer: 'releasehub-frontend',
  provider: 'releasehub-backend',
  dir: '../frontend/pacts',
})

describe('Release Window Create API', () => {
  it('creates a release window and returns the created view', () => {
    provider
      .given('a group exists for release window creation')
      .uponReceiving('a request to create a release window')
      .withRequest({
        method: 'POST',
        path: '/api/v1/release-windows',
        headers: { 'Content-Type': 'application/json' },
        body: {
          name: 'Q2 2026 Release',
          description: 'Second quarter release',
          plannedReleaseAt: '2026-06-30T00:00:00Z',
          groupCode: 'root',
        },
      })
      .willRespondWith({
        status: 200,
        headers: { 'Content-Type': 'application/json' },
        body: {
          code: string('OK'),
          message: string('OK'),
          data: {
            id: string('rw-new'),
            windowKey: string('RW-2026-Q2'),
            name: string('Q2 2026 Release'),
            description: string('Second quarter release'),
            plannedReleaseAt: string('2026-06-30T00:00:00Z'),
            groupCode: string('root'),
            status: string('DRAFT'),
            createdAt: string('2026-06-30T00:00:00Z'),
            updatedAt: string('2026-06-30T00:00:00Z'),
            frozen: boolean(false),
            publishedAt: string(null),
          },
        },
      })

    return provider.executeTest(async (mockServer) => {
      const axios = (await import('axios')).default
      const res = await axios.post(`${mockServer.url}/api/v1/release-windows`, {
        name: 'Q2 2026 Release',
        description: 'Second quarter release',
        plannedReleaseAt: '2026-06-30T00:00:00Z',
        groupCode: 'root',
      })
      expect(res.status).toBe(200)
      expect(res.data.data.id).toBeTruthy()
      expect(res.data.data.status).toBe('DRAFT')
    })
  })
})
