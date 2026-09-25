export interface DocumentSummary {
  id: number
  fileName: string
  fileSize: number
  contentVersion: number
  indexStatus: string
  indexError?: string
  createdAt: string
  updatedAt: string
}

export interface Document extends DocumentSummary {
  content: string
}
