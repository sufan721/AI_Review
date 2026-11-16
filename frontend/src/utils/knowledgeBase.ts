import type { KnowledgeBaseResource, MemberRole } from '../types/knowledgeBase'

export const MEMBER_ROLE_LABELS: Record<MemberRole, string> = {
  OWNER: '拥有者',
  EDITOR: '编辑',
  VIEWER: '只读',
}

/** 资源复合键：去重与列表 key 必须同时区分类型和 ID，避免 NOTE 与 DOCUMENT 的 id 撞车。 */
export function resourceKey(resourceType: string, resourceId: number): string {
  return `${resourceType}:${resourceId}`
}

export function isResourceIncluded(
  resources: ReadonlyArray<KnowledgeBaseResource>,
  resourceType: string,
  resourceId: number,
): boolean {
  return resources.some(
    (resource) => resource.resourceType === resourceType && resource.resourceId === resourceId,
  )
}
