/**
 * Pact Consumer Test — Auth API
 * Defines the contract that releasehub-frontend expects from releasehub-backend.
 */
import { PactV3, MatchersV3 } from '@pact-foundation/pact'

const { like, string } = MatchersV3

const provider = new PactV3({
  consumer: 'releasehub-frontend',
  provider: 'releasehub-backend',
  dir: '../frontend/pacts',
})

describe('Auth API', () => {
  it('returns a JWT token on valid login', () => {
    provider
      .given('admin user exists')
      .uponReceiving('a login request with valid credentials')
      .withRequest({
        method: 'POST',
        path: '/api/v1/auth/login',
        headers: { 'Content-Type': 'application/json' },
        body: { username: 'admin', password: 'admin' },
      })
      .willRespondWith({
        status: 200,
        headers: { 'Content-Type': 'application/json' },
        body: {
          code: string('0'),
          message: string('success'),
          data: {
            token: string('eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.signature'),
          },
        },
      })

    return provider.executeTest(async (mockServer) => {
      const axios = (await import('axios')).default
      const res = await axios.post(`${mockServer.url}/api/v1/auth/login`, {
        username: 'admin',
        password: 'admin',
      })
      expect(res.status).toBe(200)
      expect(res.data.data.token).toBeTruthy()
    })
  })

  it('returns 401 on invalid credentials', () => {
    provider
      .given('admin user exists')
      .uponReceiving('a login request with wrong password')
      .withRequest({
        method: 'POST',
        path: '/api/v1/auth/login',
        headers: { 'Content-Type': 'application/json' },
        body: { username: 'admin', password: 'wrong' },
      })
      .willRespondWith({
        status: 401,
        headers: { 'Content-Type': 'application/json' },
        body: {
          code: string('AUTH_FAILED'),
          message: string('auth failed'),
          data: null,
        },
      })

    return provider.executeTest(async (mockServer) => {
      const axios = (await import('axios')).default
      try {
        await axios.post(`${mockServer.url}/api/v1/auth/login`, {
          username: 'admin',
          password: 'wrong',
        })
      } catch (e: any) {
        expect(e.response.status).toBe(401)
      }
    })
  })
})
