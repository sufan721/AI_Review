import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { persistenceApi } from '../api/persistence'
import { usePersistenceStore } from './persistence'
import type { PersistenceSetting } from '../types/persistence'

vi.mock('../api/persistence', () => ({
  persistenceApi: {
    setting: vi.fn(),
    updateSetting: vi.fn(),
    status: vi.fn(),
    backups: vi.fn(),
    startBackup: vi.fn(),
    task: vi.fn(),
    verify: vi.fn(),
    remove: vi.fn(),
    download: vi.fn(),
    startRestore: vi.fn(),
    restoreResult: vi.fn(),
  },
}))

const api = vi.mocked(persistenceApi)
const setting: PersistenceSetting = {
  enabled: true, storagePath: 'F:/data/backups', autoBackup: false,
  backupCron: '0 0 3 * * *', retentionDays: 30,
}
const emptyPage = { records: [], total: 0, page: 1, size: 20 }

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
  api.setting.mockResolvedValue(setting)
  api.status.mockResolvedValue({
    enabled: true, storagePath: setting.storagePath, directoryWritable: true,
    databaseSizeBytes: 1024, totalRows: 3, indexChunks: 2, tables: [],
    lastBackup: null, nextRunAt: null, autoBackupEnabled: true, runningBackupId: null,
  })
  api.backups.mockResolvedValue(emptyPage)
})

describe('persistence store', () => {
  it('loads setting, status and history together', async () => {
    const store = usePersistenceStore()
    await store.loadAll()
    expect(store.setting).toEqual(setting)
    expect(store.status?.directoryWritable).toBe(true)
    expect(store.total).toBe(0)
  })

  it('uses the normalized setting returned by the server', async () => {
    const normalized = { ...setting, storagePath: 'F:/data/backups' }
    api.updateSetting.mockResolvedValue(normalized)
    const store = usePersistenceStore()
    await store.saveSetting({ ...setting, storagePath: './data/backups' })
    expect(api.updateSetting).toHaveBeenCalledWith({ ...setting, storagePath: './data/backups' })
    expect(store.setting).toEqual(normalized)
    expect(api.status).toHaveBeenCalled()
  })

  it('polls a backup task until it finishes and then reloads', async () => {
    api.startBackup.mockResolvedValue({ id: 7 })
    api.task
      .mockResolvedValueOnce({ id: 7, kind: 'BACKUP', status: 'RUNNING', completedTables: 2, totalTables: 18, message: '备份中' })
      .mockResolvedValueOnce({ id: 7, kind: 'BACKUP', status: 'SUCCESS', completedTables: 18, totalTables: 18, message: '完成' })
    const store = usePersistenceStore()
    await store.startBackup()
    expect(api.task).toHaveBeenCalledTimes(2)
    expect(store.busy).toBe(false)
    expect(store.task?.status).toBe('SUCCESS')
    expect(api.backups).toHaveBeenCalled()
  })

  it('surfaces verify result', async () => {
    api.verify.mockResolvedValue({ valid: false, reason: '校验和不一致' })
    const store = usePersistenceStore()
    expect(await store.verify(4)).toEqual({ valid: false, reason: '校验和不一致' })
    expect(store.verifyResult?.valid).toBe(false)
  })
})
