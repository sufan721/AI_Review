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

/** 管理后台用户视图，roles 为当前持有的角色码集合。 */
export type AdminUser = {
  id: number
  username: string
  nickname: string | null
  status: number
  roles: string[]
}

/** 实例访问与策略开关（对应后端 GeneralSetting）。 */
export type GeneralSetting = {
  disallowUserRegistration: boolean
  disallowPasswordAuth: boolean
  forbidChangeUsername: boolean
  forbidChangeNickname: boolean
}

/** 实例初始化状态：空库时首位注册者自动成为管理员。 */
export type BootstrapStatus = {
  initialized: boolean
}

/** RAG 系统 API 配置（以 JSON 存入 instance_setting 的 RAG_API 项）。 */
export type RagConfig = {
  enabled: boolean
  baseUrl: string
  apiKey: string
  model: string
}

/** 数据持久化配置（以 JSON 存入 instance_setting 的 PERSISTENCE 项）。 */
export type PersistenceConfig = {
  enabled: boolean
  storagePath: string
  autoBackup: boolean
  retentionDays: number
}
