export type User = {
  id: number
  username: string
  nickname: string | null
  roles: string[]
  createdAt: string
}

export type Captcha = { captchaKey: string; captchaImage: string }

export type LoginPayload = { username: string; password: string; captchaKey?: string; captchaCode?: string }

export type RegisterPayload = {
  username: string
  password: string
}

export type LoginResponse = { accessToken: string; refreshToken: string; expiresIn: number; user: User }
