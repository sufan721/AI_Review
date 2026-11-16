import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useKnowledgeBasesStore } from './knowledgeBases'
import { setToken } from '../api/request'
import type { KnowledgeBase, KnowledgeBaseMember, KnowledgeBaseResource } from '../types/knowledgeBase'

const kb: KnowledgeBase = {
  id: 1,
  ownerId: 10,
  name: '我的知识库',
  description: null,
  memberRole: 'OWNER',
  createdAt: '2026-09-17 09:00:00',
  updatedAt: '2026-09-17 10:00:00',
}

const member: KnowledgeBaseMember = {
  userId: 11,
  username: 'bob',
  nickname: null,
  memberRole: 'EDITOR',
  createdAt: '2026-09-17 10:00:00',
}

const resource: KnowledgeBaseResource = {
  resourceId: 5,
  resourceType: 'NOTE',
  name: '笔记',
  indexStatus: 'INDEXED',
  addedAt: '2026-09-17 10:00:00',
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function ok(data: unknown) {
  return { code: 0, message: 'OK', data }
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

describe('knowledgeBases store', () => {
  it('loads the list', async () => {
    stub(ok([kb]))

    const store = useKnowledgeBasesStore()
    await store.fetchList()

    expect(store.list).toEqual([kb])
    expect(store.loading).toBe(false)
  })

  it('resets loading state when the list request fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      jsonResponse({ code: 500, message: '服务器内部错误', data: null }, 500),
    ))

    const store = useKnowledgeBasesStore()

    await expect(store.fetchList()).rejects.toThrow('服务器内部错误')
    expect(store.loading).toBe(false)
  })

  it('derives permissions from the OWNER role', async () => {
    stub(ok(kb))

    const store = useKnowledgeBasesStore()
    await store.fetchOne(1)

    expect(store.role).toBe('OWNER')
    expect(store.isOwner).toBe(true)
    expect(store.canEditResources).toBe(true)
  })

  it('grants EDITOR resource editing but not member management', async () => {
    stub(ok({ ...kb, memberRole: 'EDITOR' }))

    const store = useKnowledgeBasesStore()
    await store.fetchOne(1)

    expect(store.isOwner).toBe(false)
    expect(store.canEditResources).toBe(true)
  })

  it('grants VIEWER read-only access', async () => {
    stub(ok({ ...kb, memberRole: 'VIEWER' }))

    const store = useKnowledgeBasesStore()
    await store.fetchOne(1)

    expect(store.isOwner).toBe(false)
    expect(store.canEditResources).toBe(false)
  })

  it('creates and stores the new knowledge base', async () => {
    stub(ok(kb))

    const store = useKnowledgeBasesStore()
    const created = await store.create({ name: '我的知识库' })

    expect(created.id).toBe(1)
    expect(store.current?.id).toBe(1)
  })

  it('clears current after a delete', async () => {
    const fetchMock = stubSequence(ok(null))

    const store = useKnowledgeBasesStore()
    store.current = kb
    await store.remove(1)

    expect(fetchMock.mock.calls[0][0]).toBe('/api/knowledge-bases/1')
    expect(fetchMock.mock.calls[0][1].method).toBe('DELETE')
    expect(store.current).toBeNull()
  })

  it('reloads members after a role change', async () => {
    const fetchMock = stubSequence(ok(null), ok([{ ...member, memberRole: 'VIEWER' }]))

    const store = useKnowledgeBasesStore()
    await store.changeRole(1, 11, 'VIEWER')

    expect(fetchMock.mock.calls[0][0]).toBe('/api/knowledge-bases/1/members/11')
    expect(fetchMock.mock.calls[0][1].method).toBe('PUT')
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ memberRole: 'VIEWER' })
    expect(fetchMock.mock.calls[1][0]).toBe('/api/knowledge-bases/1/members')
    expect(store.members[0].memberRole).toBe('VIEWER')
  })

  it('reloads resources after adding one', async () => {
    const fetchMock = stubSequence(ok(null), ok([resource]))

    const store = useKnowledgeBasesStore()
    await store.addResource(1, { resourceType: 'NOTE', resourceId: 5 })

    expect(fetchMock.mock.calls[0][0]).toBe('/api/knowledge-bases/1/resources')
    expect(fetchMock.mock.calls[0][1].method).toBe('POST')
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ resourceType: 'NOTE', resourceId: 5 })
    expect(fetchMock.mock.calls[1][0]).toBe('/api/knowledge-bases/1/resources')
    expect(store.resources).toEqual([resource])
  })

  it('drops all state on reset', async () => {
    stub(ok([kb]))

    const store = useKnowledgeBasesStore()
    await store.fetchList()
    store.reset()

    expect(store.list).toEqual([])
    expect(store.current).toBeNull()
    expect(store.members).toEqual([])
    expect(store.resources).toEqual([])
  })
})
