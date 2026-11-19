import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { attachmentStorageApi } from '../api/attachmentStorage'
import { useAttachmentStorageStore } from './attachmentStorage'
import type { AttachmentStorageSetting } from '../types/attachmentStorage'

vi.mock('../api/attachmentStorage', () => ({
  attachmentStorageApi: {
    setting: vi.fn(),
    updateSetting: vi.fn(),
    status: vi.fn(),
    list: vi.fn(),
    upload: vi.fn(),
    remove: vi.fn(),
    download: vi.fn(),
  },
}))

const api = vi.mocked(attachmentStorageApi)
const setting: AttachmentStorageSetting = {
  mode: 'LOCAL_FS', localDir: 'F:/data/attachments', maxSizeMb: 20,
}
const status = {
  mode: 'LOCAL_FS' as const, localDir: 'F:/data/attachments', directoryWritable: true,
  fsCount: 0, fsBytes: 0, dbCount: 0, dbBytes: 0,
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
  api.setting.mockResolvedValue(setting)
  api.status.mockResolvedValue(status)
  api.list.mockResolvedValue([])
})

describe('attachment storage store', () => {
  it('loads setting, status and items together', async () => {
    const store = useAttachmentStorageStore()
    await store.loadAll()
    expect(store.setting).toEqual(setting)
    expect(store.status?.directoryWritable).toBe(true)
    expect(store.items).toEqual([])
    expect(store.mode).toBe('LOCAL_FS')
  })

  it('uses normalized setting returned by server', async () => {
    const normalized = { mode: 'DATABASE' as const, localDir: 'F:/normalized', maxSizeMb: 30 }
    api.updateSetting.mockResolvedValue(normalized)
    api.status.mockResolvedValue({ ...status, mode: 'DATABASE' })
    const store = useAttachmentStorageStore()
    await store.loadAll()

    const saved = await store.saveSetting({ mode: 'DATABASE', localDir: 'F:/normalized', maxSizeMb: 30 })
    expect(saved).toEqual(normalized)
    expect(store.setting).toEqual(normalized)
    expect(store.mode).toBe('DATABASE')
    expect(store.busy).toBe(false)
    expect(api.updateSetting).toHaveBeenCalledWith({ mode: 'DATABASE', localDir: 'F:/normalized', maxSizeMb: 30 })
  })

  it('adds and removes items locally after mutation', async () => {
    const created = {
      id: 1, originalName: 'a.txt', contentType: 'text/plain', sizeBytes: 3,
      sha256: 'x', storageMode: 'LOCAL_FS' as const, createdAt: '2026-11-19 12:00:00',
    }
    api.upload.mockResolvedValue(created)
    api.remove.mockResolvedValue(null)
    const store = useAttachmentStorageStore()
    await store.loadAll()

    await store.upload(new File(['abc'], 'a.txt'))
    expect(store.items).toEqual([created])
    await store.remove(1)
    expect(store.items).toEqual([])
    expect(api.remove).toHaveBeenCalledWith(1)
  })
})
