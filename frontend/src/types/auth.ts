export type User = {
  id: number
  username: string
  nickname: string | null
  roles: string[]
  createdAt: string
}

export type Captcha = { captchaKey: string; captchaImage: string }

export type LoginPayload = { username: string; password: string; captchaKey: string; captchaCode: string }

export type RegisterPayload = {
  username: string
  password: string
  confirmPassword: string
  phone: string
  smsCode: string
  nickname?: string
  email?: string
}

export type SmsLoginPayload = { phone: string; smsCode: string }

export type EmailLoginPayload = { email: string; emailCode: string }

export type ForgotResetPayload = {
  account: string
  resetCode: string
  newPassword: string
  confirmPassword: string
}

export type LoginResponse = { accessToken: string; refreshToken: string; expiresIn: number; user: User }
