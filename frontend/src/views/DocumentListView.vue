<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { documentApi } from '../api/document'
import type { Document, DocumentSummary } from '../types/document'

const documents = ref<DocumentSummary[]>([])
const selected = ref<Document | null>(null)
const error = ref('')
const loading = ref(false)
const input = ref<HTMLInputElement | null>(null)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    documents.value = (await documentApi.list()).records
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '文件加载失败'
  } finally {
    loading.value = false
  }
}

async function upload(event: Event): Promise<void> {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  error.value = ''
  try {
    await documentApi.upload(file)
    await load()
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '文件上传失败'
  } finally {
    if (input.value) input.value.value = ''
  }
}

async function openDocument(document: DocumentSummary): Promise<void> {
  try {
    selected.value = await documentApi.detail(document.id)
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '文件读取失败'
  }
}

async function remove(document: DocumentSummary): Promise<void> {
  if (!window.confirm(`删除“${document.fileName}”？`)) return
  try {
    await documentApi.remove(document.id)
    if (selected.value?.id === document.id) selected.value = null
    await load()
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '文件删除失败'
  }
}

async function reindex(document: DocumentSummary): Promise<void> {
  error.value = ''
  try {
    await documentApi.reindex(document.id)
    await load()
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '重试索引失败'
  }
}

function formatSize(size: number): string {
  return size < 1024 ? `${size} B` : `${(size / 1024).toFixed(1)} KB`
}

onMounted(load)
</script>

<template>
  <section class="page">
    <header class="head">
      <div>
        <h1>Markdown 文件</h1>
        <p class="muted">上传并管理可用于后续知识库索引的 Markdown 资料。</p>
      </div>
      <label class="btn btn-primary upload">
        上传 .md
        <input ref="input" type="file" accept=".md,text/markdown" @change="upload" />
      </label>
    </header>
    <p v-if="error !== ''" class="form-error">{{ error }}</p>
    <p v-if="loading" class="muted">加载中…</p>
    <p v-else-if="documents.length === 0" class="muted empty">还没有上传 Markdown 文件。</p>
    <div v-else class="workspace">
      <ul class="list">
        <li v-for="document in documents" :key="document.id" :class="['item', { selected: selected?.id === document.id }]">
          <button class="file" type="button" @click="openDocument(document)">
            <strong>{{ document.fileName }}</strong>
            <span>{{ formatSize(document.fileSize) }} · {{ document.indexStatus }}</span>
            <span v-if="document.indexStatus === 'FAILED' && document.indexError" class="fail-reason" :title="document.indexError">
              {{ document.indexError }}
            </span>
          </button>
          <button v-if="document.indexStatus === 'FAILED'" class="btn" type="button" @click="reindex(document)">
            重试索引
          </button>
          <button class="btn danger" type="button" @click="remove(document)">删除</button>
        </li>
      </ul>
      <article v-if="selected" class="preview"><h2>{{ selected.fileName }}</h2><pre>{{ selected.content }}</pre></article>
      <p v-else class="muted preview-empty">选择左侧文件查看原文。</p>
    </div>
  </section>
</template>

<style scoped>
.page { max-width: 1000px; margin: 0 auto; }
.head { display: flex; justify-content: space-between; align-items: center; gap: 20px; margin-bottom: 20px; }
h1 { margin: 0; font-size: 22px; } h2 { margin: 0 0 12px; font-size: 18px; }
.muted { color: var(--fg-muted); margin: 4px 0; }
.upload { position: relative; overflow: hidden; } .upload input { position: absolute; inset: 0; opacity: 0; cursor: pointer; }
.workspace { display: grid; grid-template-columns: 320px 1fr; gap: 16px; min-height: 440px; }
.list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 8px; }
.item { display: flex; gap: 8px; align-items: stretch; padding: 8px; border: 1px solid var(--border); border-radius: var(--radius); }
.item.selected { border-color: var(--accent); background: var(--bg-soft); }
.file { flex: 1; min-width: 0; border: 0; background: transparent; text-align: left; color: var(--fg); padding: 4px; }
.file strong, .file span { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.file span { color: var(--fg-muted); font-size: 12px; margin-top: 2px; } .danger { color: var(--danger); padding: 4px 8px; }
.file .fail-reason { color: var(--danger); }
.preview { border: 1px solid var(--border); border-radius: var(--radius); padding: 18px; overflow: auto; background: var(--bg-soft); }
pre { white-space: pre-wrap; font: inherit; line-height: 1.6; margin: 0; } .preview-empty, .empty { padding: 48px 0; text-align: center; }
@media (max-width: 720px) { .workspace { grid-template-columns: 1fr; } .head { align-items: flex-start; flex-direction: column; } }
</style>
