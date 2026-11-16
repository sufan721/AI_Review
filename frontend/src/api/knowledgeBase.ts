import { del, post, put, request } from './request'
import type {
  KnowledgeBase,
  KnowledgeBaseMember,
  KnowledgeBasePayload,
  KnowledgeBaseResource,
  MemberRole,
  ResourceAddPayload,
} from '../types/knowledgeBase'

export const knowledgeBaseApi = {
  list: () => request<KnowledgeBase[]>('/api/knowledge-bases'),
  create: (payload: KnowledgeBasePayload) => post<KnowledgeBase>('/api/knowledge-bases', payload),
  detail: (id: number) => request<KnowledgeBase>(`/api/knowledge-bases/${id}`),
  update: (id: number, payload: KnowledgeBasePayload) => put<KnowledgeBase>(`/api/knowledge-bases/${id}`, payload),
  remove: (id: number) => del<null>(`/api/knowledge-bases/${id}`),
  members: (id: number) => request<KnowledgeBaseMember[]>(`/api/knowledge-bases/${id}/members`),
  updateMemberRole: (id: number, userId: number, memberRole: MemberRole) =>
    put<null>(`/api/knowledge-bases/${id}/members/${userId}`, { memberRole }),
  removeMember: (id: number, userId: number) => del<null>(`/api/knowledge-bases/${id}/members/${userId}`),
  resources: (id: number) => request<KnowledgeBaseResource[]>(`/api/knowledge-bases/${id}/resources`),
  addResource: (id: number, payload: ResourceAddPayload) => post<null>(`/api/knowledge-bases/${id}/resources`, payload),
  removeResource: (id: number, resourceType: string, resourceId: number) =>
    del<null>(`/api/knowledge-bases/${id}/resources/${resourceType}/${resourceId}`),
}
