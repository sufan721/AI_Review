import { del, post, request } from './request'
import type { PageResult } from '../types/api'
import type { Document, DocumentSummary } from '../types/document'

export const documentApi = {
  list: (page = 1, size = 20) => request<PageResult<DocumentSummary>>(`/api/documents?page=${page}&size=${size}`),
  detail: (id: number) => request<Document>(`/api/documents/${id}`),
  upload: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return request<Document>('/api/documents', { method: 'POST', body: form })
  },
  remove: (id: number) => del<null>(`/api/documents/${id}`),
  reindex: (id: number) => post<Document>(`/api/documents/${id}/reindex`),
}
