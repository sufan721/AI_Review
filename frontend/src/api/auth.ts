import { post, request } from './request'
import type {
  Captcha,
  EmailLoginPayload,
  ForgotResetPayload,
  LoginPayload,
  LoginResponse,
  RegisterPayload,
  SmsLoginPayload,
  User,
} from '../types/auth'

export const authApi = {
  captcha: () => request<Captcha>('/api/auth/captcha'),
  register: (payload: RegisterPayload) => post<LoginResponse>('/api/auth/register', payload),
  login: (payload: LoginPayload) => post<LoginResponse>('/api/auth/login', payload),
  refresh: (refreshToken: string, silentUnauthorized = false) =>
    post<LoginResponse>('/api/auth/refresh', { refreshToken }, silentUnauthorized),
  logout: () => post<void>('/api/auth/logout'),
  smsSend: (phone: string) => post<void>('/api/auth/sms/send', { phone }),
  smsLogin: (payload: SmsLoginPayload) => post<LoginResponse>('/api/auth/sms/login', payload),
  emailSend: (email: string) => post<void>('/api/auth/email/send', { email }),
  emailLogin: (payload: EmailLoginPayload) => post<LoginResponse>('/api/auth/email/login', payload),
  forgotSendCode: (account: string) => post<void>('/api/auth/forgot/send-code', { account }),
  forgotReset: (payload: ForgotResetPayload) => post<void>('/api/auth/forgot/reset', payload),
  me: (silentUnauthorized = false) =>
    request<User>('/api/auth/me', undefined, silentUnauthorized ? { silentUnauthorized: true } : undefined),
}
