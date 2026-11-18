<script setup lang="ts">
import type { KnowledgeBase } from '../../types/knowledgeBase'
import { MEMBER_ROLE_LABELS } from '../../utils/knowledgeBase'

defineProps<{ knowledgeBase: KnowledgeBase }>()
</script>

<template>
  <li class="item card-hover">
    <RouterLink class="link" :to="{ name: 'knowledge-base-detail', params: { id: String(knowledgeBase.id) } }">
      <span class="main">
        <strong class="name">{{ knowledgeBase.name }}</strong>
        <span v-if="knowledgeBase.description" class="desc">{{ knowledgeBase.description }}</span>
      </span>
      <span class="meta">
        <span class="badge">{{ MEMBER_ROLE_LABELS[knowledgeBase.memberRole] }}</span>
        <span class="time">{{ knowledgeBase.updatedAt }}</span>
      </span>
    </RouterLink>
  </li>
</template>

<style scoped>
.item {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg);
}
.link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  color: var(--fg);
  transition: background-color 0.18s ease;
}
.link:hover {
  background: var(--bg-soft);
}
.main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.desc {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--fg-muted);
  font-size: 13px;
}
.meta {
  flex: none;
  display: flex;
  align-items: center;
  gap: 12px;
}
.badge {
  padding: 1px 6px;
  border: 1px solid var(--border);
  border-radius: 999px;
  color: var(--fg-muted);
  font-size: 12px;
  white-space: nowrap;
}
.time {
  color: var(--fg-muted);
  font-size: 13px;
  white-space: nowrap;
}
</style>
