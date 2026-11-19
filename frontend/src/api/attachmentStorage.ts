import { del, getToken, post, put, request } from './request'
import type {
  AttachmentItem,
  AttachmentStorageSetting,
  AttachmentStorageStatus,
} from '../types/attachmentStorage'

/** 附件存储：管理员配置/概览，以及登录用户的附件上传下载删除。 */
export const attachmentStorageApi = {
  // ---- 管理员：存储方式配置与用量 ----
  setting: () => request<AttachmentStorageSetting>('/api/admin/attachment-storage/setting'),
  updateSetting: (payload: AttachmentStorageSetting) =>
    put<AttachmentStorageSetting>('/api/admin/attachment-storage/setting', payload),
  status: () => request<AttachmentStorageStatus>('/api/admin/attachment-storage/status'),

  // ---- 登录用户：附件本体 ----
  list: () => request<AttachmentItem[]>('/api/attachments'),
  upload: (file: File): Promise<AttachmentItem> => {
    const form = new FormData()
    form.append('file', file)
    return request<AttachmentItem>('/api/attachments', { method: 'POST', body: form })
  },
  remove: (id: number) => del<null>(`/api/attachments/${id}`),
  /** 下载走原生 fetch：接口返回裸字节流，不带 Result 信封，必须手动带 Authorization。 */
  download: async (id: number, fileName: string): Promise<void> => {
    const token = getToken()
    const response = await fetch(`/api/attachments/${id}/download`, {
      headers: token === null ? {} : { Authorization: `Bearer ${token}` },
    })
    if (!response.ok) throw new Error('下载失败，请检查登录状态')
    const blob = await response.blob()
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = fileName
    anchor.click()
    URL.revokeObjectURL(url)
  },
}
