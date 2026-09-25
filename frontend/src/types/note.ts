export type IndexStatus = 'PENDING' | 'INDEXED' | 'FAILED'

/** 列表项，后端不下发正文。 */
export type NoteSummary = {
  id: number
  title: string
  isPinned: boolean
  isArchived: boolean
  indexStatus: IndexStatus
  indexError?: string
  updatedAt: string
}

export type Note = {
  id: number
  title: string
  content: string
  isPinned: boolean
  isArchived: boolean
  contentVersion: number
  indexStatus: IndexStatus
  indexError?: string
  createdAt: string
  updatedAt: string
}

export type NotePayload = {
  title: string
  content: string
}

export type NoteQuery = {
  isPinned?: boolean
  isArchived?: boolean
  page?: number
  size?: number
}
