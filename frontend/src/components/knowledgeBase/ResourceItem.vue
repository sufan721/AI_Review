<script setup lang="ts">
import type { KnowledgeBaseResource } from '../../types/knowledgeBase'

defineProps<{ resource: KnowledgeBaseResource; canRemove: boolean }>()

const emit = defineEmits<{ remove: [resource: KnowledgeBaseResource] }>()
</script>

<template>
  <li class="item">
    <div class="main">
      <span class="name">{{ resource.name ?? '（无标题）' }}</span>
      <span :class="['badge', resource.resourceType === 'NOTE' ? 'badge-note' : 'badge-doc']">
        {{ resource.resourceType === 'NOTE' ? '笔记' : '文件' }}
      </span>
      <span :class="['badge', { 'badge-failed': resource.indexStatus === 'FAILED' }]">{{ resource.indexStatus }}</span>
    </div>
    <button v-if="canRemove" class="btn danger" type="button" @click="emit('remove', resource)">移除</button>
  </li>
</template>

<style scoped>
.item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg);
}
.main {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.badge {
  flex: none;
  padding: 1px 6px;
  border: 1px solid var(--border);
  border-radius: 999px;
  color: var(--fg-muted);
  font-size: 12px;
}
.badge-note {
  border-color: var(--accent);
  color: var(--accent);
}
.badge-doc {
  border-color: var(--fg-muted);
  color: var(--fg-muted);
}
.badge-failed {
  border-color: var(--danger);
  color: var(--danger);
}
.danger {
  color: var(--danger);
  padding: 4px 10px;
  font-size: 13px;
}
</style>
