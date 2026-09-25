<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import NoteItem from '../components/note/NoteItem.vue'
import { useNotesStore } from '../stores/notes'
import type { NoteSummary } from '../types/note'

type Scope = 'active' | 'archived'

const notes = useNotesStore()
const router = useRouter()

const scope = ref<Scope>('active')
const error = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(notes.total / (notes.query.size ?? 20))))
const currentPage = computed(() => notes.query.page ?? 1)

async function load(): Promise<void> {
  error.value = ''
  try {
    await notes.fetchList({ isArchived: scope.value === 'archived' })
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '笔记加载失败'
  }
}

onMounted(load)

async function changeScope(next: Scope): Promise<void> {
  scope.value = next
  await load()
}

async function goToPage(page: number): Promise<void> {
  if (page < 1 || page > totalPages.value) return
  error.value = ''
  try {
    await notes.fetchList({ page })
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '笔记加载失败'
  }
}

async function run(action: () => Promise<void>): Promise<void> {
  error.value = ''
  try {
    await action()
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '操作失败'
  }
}

function open(id: number): void {
  void router.push({ name: 'note-edit', params: { id: String(id) } })
}

async function remove(note: NoteSummary): Promise<void> {
  const label = note.title === '' ? '无标题' : note.title
  if (!window.confirm(`删除《${label}》？该操作不可撤销。`)) return
  await run(() => notes.remove(note.id))
}
</script>

<template>
  <section class="page">
    <header class="head">
      <h1>我的笔记</h1>
      <RouterLink class="btn btn-primary" :to="{ name: 'note-new' }">新建笔记</RouterLink>
    </header>

    <nav class="scopes">
      <button
        :class="['btn', { active: scope === 'active' }]"
        type="button"
        @click="changeScope('active')"
      >
        全部笔记
      </button>
      <button
        :class="['btn', { active: scope === 'archived' }]"
        type="button"
        @click="changeScope('archived')"
      >
        已归档
      </button>
    </nav>

    <p v-if="error !== ''" class="form-error">{{ error }}</p>

    <p v-if="notes.loading" class="muted">加载中…</p>

    <p v-else-if="notes.list.length === 0" class="muted empty">
      {{ scope === 'archived' ? '还没有归档的笔记' : '还没有笔记，点击右上角新建一篇' }}
    </p>

    <ul v-else class="list">
      <NoteItem
        v-for="note in notes.list"
        :key="note.id"
        :note="note"
        @open="open"
        @toggle-pin="(id) => run(() => notes.togglePin(id))"
        @toggle-archive="(id) => run(() => notes.toggleArchive(id))"
        @reindex="(id) => run(() => notes.reindex(id))"
        @remove="remove"
      />
    </ul>

    <footer v-if="totalPages > 1" class="pager">
      <button class="btn" type="button" :disabled="currentPage <= 1" @click="goToPage(currentPage - 1)">
        上一页
      </button>
      <span class="muted">{{ currentPage }} / {{ totalPages }}（共 {{ notes.total }} 条）</span>
      <button
        class="btn"
        type="button"
        :disabled="currentPage >= totalPages"
        @click="goToPage(currentPage + 1)"
      >
        下一页
      </button>
    </footer>
  </section>
</template>

<style scoped>
.page {
  max-width: 900px;
  margin: 0 auto;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 16px;
}

h1 {
  margin: 0;
  font-size: 22px;
}

.scopes {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.scopes .active {
  border-color: var(--accent);
  color: var(--accent);
}

.list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.muted {
  color: var(--fg-muted);
}

.empty {
  padding: 40px 0;
  text-align: center;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 20px;
}
@media (max-width: 480px) {
  .head > .btn { width: 100%; text-align: center; }
  .scopes { flex-wrap: wrap; }
}
</style>
