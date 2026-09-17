import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { DEFAULT_QUERY, useNotesStore } from './notes'
import { setToken } from '../api/request'
import type { Note, NoteSummary } from '../types/note'

const summary: NoteSummary = {
  id: 1,
  title: '会议纪要',
  isPinned: true,
  isArchived: false,
  indexStatus: 'PENDING',
  updatedAt: '2026-09-17 10:00:00',
}

const detail: Note = { ...summary, content: '# 结论', contentVersion: 3, createdAt: '2026-09-17 09:00:00' }

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function pageOf(records: NoteSummary[], total = records.length) {
  return { code: 0, message: 'OK', data: { records, total, page: 1, size: 20 } }
}

function stub(data: unknown) {
  // 每次调用都要新建 Response，Body 只能被读取一次
  const fetchMock = vi.fn().mockImplementation(async () => jsonResponse(data))
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

/** 按顺序返回多个响应，用于「先改后刷新列表」这类会发两次请求的操作。 */
function stubSequence(...bodies: unknown[]) {
  let index = 0
  const fetchMock = vi.fn().mockImplementation(async () => {
    const body = bodies[Math.min(index, bodies.length - 1)]
    index += 1
    return jsonResponse(body)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

beforeEach(() => setActivePinia(createPinia()))

afterEach(() => {
  vi.unstubAllGlobals()
  setToken(null)
})

describe('notes store', () => {
  it('defaults to the unarchived list', () => {
    expect(DEFAULT_QUERY).toEqual({ isArchived: false, page: 1, size: 20 })
  })

  it('loads a page of notes into the list', async () => {
    stub(pageOf([summary], 42))

    const notes = useNotesStore()
    await notes.fetchList()

    expect(notes.list).toEqual([summary])
    expect(notes.total).toBe(42)
    expect(notes.loading).toBe(false)
  })

  it('merges query changes and requests the right filter', async () => {
    const fetchMock = stub(pageOf([]))

    const notes = useNotesStore()
    await notes.fetchList({ isArchived: true, page: 3 })

    expect(fetchMock.mock.calls[0][0]).toBe('/api/notes?isArchived=true&page=3&size=20')
    expect(notes.query.isArchived).toBe(true)
    expect(notes.query.page).toBe(3)
  })

  it('resets loading state when the request fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      jsonResponse({ code: 500, message: '服务器内部错误', data: null }, 500),
    ))

    const notes = useNotesStore()

    await expect(notes.fetchList()).rejects.toThrow('服务器内部错误')
    expect(notes.loading).toBe(false)
  })

  it('loads a single note into current', async () => {
    stub({ code: 0, message: 'OK', data: detail })

    const notes = useNotesStore()
    await notes.fetchOne(1)

    expect(notes.current?.contentVersion).toBe(3)
  })

  it('creates when no id is given and updates otherwise', async () => {
    const fetchMock = stub({ code: 0, message: 'OK', data: detail })

    const notes = useNotesStore()
    await notes.save(null, { title: 't', content: 'c' })
    await notes.save(1, { title: 't2', content: 'c2' })

    expect(fetchMock.mock.calls[0][1].method).toBe('POST')
    expect(fetchMock.mock.calls[1][0]).toBe('/api/notes/1')
    expect(fetchMock.mock.calls[1][1].method).toBe('PUT')
    expect(notes.current?.id).toBe(1)
  })

  it('refreshes the list after a delete', async () => {
    const fetchMock = stubSequence({ code: 0, message: 'OK', data: null }, pageOf([]))

    const notes = useNotesStore()
    notes.current = detail
    await notes.remove(1)

    expect(fetchMock.mock.calls[0][0]).toBe('/api/notes/1')
    expect(fetchMock.mock.calls[0][1].method).toBe('DELETE')
    expect(fetchMock.mock.calls[1][0]).toBe('/api/notes?isArchived=false&page=1&size=20')
    expect(notes.current).toBeNull()
  })

  it('refreshes the list after pinning so the order updates', async () => {
    const fetchMock = stubSequence({ code: 0, message: 'OK', data: detail }, pageOf([summary]))

    const notes = useNotesStore()
    await notes.togglePin(1)

    expect(fetchMock.mock.calls[0][0]).toBe('/api/notes/1/pin')
    expect(fetchMock.mock.calls[0][1].method).toBe('PATCH')
    expect(fetchMock.mock.calls[1][0]).toContain('/api/notes?')
    expect(notes.list).toEqual([summary])
  })

  it('drops local state on reset', async () => {
    stub(pageOf([summary], 1))

    const notes = useNotesStore()
    await notes.fetchList()
    notes.reset()

    expect(notes.list).toEqual([])
    expect(notes.total).toBe(0)
    expect(notes.query).toEqual(DEFAULT_QUERY)
  })
})
