export type IdentityProvider = {
  uid: string
  name: string
  type: string
  identifierFilter: string | null
  config: string | null
}

export type IdentityProviderPayload = {
  name: string
  type: string
  identifierFilter?: string | null
  config?: string | null
}

export type InstanceSetting = {
  name: string
  value: string | null
  description: string | null
}

export type InstanceSettingPayload = {
  value?: string | null
  description?: string | null
}

export type AdminUser = {
  id: number
  username: string
  nickname: string | null
  status: number
}
