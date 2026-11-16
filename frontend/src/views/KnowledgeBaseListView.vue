<script setup lang="ts">
import { onMounted, ref } from 'vue'
import KnowledgeBaseItem from '../components/knowledgeBase/KnowledgeBaseItem.vue'
import { useKnowledgeBasesStore } from '../stores/knowledgeBases'

const store = useKnowledgeBasesStore()
const error = ref('')

async function load(): Promise<void> {
  error.value = ''
  try {
    await store.fetchList()
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '知识库加载失败'
  }
}

onMounted(load)
</script>

<template>
  <section class="page">
    <header class="head">
      <h1>知识库</h1>
      <RouterLink class="btn btn-primary" :to="{ name: 'knowledge-base-new' }">新建知识库</RouterLink>
    </header>

    <p v-if="error !== ''" class="form-error">{{ error }}</p>
    <p v-if="store.loading" class="muted">加载中…</p>
    <p v-else-if="store.list.length === 0" class="muted empty">还没有知识库，点击右上角新建一个。</p>
    <ul v-else class="list">
      <KnowledgeBaseItem v-for="kb in store.list" :key="kb.id" :knowledge-base="kb" />
    </ul>
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
@media (max-width: 480px) {
  .head > .btn {
    width: 100%;
    text-align: center;
  }
}
</style>
