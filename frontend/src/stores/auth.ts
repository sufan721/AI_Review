import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi } from '../api/auth'
import { getRefreshToken, getToken, setRefreshToken, setToken } from '../api/request'
import type {
  LoginPayload,
  LoginResponse,
  RegisterPayload,
  User,
} from '../types/auth'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const isAuthenticated = computed(() => user.value !== null)

  function persist(response: LoginResponse): void {
    setToken(response.accessToken)
    setRefreshToken(response.refreshToken)
    user.value = response.user
  }

  async function login(payload: LoginPayload): Promise<void> {
    persist(await authApi.login(payload))
  }

  async function register(payload: RegisterPayload): Promise<void> {
    persist(await authApi.register(payload))
  }

  /** 用刷新令牌换新令牌；无刷新令牌时抛错，由调用方处理。 */
  async function refresh(): Promise<void> {
    const refreshToken = getRefreshToken()
    if (refreshToken === null) throw new Error('无刷新令牌')
    persist(await authApi.refresh(refreshToken))
  }

  /** 刷新页面后用本地令牌恢复登录态；访问令牌失效时尝试用刷新令牌续期，仍失败则静默清理。 */
  async function restoreSession(): Promise<void> {
    if (user.value !== null) return
    if (getToken() === null && getRefreshToken() === null) return
    try {
      user.value = await authApi.me(true)
    } catch {
      const refreshToken = getRefreshToken()
      if (refreshToken !== null) {
        try {
          // 续期接口直接带回用户信息，无需再请求 /me
          persist(await authApi.refresh(refreshToken, true))
          return
        } catch {
          /* 续期失败，走清理 */
        }
      }
      clear()
    }
  }

  function clear(): void {
    setToken(null)
    setRefreshToken(null)
    user.value = null
  }

  /** 本地登出并尽力通知后端拉黑当前令牌；失败不影响本地登出。 */
  function logout(): void {
    if (getToken() !== null) {
      void authApi.logout().catch(() => {})
    }
    clear()
  }

  return { user, isAuthenticated, login, register, refresh, restoreSession, logout }
})
