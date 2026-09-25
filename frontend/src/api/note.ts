import { del, patch, post, put, request } from './request'
import type { PageResult } from '../types/api'
import type { Note, NotePayload, NoteQuery, NoteSummary } from '../types/note'

function toSearchParams(query: NoteQuery): string {
  const params = new URLSearchParams()
  if (query.isPinned !== undefined) params.set('isPinned', String(query.isPinned))
  if (query.isArchived !== undefined) params.set('isArchived', String(query.isArchived))
  if (query.page !== undefined) params.set('page', String(query.page))
  if (query.size !== undefined) params.set('size', String(query.size))
  const serialized = params.toString()
  return serialized === '' ? '' : `?${serialized}`
}

export const noteApi = {
  list: (query: NoteQuery = {}) => request<PageResult<NoteSummary>>(`/api/notes${toSearchParams(query)}`),
  detail: (id: number) => request<Note>(`/api/notes/${id}`),
  create: (payload: NotePayload) => post<Note>('/api/notes', payload),
  update: (id: number, payload: NotePayload) => put<Note>(`/api/notes/${id}`, payload),
  remove: (id: number) => del<null>(`/api/notes/${id}`),
  togglePin: (id: number) => patch<Note>(`/api/notes/${id}/pin`),
  toggleArchive: (id: number) => patch<Note>(`/api/notes/${id}/archive`),
  reindex: (id: number) => post<Note>(`/api/notes/${id}/reindex`),
}
