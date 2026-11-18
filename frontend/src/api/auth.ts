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
  /** 当前用户自助改密码：校验旧密码，成功后旧会话失效需重新登录。 */
  changePassword: (oldPassword: string, newPassword: string) =>
    post<void>('/api/auth/change-password', { oldPassword, newPassword }),
}
