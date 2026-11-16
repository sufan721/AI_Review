export type MemberRole = 'OWNER' | 'EDITOR' | 'VIEWER'

/** 前端可把成员改成的角色：不含 OWNER，OWNER 只读展示。 */
export type EditableMemberRole = 'EDITOR' | 'VIEWER'

export type ResourceType = 'NOTE' | 'DOCUMENT'

export type KnowledgeBase = {
  id: number
  ownerId: number
  name: string
  description: string | null
  memberRole: MemberRole
  createdAt: string
  updatedAt: string
}

export type KnowledgeBasePayload = {
  name: string
  description?: string
}

export type KnowledgeBaseMember = {
  userId: number
  username: string | null
  nickname: string | null
  memberRole: MemberRole
  createdAt: string
}

export type KnowledgeBaseResource = {
  resourceId: number
  resourceType: ResourceType
  name: string | null
  indexStatus: string
  addedAt: string
}

export type ResourceAddPayload = {
  resourceType: ResourceType
  resourceId: number
}
