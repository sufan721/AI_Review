<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useKnowledgeBasesStore } from '../stores/knowledgeBases'

const props = defineProps<{ id?: string }>()

const store = useKnowledgeBasesStore()
const router = useRouter()

const name = ref('')
const description = ref('')
const error = ref('')
const loading = ref(false)
const saving = ref(false)

const kbId = computed(() => {
  if (props.id === undefined) return null
  const parsed = Number(props.id)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
})

const isNew = computed(() => props.id === undefined)
const invalidId = computed(() => !isNew.value && kbId.value === null)

const backRoute = computed(() => {
  if (isNew.value) return { name: 'knowledge-bases' }
  return { name: 'knowledge-base-detail', params: { id: props.id as string } }
})

onMounted(async () => {
  if (isNew.value) return
  if (invalidId.value) {
    error.value = '知识库地址无效'
    return
  }
  loading.value = true
  try {
    const kb = await store.fetchOne(kbId.value as number)
    name.value = kb.name
    description.value = kb.description ?? ''
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '知识库加载失败'
  } finally {
    loading.value = false
  }
})

async function save(): Promise<void> {
  if (invalidId.value) return
  const trimmedName = name.value.trim()
  if (trimmedName === '') {
    error.value = '知识库名称不能为空'
    return
  }
  saving.value = true
  error.value = ''
  try {
    const payload = { name: trimmedName, description: description.value.trim() }
    if (isNew.value) {
      const kb = await store.create(payload)
      await router.replace({ name: 'knowledge-base-detail', params: { id: String(kb.id) } })
    } else {
      await store.update(kbId.value as number, payload)
      await router.replace({ name: 'knowledge-base-detail', params: { id: String(kbId.value) } })
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
      <h1>{{ isNew ? '新建知识库' : '编辑知识库' }}</h1>
      <RouterLink class="btn" :to="backRoute">返回</RouterLink>
    </header>

    <p v-if="error !== ''" class="form-error">{{ error }}</p>
    <p v-if="loading" class="muted">加载中…</p>

    <form v-show="!loading" @submit.prevent="save">
      <label class="field">
        名称
        <input v-model="name" type="text" maxlength="128" placeholder="知识库名称" required />
      </label>
      <label class="field">
        描述
        <textarea v-model="description" class="desc-input" maxlength="512" placeholder="可选，简要描述这个知识库" />
      </label>
      <button class="btn btn-primary" type="submit" :disabled="saving || invalidId">
        {{ saving ? '保存中…' : '保存' }}
      </button>
    </form>
  </section>
</template>

<style scoped>
.page {
  max-width: 640px;
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
.muted {
  color: var(--fg-muted);
}
.desc-input {
  min-height: 120px;
  padding: 8px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg);
  color: var(--fg);
  font-family: inherit;
  font-size: inherit;
  resize: vertical;
}
.desc-input:focus {
  outline: 2px solid var(--accent);
  outline-offset: -1px;
}
</style>
