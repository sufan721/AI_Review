<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { documentApi } from '../api/document'
import { noteApi } from '../api/note'
import ResourceItem from '../components/knowledgeBase/ResourceItem.vue'
import { useAuthStore } from '../stores/auth'
import { useKnowledgeBasesStore } from '../stores/knowledgeBases'
import type { KnowledgeBaseMember, KnowledgeBaseResource } from '../types/knowledgeBase'
import type { DocumentSummary } from '../types/document'
import type { NoteSummary } from '../types/note'
import { isResourceIncluded, MEMBER_ROLE_LABELS, resourceKey } from '../utils/knowledgeBase'

const props = defineProps<{ id?: string }>()

const store = useKnowledgeBasesStore()
const auth = useAuthStore()
const router = useRouter()

const error = ref('')
const loading = ref(false)
const saving = ref(false)

const pickerOpen = ref(false)
const pickerLoading = ref(false)
const noteCandidates = ref<NoteSummary[]>([])
const documentCandidates = ref<DocumentSummary[]>([])

const kbId = computed(() => {
  if (props.id === undefined) return null
  const parsed = Number(props.id)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
})

const invalidId = computed(() => kbId.value === null)
const currentUserId = computed(() => auth.user?.id ?? null)

async function load(): Promise<void> {
  if (invalidId.value) {
    error.value = '知识库地址无效'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const id = kbId.value as number
    await Promise.all([store.fetchOne(id), store.fetchMembers(id), store.fetchResources(id)])
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '知识库加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function onDelete(): Promise<void> {
  if (invalidId.value || store.current === null) return
  if (!window.confirm(`删除知识库「${store.current.name}」？该操作不可撤销。`)) return
  saving.value = true
  error.value = ''
  try {
    await store.remove(kbId.value as number)
    await router.replace({ name: 'knowledge-bases' })
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '删除失败'
  } finally {
    saving.value = false
  }
}

async function openPicker(): Promise<void> {
  pickerOpen.value = !pickerOpen.value
  if (!pickerOpen.value) return
  if (noteCandidates.value.length === 0 && documentCandidates.value.length === 0) {
    pickerLoading.value = true
    error.value = ''
    try {
      const [notesPage, docsPage] = await Promise.all([noteApi.list({ size: 100 }), documentApi.list(1, 100)])
      noteCandidates.value = notesPage.records
      documentCandidates.value = docsPage.records
    } catch (caught) {
      error.value = caught instanceof Error ? caught.message : '资料加载失败'
      pickerOpen.value = false
    } finally {
      pickerLoading.value = false
    }
  }
}

function included(resourceType: 'NOTE' | 'DOCUMENT', resourceId: number): boolean {
  return isResourceIncluded(store.resources, resourceType, resourceId)
}

async function addCandidate(resourceType: 'NOTE' | 'DOCUMENT', resourceId: number): Promise<void> {
  error.value = ''
  try {
    await store.addResource(kbId.value as number, { resourceType, resourceId })
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '关联失败'
  }
}

async function onRemoveResource(resource: KnowledgeBaseResource): Promise<void> {
  error.value = ''
  try {
    await store.removeResource(kbId.value as number, resource.resourceType, resource.resourceId)
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '移除失败'
  }
}

async function onRoleChange(member: KnowledgeBaseMember, event: Event): Promise<void> {
  const value = (event.target as HTMLSelectElement).value
  if (value !== 'EDITOR' && value !== 'VIEWER') return
  error.value = ''
  try {
    await store.changeRole(kbId.value as number, member.userId, value)
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '角色修改失败'
    try {
      await store.fetchMembers(kbId.value as number)
    } catch {
      // 回滚失败时保留原错误信息
    }
  }
}

async function onRemoveMember(member: KnowledgeBaseMember): Promise<void> {
  const label = member.nickname ?? member.username ?? String(member.userId)
  if (!window.confirm(`移除成员「${label}」？`)) return
  error.value = ''
  try {
    await store.removeMember(kbId.value as number, member.userId)
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '移除失败'
  }
}
</script>

<template>
  <section class="page">
    <p v-if="error !== ''" class="form-error">{{ error }}</p>
    <p v-if="loading" class="muted">加载中…</p>

    <template v-if="!loading && store.current">
      <header class="head">
        <div class="head-main">
          <h1>{{ store.current.name }}</h1>
          <span class="badge">{{ MEMBER_ROLE_LABELS[store.current.memberRole] }}</span>
        </div>
        <div class="head-actions">
          <RouterLink class="btn" :to="{ name: 'knowledge-bases' }">返回列表</RouterLink>
          <RouterLink
            v-if="store.isOwner"
            class="btn"
            :to="{ name: 'knowledge-base-edit', params: { id: String(kbId) } }"
          >
            编辑
          </RouterLink>
          <button v-if="store.isOwner" class="btn danger" type="button" :disabled="saving" @click="onDelete">
            {{ saving ? '删除中…' : '删除' }}
          </button>
        </div>
      </header>

      <p v-if="store.current.description" class="desc">{{ store.current.description }}</p>

      <div class="section">
        <div class="section-head">
          <h2>成员</h2>
          <span class="muted">添加成员待用户查询接口提供后开放</span>
        </div>
        <p v-if="store.members.length === 0" class="muted empty">暂无成员。</p>
        <ul v-else class="members">
          <li v-for="member in store.members" :key="member.userId" class="member card-hover">
            <div class="member-info">
              <strong>{{ member.nickname ?? member.username ?? `用户 ${member.userId}` }}</strong>
              <span v-if="member.username && member.nickname" class="muted">{{ member.username }}</span>
            </div>
            <div class="member-actions">
              <span v-if="!store.isOwner || member.memberRole === 'OWNER'" class="badge">
                {{ MEMBER_ROLE_LABELS[member.memberRole] }}
              </span>
              <select v-else :value="member.memberRole" class="role-select" @change="onRoleChange(member, $event)">
                <option value="EDITOR">编辑</option>
                <option value="VIEWER">只读</option>
              </select>
              <button
                v-if="store.isOwner && member.userId !== currentUserId"
                class="btn danger"
                type="button"
                @click="onRemoveMember(member)"
              >
                移除
              </button>
            </div>
          </li>
        </ul>
      </div>

      <div class="section">
        <div class="section-head">
          <h2>资料</h2>
          <button v-if="store.canEditResources" class="btn" type="button" @click="openPicker">
            {{ pickerOpen ? '收起' : '关联资料' }}
          </button>
        </div>

        <div v-if="pickerOpen" class="picker">
          <p v-if="pickerLoading" class="muted">加载中…</p>
          <template v-else>
            <div class="picker-group">
              <h3>我的笔记</h3>
              <p v-if="noteCandidates.length === 0" class="muted">暂无笔记。</p>
              <ul v-else class="candidates">
                <li v-for="note in noteCandidates" :key="note.id" class="candidate card-hover">
                  <span class="candidate-name">{{ note.title === '' ? '无标题' : note.title }}</span>
                  <button
                    class="btn"
                    type="button"
                    :disabled="included('NOTE', note.id)"
                    @click="addCandidate('NOTE', note.id)"
                  >
                    {{ included('NOTE', note.id) ? '已关联' : '关联' }}
                  </button>
                </li>
              </ul>
            </div>
            <div class="picker-group">
              <h3>我的文件</h3>
              <p v-if="documentCandidates.length === 0" class="muted">暂无文件。</p>
              <ul v-else class="candidates">
                <li v-for="document in documentCandidates" :key="document.id" class="candidate card-hover">
                  <span class="candidate-name">{{ document.fileName }}</span>
                  <button
                    class="btn"
                    type="button"
                    :disabled="included('DOCUMENT', document.id)"
                    @click="addCandidate('DOCUMENT', document.id)"
                  >
                    {{ included('DOCUMENT', document.id) ? '已关联' : '关联' }}
                  </button>
                </li>
              </ul>
            </div>
          </template>
        </div>

        <p v-if="store.resources.length === 0" class="muted empty">还没有关联资料。</p>
        <ul v-else class="list">
          <ResourceItem
            v-for="resource in store.resources"
            :key="resourceKey(resource.resourceType, resource.resourceId)"
            :resource="resource"
            :can-remove="store.canEditResources"
            @remove="onRemoveResource"
          />
        </ul>
      </div>
    </template>
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
  margin-bottom: 8px;
}
.head-main {
  display: flex;
  align-items: center;
  gap: 12px;
}
h1 {
  margin: 0;
  font-size: 22px;
}
h2 {
  margin: 0;
  font-size: 17px;
}
h3 {
  margin: 0 0 8px;
  font-size: 14px;
  color: var(--fg-muted);
}
.head-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.badge {
  padding: 1px 8px;
  border: 1px solid var(--border);
  border-radius: 999px;
  color: var(--fg-muted);
  font-size: 12px;
  white-space: nowrap;
}
.desc {
  color: var(--fg-muted);
  margin: 0 0 20px;
}
.section {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--border);
}
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 12px;
}
.muted {
  color: var(--fg-muted);
}
.empty {
  padding: 24px 0;
  text-align: center;
}
.danger {
  color: var(--danger);
  padding: 4px 10px;
  font-size: 13px;
}
.members,
.list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.member {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg);
}
.member-info {
  min-width: 0;
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
}
.member-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.role-select {
  padding: 4px 8px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg);
  color: var(--fg);
  font-family: inherit;
  font-size: 13px;
  outline: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.role-select:focus {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 16%, transparent);
}
.picker {
  margin-bottom: 16px;
  padding: 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-soft);
}
.picker-group + .picker-group {
  margin-top: 16px;
}
.candidates {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.candidate {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 10px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg);
}
.candidate-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.candidate .btn {
  padding: 4px 12px;
  font-size: 13px;
}
.candidate .btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
@media (max-width: 640px) {
  .head {
    align-items: flex-start;
    flex-direction: column;
  }
  .head-actions {
    width: 100%;
  }
}
</style>
