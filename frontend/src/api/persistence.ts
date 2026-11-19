import { del, post, put, request } from './request'
import { getToken } from './request'
import type {
  BackupPage,
  BackupRecord,
  PersistenceSetting,
  PersistenceStatus,
  PersistenceTask,
  RestoreResult,
  VerifyResult,
} from '../types/persistence'

/** 数据快照：配置、状态、历史、下载、校验与恢复。全部仅管理员可用。 */
export const persistenceApi = {
  setting: () => request<PersistenceSetting>('/api/admin/persistence/setting'),
  updateSetting: (payload: PersistenceSetting) =>
    put<PersistenceSetting>('/api/admin/persistence/setting', payload),
  status: () => request<PersistenceStatus>('/api/admin/persistence/status'),
  backups: (page = 1, size = 20) =>
    request<BackupPage>(`/api/admin/persistence/backups?page=${page}&size=${size}`),
  backup: (id: number) => request<BackupRecord>(`/api/admin/persistence/backups/${id}`),
  startBackup: () => post<{ id: number }>('/api/admin/persistence/backups'),
  task: (id: number) => request<PersistenceTask>(`/api/admin/persistence/tasks/${id}`),
  verify: (id: number) => post<VerifyResult>(`/api/admin/persistence/backups/${id}/verify`),
  remove: (id: number) => del<null>(`/api/admin/persistence/backups/${id}`),
  startRestore: (id: number) =>
    post<{ taskId: number }>(`/api/admin/persistence/backups/${id}/restore`, { confirm: 'RESTORE' }),
  /** 恢复完成后读取结果（任务成功后写入的摘要）。 */
  restoreResult: () => request<RestoreResult>('/api/admin/persistence/last-restore'),
  /**
   * 下载走原生 fetch：接口返回的是 zip 字节流，不带 Result 信封；
   * 必须手动带 Authorization，否则会被 JWT 拦截器拦成 401。
   */
  download: async (id: number, fileName: string): Promise<void> => {
    const token = getToken()
    const response = await fetch(`/api/admin/persistence/backups/${id}/download`, {
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
