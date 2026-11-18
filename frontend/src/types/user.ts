/** 用户设置键，后端限定为 GENERAL / TAGS / EDITOR。 */
export type UserSettingKey = 'GENERAL' | 'TAGS' | 'EDITOR'

export type UserProfile = {
  id: number
  username: string
  displayName: string | null
  description: string | null
  avatarUrl: string | null
  roles: string[]
  createdAt: number
}

export type UpdateProfilePayload = {
  displayName?: string | null
  description?: string | null
  avatarUrl?: string | null
}

export type UserSetting = {
  key: string
  value: string
}

export type UpdateUserSettingPayload = {
  key: string
  value: string
}
