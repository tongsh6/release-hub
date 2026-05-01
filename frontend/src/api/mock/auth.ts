import type { LoginPayload, LoginResult } from '../auth'
import type { UserMeResult } from '../user'

const delay = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))

export const mockAuthApi = {
  async login(payload: LoginPayload): Promise<LoginResult> {
    await delay(500)
    if (payload.username === 'admin' && payload.password === 'admin') {
      return {
        token: 'mock-token-admin-' + Date.now()
      }
    }
    if (payload.username === 'user' && payload.password === '123456') {
      return {
        token: 'mock-token-user-' + Date.now()
      }
    }
    // Simulate 401
    const error: any = new Error('Incorrect username or password')
    error.response = { status: 401, data: { code: 'AUTH_FAILED', message: 'Incorrect username or password' } }
    throw error
  }
}

export const mockUserApi = {
  async me(): Promise<UserMeResult> {
    await delay(300)
    // Return different profile based on token? 
    // For simplicity, just return admin
    return {
      id: '1',
      username: 'admin',
      displayName: 'Administrator',
      permissions: [
        'iteration:read',
        'iteration:write',
        'run:read'
      ]
    }
  }
}
