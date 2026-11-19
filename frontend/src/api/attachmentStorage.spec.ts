import { afterEach, describe, expect, it, vi } from 'vitest'
import { attachmentStorageApi } from './attachmentStorage'
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

describe('attachment storage api', () => {
  it('reads and updates the typed setting', async () => {
    const setting = { mode: 'LOCAL_FS' as const, localDir: 'F:/data/attachments', maxSizeMb: 20 }
    const fetchMock = stub(setting)
    expect(await attachmentStorageApi.setting()).toEqual(setting)
    expect(fetchMock.mock.calls[0][0]).toBe('/api/admin/attachment-storage/setting')

    await attachmentStorageApi.updateSetting(setting)
    const [path, init] = fetchMock.mock.calls[1] as [string, RequestInit]
    expect(path).toBe('/api/admin/attachment-storage/setting')
    expect(init.method).toBe('PUT')
    expect(JSON.parse(init.body as string)).toEqual(setting)
  })

  it('reads storage status', async () => {
    const status = {
      mode: 'DATABASE' as const, localDir: 'F:/data/attachments', directoryWritable: true,
      fsCount: 2, fsBytes: 100, dbCount: 3, dbBytes: 200,
    }
    const fetchMock = stub(status)
    expect(await attachmentStorageApi.status()).toEqual(status)
    expect(fetchMock.mock.calls[0][0]).toBe('/api/admin/attachment-storage/status')
  })

  it('uploads as multipart form data', async () => {
    const item = {
      id: 1, originalName: 'a.txt', contentType: 'text/plain', sizeBytes: 3,
      sha256: 'x', storageMode: 'LOCAL_FS' as const, createdAt: '2026-11-19 12:00:00',
    }
    const fetchMock = stub(item)
    expect(await attachmentStorageApi.upload(new File(['abc'], 'a.txt'))).toEqual(item)
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(path).toBe('/api/attachments')
    expect(init.method).toBe('POST')
    expect(init.body).toBeInstanceOf(FormData)
  })

  it('downloads raw bytes with bearer token', async () => {
    setToken('tok')
    const blob = new Blob(['bytes'])
    const fetchMock = vi.fn().mockImplementation(async () => new Response(blob, { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    const createObjectURL = vi.fn().mockReturnValue('blob:mock')
    vi.stubGlobal('URL', { ...URL, createObjectURL, revokeObjectURL: vi.fn() })
    const click = vi.fn()
    vi.stubGlobal('document', {
      createElement: () => ({ click, href: '', download: '' }),
    })

    await attachmentStorageApi.download(7, 'a.txt')
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(path).toBe('/api/attachments/7/download')
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer tok')
    expect(createObjectURL).toHaveBeenCalled()
    expect(click).toHaveBeenCalled()
  })

  it('deletes an attachment', async () => {
    const fetchMock = stub(null)
    await attachmentStorageApi.remove(9)
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(path).toBe('/api/attachments/9')
    expect(init.method).toBe('DELETE')
  })
})
