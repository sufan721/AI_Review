import { defineStore } from 'pinia'
import { ref } from 'vue'
import { noteApi } from '../api/note'
import type { Note, NotePayload, NoteQuery, NoteSummary } from '../types/note'

/** 列表默认只看未归档笔记，与需求中「归档即移出主列表」一致。 */
export const DEFAULT_QUERY: NoteQuery = { isArchived: false, page: 1, size: 20 }

export const useNotesStore = defineStore('notes', () => {
  const list = ref<NoteSummary[]>([])
  const total = ref(0)
  const query = ref<NoteQuery>({ ...DEFAULT_QUERY })
  const current = ref<Note | null>(null)
  const loading = ref(false)

  async function fetchList(changes: Partial<NoteQuery> = {}): Promise<void> {
    query.value = { ...query.value, ...changes }
    loading.value = true
    try {
      const page = await noteApi.list(query.value)
      list.value = page.records
      total.value = page.total
    } finally {
      loading.value = false
    }
  }

  async function fetchOne(id: number): Promise<Note> {
    current.value = await noteApi.detail(id)
    return current.value
  }

  async function save(id: number | null, payload: NotePayload): Promise<Note> {
    current.value = id === null ? await noteApi.create(payload) : await noteApi.update(id, payload)
    return current.value
  }

  async function remove(id: number): Promise<void> {
    await noteApi.remove(id)
    if (current.value?.id === id) current.value = null
    await fetchList()
  }

  /** 置顶与归档都会改变列表排序或过滤结果，操作后统一重新拉取。 */
  async function togglePin(id: number): Promise<void> {
    await noteApi.togglePin(id)
    await fetchList()
  }

  async function toggleArchive(id: number): Promise<void> {
    await noteApi.toggleArchive(id)
    await fetchList()
  }

  async function reindex(id: number): Promise<void> {
    await noteApi.reindex(id)
    await fetchList()
  }

  function reset(): void {
    list.value = []
    total.value = 0
    query.value = { ...DEFAULT_QUERY }
    current.value = null
  }

  return { list, total, query, current, loading, fetchList, fetchOne, save, remove, togglePin, toggleArchive, reindex, reset }
})
