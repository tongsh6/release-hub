/**
 * Pact Consumer Test — Groups Tree API
 * Defines the contract for GET /api/v1/groups/tree
 */
import { PactV3, MatchersV3 } from '@pact-foundation/pact'

const { string, eachLike } = MatchersV3

const provider = new PactV3({
  consumer: 'releasehub-frontend',
  provider: 'releasehub-backend',
  dir: '../frontend/pacts',
})

describe('Groups Tree API', () => {
  it('returns a tree of group nodes', () => {
    provider
      .given('groups exist with tree structure')
      .uponReceiving('a request to get the group tree')
      .withRequest({
        method: 'GET',
        path: '/api/v1/groups/tree',
        headers: { Accept: 'application/json' },
      })
      .willRespondWith({
        status: 200,
        headers: { 'Content-Type': 'application/json' },
        body: {
          code: string('OK'),
          message: string('OK'),
          data: eachLike({
            id: string('g1'),
            name: string('Root Group'),
            code: string('root'),
            parentCode: string(null),
            createdAt: string('2026-01-01T00:00:00Z'),
            updatedAt: string('2026-01-01T00:00:00Z'),
            children: [],
          }),
        },
      })

    return provider.executeTest(async (mockServer) => {
      const axios = (await import('axios')).default
      const res = await axios.get(`${mockServer.url}/api/v1/groups/tree`, {
        headers: { Accept: 'application/json' },
      })
      expect(res.status).toBe(200)
      expect(res.data.data).toBeInstanceOf(Array)
    })
  })
})
