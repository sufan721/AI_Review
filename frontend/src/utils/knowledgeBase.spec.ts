import { describe, expect, it } from 'vitest'
import { isResourceIncluded, MEMBER_ROLE_LABELS, resourceKey } from './knowledgeBase'
import type { KnowledgeBaseResource } from '../types/knowledgeBase'

const noteResource: KnowledgeBaseResource = {
  resourceId: 5,
  resourceType: 'NOTE',
  name: '笔记',
  indexStatus: 'INDEXED',
  addedAt: '2026-09-17 10:00:00',
}

describe('resourceKey', () => {
  it('combines type and id into a composite key', () => {
    expect(resourceKey('NOTE', 5)).toBe('NOTE:5')
    expect(resourceKey('DOCUMENT', 5)).toBe('DOCUMENT:5')
  })

  it('distinguishes the same id across different types', () => {
    expect(resourceKey('NOTE', 5)).not.toBe(resourceKey('DOCUMENT', 5))
  })
})

describe('isResourceIncluded', () => {
  it('matches only when both type and id agree', () => {
    expect(isResourceIncluded([noteResource], 'NOTE', 5)).toBe(true)
    expect(isResourceIncluded([noteResource], 'DOCUMENT', 5)).toBe(false)
    expect(isResourceIncluded([noteResource], 'NOTE', 6)).toBe(false)
  })

  it('returns false for an empty list', () => {
    expect(isResourceIncluded([], 'NOTE', 5)).toBe(false)
  })
})

describe('MEMBER_ROLE_LABELS', () => {
  it('labels all three roles', () => {
    expect(MEMBER_ROLE_LABELS.OWNER).toBe('拥有者')
    expect(MEMBER_ROLE_LABELS.EDITOR).toBe('编辑')
    expect(MEMBER_ROLE_LABELS.VIEWER).toBe('只读')
  })
})
