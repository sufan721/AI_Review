import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, getToken, request, setToken, setUnauthorizedHandler } from './request'

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

afterEach(() => {
  vi.unstubAllGlobals()
  setToken(null)
  setUnauthorizedHandler(() => {})
})

describe('request', () => {
  it('unwraps a successful Result response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ code: 0, message: 'OK', data: { status: 'UP' } })))

    await expect(request<{ status: string }>('/api/health')).resolves.toEqual({ status: 'UP' })
  })

  it('attaches the bearer token when one is stored', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ code: 0, message: 'OK', data: null }))
    vi.stubGlobal('fetch', fetchMock)
    setToken('token-1')

    await request('/api/auth/me')

    const headers = fetchMock.mock.calls[0][1].headers as Record<string, string>
    expect(headers.Authorization).toBe('Bearer token-1')
  })

  it('omits the authorization header without a token', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ code: 0, message: 'OK', data: null }))
    vi.stubGlobal('fetch', fetchMock)

    await request('/api/health')

    const headers = fetchMock.mock.calls[0][1].headers as Record<string, string>
    expect(headers.Authorization).toBeUndefined()
  })

  it('surfaces the server message on a business error', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ code: 409, message: '用户名已被占用', data: null }, 409)))

    await expect(request('/api/auth/register')).rejects.toThrow(new ApiError(409, '用户名已被占用'))
  })

  it('turns an empty proxy response (backend down) into a readable error', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('', { status: 500 })))

    await expect(request('/api/auth/captcha')).rejects.toThrow('服务暂不可用')
  })

  it('turns a non-JSON response into a readable error', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('<html>Bad Gateway</html>', { status: 502 })))

    await expect(request('/api/auth/me')).rejects.toThrow('服务返回了无法解析的响应')
  })

  it('drops the token and notifies the app when the token is rejected', async () => {
    const onUnauthorized = vi.fn()
    setUnauthorizedHandler(onUnauthorized)
    setToken('stale-token')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      jsonResponse({ code: 401, message: '未登录或登录已过期', data: null }, 401),
    ))

    await expect(request('/api/auth/me')).rejects.toThrow('未登录或登录已过期')

    expect(getToken()).toBeNull()
    expect(onUnauthorized).toHaveBeenCalledOnce()
  })
})
