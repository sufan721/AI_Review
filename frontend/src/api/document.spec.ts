import { beforeEach, describe, expect, it, vi } from 'vitest'
import { documentApi } from './document'

describe('documentApi', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('uploads Markdown as multipart form data without a JSON content type', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ code: 0, message: 'OK', data: { id: 1 } }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await documentApi.upload(new File(['# Guide'], 'guide.md', { type: 'text/markdown' }))

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(init.method).toBe('POST')
    expect(init.body).toBeInstanceOf(FormData)
    expect(new Headers(init.headers).has('Content-Type')).toBe(false)
  })
})
