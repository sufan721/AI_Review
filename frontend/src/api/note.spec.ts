import { afterEach, describe, expect, it, vi } from 'vitest'
import { noteApi } from './note'
import { setToken } from './request'

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function mockFetch() {
  // 每次调用都要新建 Response，Body 只能被读取一次
  const fetchMock = vi.fn().mockImplementation(async () => jsonResponse({ code: 0, message: 'OK', data: null }))
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

afterEach(() => {
  vi.unstubAllGlobals()
  setToken(null)
})

describe('noteApi', () => {
  it('serializes list filters into the query string', async () => {
    const fetchMock = mockFetch()

    await noteApi.list({ isArchived: false, isPinned: true, page: 2, size: 50 })

    const [path, init] = fetchMock.mock.calls[0]
    expect(path).toBe('/api/notes?isPinned=true&isArchived=false&page=2&size=50')
    expect(init.method).toBeUndefined()
  })

  it('keeps false booleans instead of dropping them', async () => {
    const fetchMock = mockFetch()

    await noteApi.list({ isArchived: false })

    expect(fetchMock.mock.calls[0][0]).toBe('/api/notes?isArchived=false')
  })

  it('omits unset filters', async () => {
    const fetchMock = mockFetch()

    await noteApi.list()

    expect(fetchMock.mock.calls[0][0]).toBe('/api/notes')
  })

  it('creates a note with a JSON body', async () => {
    const fetchMock = mockFetch()

    await noteApi.create({ title: '标题', content: '# 正文' })

    const [path, init] = fetchMock.mock.calls[0]
    expect(path).toBe('/api/notes')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body)).toEqual({ title: '标题', content: '# 正文' })
  })

  it('updates a note by id', async () => {
    const fetchMock = mockFetch()

    await noteApi.update(7, { title: 't', content: 'c' })

    expect(fetchMock.mock.calls[0][0]).toBe('/api/notes/7')
    expect(fetchMock.mock.calls[0][1].method).toBe('PUT')
  })

  it('deletes a note by id', async () => {
    const fetchMock = mockFetch()

    await noteApi.remove(7)

    expect(fetchMock.mock.calls[0][0]).toBe('/api/notes/7')
    expect(fetchMock.mock.calls[0][1].method).toBe('DELETE')
  })

  it('toggles pin and archive with an empty PATCH body', async () => {
    const fetchMock = mockFetch()

    await noteApi.togglePin(7)
    await noteApi.toggleArchive(7)

    expect(fetchMock.mock.calls[0][0]).toBe('/api/notes/7/pin')
    expect(fetchMock.mock.calls[0][1].method).toBe('PATCH')
    expect(fetchMock.mock.calls[0][1].body).toBeUndefined()
    expect(fetchMock.mock.calls[1][0]).toBe('/api/notes/7/archive')
    expect(fetchMock.mock.calls[1][1].method).toBe('PATCH')
  })
})
