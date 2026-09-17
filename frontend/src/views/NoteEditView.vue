<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import MarkdownRenderer from '../components/markdown/MarkdownRenderer.vue'
import { useNotesStore } from '../stores/notes'

const props = defineProps<{ id?: string }>()

const notes = useNotesStore()
const router = useRouter()

const title = ref('')
const content = ref('')
const error = ref('')
const saved = ref(false)
const loading = ref(false)
const saving = ref(false)

const id = computed(() => {
  if (props.id === undefined) return null
  const parsed = Number(props.id)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
})

const isNew = computed(() => props.id === undefined)
const invalidId = computed(() => !isNew.value && id.value === null)

onMounted(async () => {
  if (isNew.value) return
  if (invalidId.value) {
    error.value = '笔记地址无效'
    return
  }
  loading.value = true
  try {
    const note = await notes.fetchOne(id.value as number)
    title.value = note.title
    content.value = note.content
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '笔记加载失败'
  } finally {
    loading.value = false
  }
})

async function save(): Promise<void> {
  if (invalidId.value) return
  saving.value = true
  error.value = ''
  saved.value = false
  try {
    const note = await notes.save(id.value, { title: title.value, content: content.value })
    if (isNew.value) {
      await router.replace({ name: 'note-edit', params: { id: String(note.id) } })
    } else {
      saved.value = true
    }
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '保存失败'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section class="page">
    <header class="head">
      <input v-model="title" class="title" type="text" maxlength="255" placeholder="标题" />
      <div class="head-actions">
        <span v-if="saved" class="muted">已保存</span>
        <button class="btn" type="button" @click="router.push({ name: 'notes' })">返回列表</button>
        <button class="btn btn-primary" type="button" :disabled="saving || invalidId" @click="save">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </div>
    </header>

    <p v-if="error !== ''" class="form-error">{{ error }}</p>
    <p v-if="loading" class="muted">加载中…</p>

    <div v-show="!loading" class="panes">
      <div class="pane">
        <label class="pane-label" for="note-content">Markdown</label>
        <textarea
          id="note-content"
          v-model="content"
          class="editor"
          spellcheck="false"
          placeholder="支持 Markdown 语法，右侧实时预览"
        />
      </div>
      <div class="pane">
        <span class="pane-label">预览</span>
        <div class="preview">
          <MarkdownRenderer v-if="content.trim() !== ''" :content="content" />
          <p v-else class="muted">暂无内容</p>
        </div>
      </div>
    </div>

    <p class="muted hint">正文渲染已关闭原生 HTML，粘贴的 HTML 片段会按纯文本展示。</p>
  </section>
</template>

<style scoped>
.page {
  max-width: 1200px;
  margin: 0 auto;
}

.head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.title {
  flex: 1;
  padding: 8px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg);
  color: var(--fg);
  font-size: 18px;
  font-weight: 600;
}

.title:focus {
  outline: 2px solid var(--accent);
  outline-offset: -1px;
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.panes {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

@media (max-width: 900px) {
  .panes {
    grid-template-columns: 1fr;
  }
}

.pane {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.pane-label {
  color: var(--fg-muted);
  font-size: 13px;
}

.editor,
.preview {
  height: 60vh;
  min-height: 320px;
  padding: 12px 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg);
  overflow-y: auto;
}

.editor {
  resize: vertical;
  font-family: ui-monospace, "SFMono-Regular", Consolas, monospace;
  line-height: 1.7;
}

.editor:focus {
  outline: 2px solid var(--accent);
  outline-offset: -1px;
}

.muted {
  color: var(--fg-muted);
}

.hint {
  margin-top: 12px;
  font-size: 13px;
}
</style>
