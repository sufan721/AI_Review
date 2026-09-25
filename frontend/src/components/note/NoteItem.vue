<script setup lang="ts">
import type { NoteSummary } from '../../types/note'

defineProps<{ note: NoteSummary }>()

const emit = defineEmits<{
  open: [id: number]
  togglePin: [id: number]
  toggleArchive: [id: number]
  remove: [note: NoteSummary]
  reindex: [id: number]
}>()
</script>

<template>
  <li class="item">
    <div class="main-wrap">
      <button class="main" type="button" @click="emit('open', note.id)">
        <span class="title">
          <span v-if="note.isPinned" class="badge badge-pin">置顶</span>
          <span v-if="note.isArchived" class="badge">已归档</span>
          <span v-if="note.indexStatus === 'FAILED'" class="badge badge-failed">索引失败</span>
          <span :class="['text', { untitled: note.title === '' }]">{{ note.title === '' ? '无标题' : note.title }}</span>
        </span>
        <span class="time">{{ note.updatedAt }}</span>
      </button>
      <p v-if="note.indexStatus === 'FAILED' && note.indexError" class="index-error">{{ note.indexError }}</p>
    </div>
    <div class="actions">
      <button v-if="note.indexStatus === 'FAILED'" class="btn" type="button" @click="emit('reindex', note.id)">
        重试索引
      </button>
      <button class="btn" type="button" @click="emit('togglePin', note.id)">
        {{ note.isPinned ? '取消置顶' : '置顶' }}
      </button>
      <button class="btn" type="button" @click="emit('toggleArchive', note.id)">
        {{ note.isArchived ? '取消归档' : '归档' }}
      </button>
      <button class="btn danger" type="button" @click="emit('remove', note)">删除</button>
    </div>
  </li>
</template>

<style scoped>
.item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg);
}

.main-wrap {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0;
  border: none;
  background: none;
  color: inherit;
  text-align: left;
}

.title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.untitled {
  color: var(--fg-muted);
}

.badge {
  flex: none;
  padding: 1px 6px;
  border: 1px solid var(--border);
  border-radius: 999px;
  color: var(--fg-muted);
  font-size: 12px;
}

.badge-pin {
  border-color: var(--accent);
  color: var(--accent);
}

.badge-failed {
  border-color: var(--danger);
  color: var(--danger);
}

.index-error {
  margin: 0;
  color: var(--danger);
  font-size: 12px;
  word-break: break-all;
}

.time {
  flex: none;
  color: var(--fg-muted);
  font-size: 13px;
}

.actions {
  display: flex;
  gap: 6px;
}

.actions .btn {
  padding: 4px 10px;
  font-size: 13px;
}

.danger {
  color: var(--danger);
}
</style>
