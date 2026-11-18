import { post, request } from './request'
import type {
  Captcha,
  LoginPayload,
  LoginResponse,
  RegisterPayload,
  User,
} from '../types/auth'

export const authApi = {
  captcha: () => request<Captcha>('/api/auth/captcha'),
  register: (payload: RegisterPayload) => post<LoginResponse>('/api/auth/register', payload),
  login: (payload: LoginPayload) => post<LoginResponse>('/api/auth/login', payload),
  refresh: (refreshToken: string, silentUnauthorized = false) =>
    post<LoginResponse>('/api/auth/refresh', { refreshToken }, silentUnauthorized),
  logout: () => post<void>('/api/auth/logout'),
  me: (silentUnauthorized = false) =>
    request<User>('/api/auth/me', undefined, silentUnauthorized ? { silentUnauthorized: true } : undefined),
}
