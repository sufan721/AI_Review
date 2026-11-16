import { afterEach, describe, expect, it, vi } from 'vitest'
import { knowledgeBaseApi } from './knowledgeBase'
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

describe('knowledgeBaseApi', () => {
  it('lists knowledge bases via GET', async () => {
    const fetchMock = mockFetch()

    await knowledgeBaseApi.list()

    expect(fetchMock.mock.calls[0][0]).toBe('/api/knowledge-bases')
    expect(fetchMock.mock.calls[0][1].method).toBeUndefined()
  })

  it('creates a knowledge base with a JSON body', async () => {
    const fetchMock = mockFetch()

    await knowledgeBaseApi.create({ name: '我的知识库', description: '描述' })

    const [path, init] = fetchMock.mock.calls[0]
    expect(path).toBe('/api/knowledge-bases')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body)).toEqual({ name: '我的知识库', description: '描述' })
  })

  it('fetches detail, updates and deletes by id', async () => {
    const fetchMock = mockFetch()

    await knowledgeBaseApi.detail(7)
    await knowledgeBaseApi.update(7, { name: '新名字' })
    await knowledgeBaseApi.remove(7)

    expect(fetchMock.mock.calls[0][0]).toBe('/api/knowledge-bases/7')
    expect(fetchMock.mock.calls[1][0]).toBe('/api/knowledge-bases/7')
    expect(fetchMock.mock.calls[1][1].method).toBe('PUT')
    expect(fetchMock.mock.calls[2][0]).toBe('/api/knowledge-bases/7')
    expect(fetchMock.mock.calls[2][1].method).toBe('DELETE')
  })

  it('lists members and updates a member role', async () => {
    const fetchMock = mockFetch()

    await knowledgeBaseApi.members(7)
    await knowledgeBaseApi.updateMemberRole(7, 3, 'EDITOR')

    expect(fetchMock.mock.calls[0][0]).toBe('/api/knowledge-bases/7/members')
    expect(fetchMock.mock.calls[1][0]).toBe('/api/knowledge-bases/7/members/3')
    expect(fetchMock.mock.calls[1][1].method).toBe('PUT')
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({ memberRole: 'EDITOR' })
  })

  it('removes a member by userId', async () => {
    const fetchMock = mockFetch()

    await knowledgeBaseApi.removeMember(7, 3)

    expect(fetchMock.mock.calls[0][0]).toBe('/api/knowledge-bases/7/members/3')
    expect(fetchMock.mock.calls[0][1].method).toBe('DELETE')
  })

  it('lists resources and adds one with type and id', async () => {
    const fetchMock = mockFetch()

    await knowledgeBaseApi.resources(7)
    await knowledgeBaseApi.addResource(7, { resourceType: 'NOTE', resourceId: 5 })

    expect(fetchMock.mock.calls[0][0]).toBe('/api/knowledge-bases/7/resources')
    expect(fetchMock.mock.calls[1][0]).toBe('/api/knowledge-bases/7/resources')
    expect(fetchMock.mock.calls[1][1].method).toBe('POST')
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({ resourceType: 'NOTE', resourceId: 5 })
  })

  it('removes a resource by composite type and id', async () => {
    const fetchMock = mockFetch()

    await knowledgeBaseApi.removeResource(7, 'DOCUMENT', 9)

    expect(fetchMock.mock.calls[0][0]).toBe('/api/knowledge-bases/7/resources/DOCUMENT/9')
    expect(fetchMock.mock.calls[0][1].method).toBe('DELETE')
  })
})
