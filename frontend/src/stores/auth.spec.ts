import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from './auth'
import { getRefreshToken, getToken, setRefreshToken, setToken } from '../api/request'

const user = { id: 1, username: 'alice', nickname: '爱丽丝', roles: ['ROLE_USER'], createdAt: '2026-09-17 10:00:00' }

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

beforeEach(() => setActivePinia(createPinia()))

afterEach(() => {
  vi.unstubAllGlobals()
  setToken(null)
  setRefreshToken(null)
})

describe('auth store', () => {
  it('keeps the tokens and user after login', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      jsonResponse({ code: 0, message: 'OK', data: { accessToken: 'token-1', refreshToken: 'refresh-1', expiresIn: 1800, user } }),
    ))

    const auth = useAuthStore()
    await auth.login({ username: 'alice', password: 'Secret123!', captchaKey: 'k', captchaCode: '7' })

    expect(auth.isAuthenticated).toBe(true)
    expect(auth.user?.nickname).toBe('爱丽丝')
    expect(getToken()).toBe('token-1')
    expect(getRefreshToken()).toBe('refresh-1')
  })

  it('clears everything on logout', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ code: 0, message: 'OK', data: null })))
    const auth = useAuthStore()
    setToken('token-1')
    setRefreshToken('refresh-1')

    auth.logout()

    expect(auth.isAuthenticated).toBe(false)
    expect(getToken()).toBeNull()
    expect(getRefreshToken()).toBeNull()
  })

  it('restores the session from a stored token', async () => {
    setToken('token-1')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ code: 0, message: 'OK', data: user })))

    const auth = useAuthStore()
    await auth.restoreSession()

    expect(auth.user?.username).toBe('alice')
  })

  it('drops a stored token that the server rejects', async () => {
    setToken('stale-token')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      jsonResponse({ code: 401, message: '未登录或登录已过期', data: null }, 401),
    ))

    const auth = useAuthStore()
    await auth.restoreSession()

    expect(auth.isAuthenticated).toBe(false)
    expect(getToken()).toBeNull()
  })

  it('renews the session with a refresh token when access token expired', async () => {
    setRefreshToken('refresh-1')
    let meCalls = 0
    vi.stubGlobal('fetch', vi.fn((url: string) => {
      if (url === '/api/auth/refresh') {
        return Promise.resolve(jsonResponse({
          code: 0,
          message: 'OK',
          data: { accessToken: 'new-token', refreshToken: 'new-refresh', expiresIn: 1800, user },
        }))
      }
      if (url === '/api/auth/me') {
        meCalls += 1
        return meCalls === 1
          ? Promise.resolve(jsonResponse({ code: 401, message: '未登录', data: null }, 401))
          : Promise.resolve(jsonResponse({ code: 0, message: 'OK', data: user }))
      }
      return Promise.resolve(jsonResponse({ code: 0, message: 'OK', data: null }))
    }))

    const auth = useAuthStore()
    await auth.restoreSession()

    expect(auth.user?.username).toBe('alice')
    expect(getToken()).toBe('new-token')
  })
})
