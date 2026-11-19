import { afterEach, describe, expect, it, vi } from 'vitest'
import { persistenceApi } from './persistence'
import { setToken } from './request'

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } })
}

function stub(body: unknown = null) {
  const fetchMock = vi.fn().mockImplementation(async () => jsonResponse({ code: 0, message: 'OK', data: body }))
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

afterEach(() => {
  vi.unstubAllGlobals()
  setToken(null)
})

describe('persistence api', () => {
  it('reads and updates the typed setting', async () => {
    const setting = {
      enabled: true, storagePath: 'F:/data/backups', autoBackup: true,
      backupCron: '0 0 3 * * *', retentionDays: 30,
    }
    const fetchMock = stub(setting)
    expect(await persistenceApi.setting()).toEqual(setting)
    expect(fetchMock.mock.calls[0][0]).toBe('/api/admin/persistence/setting')

    await persistenceApi.updateSetting(setting)
    const [path, init] = fetchMock.mock.calls[1] as [string, RequestInit]
    expect(path).toBe('/api/admin/persistence/setting')
    expect(init.method).toBe('PUT')
    expect(JSON.parse(init.body as string)).toEqual(setting)
  })

  it('starts backup/restore with the right payloads', async () => {
    let call = 0
    const fetchMock = vi.fn().mockImplementation(async () => {
      call++
      return jsonResponse({ code: 0, message: 'OK', data: call === 1 ? { id: 9 } : { taskId: 11 } })
    })
    vi.stubGlobal('fetch', fetchMock)
    expect(await persistenceApi.startBackup()).toEqual({ id: 9 })
    expect(fetchMock.mock.calls[0][0]).toBe('/api/admin/persistence/backups')
    expect((fetchMock.mock.calls[0][1] as RequestInit).method).toBe('POST')

    await persistenceApi.startRestore(7)
    const [path, init] = fetchMock.mock.calls[1] as [string, RequestInit]
    expect(path).toBe('/api/admin/persistence/backups/7/restore')
    expect(JSON.parse(init.body as string)).toEqual({ confirm: 'RESTORE' })
  })

  it('polls task and verifies an archive', async () => {
    const fetchMock = stub({ id: 3, kind: 'BACKUP', status: 'SUCCESS', completedTables: 18, totalTables: 18, message: '完成' })
    expect((await persistenceApi.task(3)).status).toBe('SUCCESS')
    expect(fetchMock.mock.calls[0][0]).toBe('/api/admin/persistence/tasks/3')

    await persistenceApi.verify(5)
    expect(fetchMock.mock.calls[1][0]).toBe('/api/admin/persistence/backups/5/verify')
  })

  it('surfaces paged history', async () => {
    const fetchMock = stub({ records: [], total: 0, page: 2, size: 20 })
    const result = await persistenceApi.backups(2, 20)
    expect(result.page).toBe(2)
    expect(fetchMock.mock.calls[0][0]).toBe('/api/admin/persistence/backups?page=2&size=20')
  })
})
