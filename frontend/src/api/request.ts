import type { Result } from '../types/api'

const TOKEN_KEY = 'aireview.token'

/** 测试与无 localStorage 的环境退化为内存存储，避免请求层依赖浏览器 API。 */
const memoryStore = new Map<string, string>()

function readToken(): string | null {
  if (typeof localStorage === 'undefined') return memoryStore.get(TOKEN_KEY) ?? null
  return localStorage.getItem(TOKEN_KEY)
}

export function getToken(): string | null {
  return readToken()
}

export function setToken(token: string | null): void {
  if (typeof localStorage === 'undefined') {
    if (token === null) memoryStore.delete(TOKEN_KEY)
    else memoryStore.set(TOKEN_KEY, token)
    return
  }
  if (token === null) localStorage.removeItem(TOKEN_KEY)
  else localStorage.setItem(TOKEN_KEY, token)
}

export class ApiError extends Error {
  constructor(readonly code: number, message: string) {
    super(message)
    this.name = 'ApiError'
  }
}

let unauthorizedHandler: (() => void) | null = null

/** 令牌失效时的统一处理（跳登录页），由应用启动时注入。 */
export function setUnauthorizedHandler(handler: () => void): void {
  unauthorizedHandler = handler
}

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const token = getToken()
  const response = await fetch(path, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(token === null ? {} : { Authorization: `Bearer ${token}` }),
      ...init?.headers,
    },
  })
  const body = (await response.json()) as Result<T>
  if (!response.ok || body.code !== 0) {
    if (body.code === 401) {
      setToken(null)
      unauthorizedHandler?.()
    }
    throw new ApiError(body.code, body.message)
  }
  return body.data
}

export function post<T>(path: string, payload?: unknown): Promise<T> {
  return request<T>(path, { method: 'POST', body: payload === undefined ? undefined : JSON.stringify(payload) })
}

export function put<T>(path: string, payload?: unknown): Promise<T> {
  return request<T>(path, { method: 'PUT', body: payload === undefined ? undefined : JSON.stringify(payload) })
}

export function patch<T>(path: string, payload?: unknown): Promise<T> {
  return request<T>(path, {
    method: 'PATCH',
    body: payload === undefined ? undefined : JSON.stringify(payload),
  })
}

export function del<T>(path: string): Promise<T> {
  return request<T>(path, { method: 'DELETE' })
}
