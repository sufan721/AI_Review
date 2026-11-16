import type { Result } from '../types/api'

const TOKEN_KEY = 'aireview.token'
const REFRESH_TOKEN_KEY = 'aireview.refreshToken'

/** 测试与无 localStorage 的环境退化为内存存储，避免请求层依赖浏览器 API。 */
const memoryStore = new Map<string, string>()

function read(key: string): string | null {
  if (typeof localStorage === 'undefined') return memoryStore.get(key) ?? null
  return localStorage.getItem(key)
}

function write(key: string, value: string | null): void {
  if (typeof localStorage === 'undefined') {
    if (value === null) memoryStore.delete(key)
    else memoryStore.set(key, value)
    return
  }
  if (value === null) localStorage.removeItem(key)
  else localStorage.setItem(key, value)
}

export function getToken(): string | null {
  return read(TOKEN_KEY)
}

export function setToken(token: string | null): void {
  write(TOKEN_KEY, token)
}

export function getRefreshToken(): string | null {
  return read(REFRESH_TOKEN_KEY)
}

export function setRefreshToken(token: string | null): void {
  write(REFRESH_TOKEN_KEY, token)
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

export interface RequestOptions {
  /** 401 时交由调用方自行处理：不清理令牌、不触发全局未授权回调（用于自动续期探测）。 */
  silentUnauthorized?: boolean
}

export async function request<T>(path: string, init?: RequestInit, options?: RequestOptions): Promise<T> {
  const token = getToken()
  const isFormData = typeof FormData !== 'undefined' && init?.body instanceof FormData
  const response = await fetch(path, {
    ...init,
    headers: {
      ...(isFormData ? {} : { 'Content-Type': 'application/json' }),
      ...(token === null ? {} : { Authorization: `Bearer ${token}` }),
      ...init?.headers,
    },
  })
  const body = await readResult<T>(response)
  if (!response.ok || body.code !== 0) {
    if (body.code === 401 && options?.silentUnauthorized !== true) {
      // 只清访问令牌，保留刷新令牌，供 restoreSession 尝试续期
      setToken(null)
      unauthorizedHandler?.()
    }
    throw new ApiError(body.code, body.message)
  }
  return body.data
}

/** 解析统一返回信封；后端未启动或代理返回空体/非 JSON 时给出可读错误，而非让 JSON 解析异常直接冒泡。 */
async function readResult<T>(response: Response): Promise<Result<T>> {
  const text = await response.text()
  if (text.trim() === '') {
    const code = response.ok ? 0 : response.status || 502
    return { code, message: code === 0 ? 'OK' : '服务暂不可用，请确认后端已启动', data: null as T }
  }
  try {
    return JSON.parse(text) as Result<T>
  } catch {
    return { code: 502, message: '服务返回了无法解析的响应', data: null as T }
  }
}

export function post<T>(path: string, payload?: unknown, silentUnauthorized = false): Promise<T> {
  return request<T>(
    path,
    { method: 'POST', body: payload === undefined ? undefined : JSON.stringify(payload) },
    silentUnauthorized ? { silentUnauthorized: true } : undefined,
  )
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
