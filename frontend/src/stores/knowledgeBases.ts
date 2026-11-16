import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { knowledgeBaseApi } from '../api/knowledgeBase'
import type {
  EditableMemberRole,
  KnowledgeBase,
  KnowledgeBaseMember,
  KnowledgeBasePayload,
  KnowledgeBaseResource,
  ResourceAddPayload,
} from '../types/knowledgeBase'

export const useKnowledgeBasesStore = defineStore('knowledgeBases', () => {
  const list = ref<KnowledgeBase[]>([])
  const current = ref<KnowledgeBase | null>(null)
  const members = ref<KnowledgeBaseMember[]>([])
  const resources = ref<KnowledgeBaseResource[]>([])
  const loading = ref(false)

  const role = computed(() => current.value?.memberRole ?? null)
  const isOwner = computed(() => role.value === 'OWNER')
  const canEditResources = computed(() => role.value === 'OWNER' || role.value === 'EDITOR')

  async function fetchList(): Promise<void> {
    loading.value = true
    try {
      list.value = await knowledgeBaseApi.list()
    } finally {
      loading.value = false
    }
  }

  async function fetchOne(id: number): Promise<KnowledgeBase> {
    current.value = await knowledgeBaseApi.detail(id)
    return current.value
  }

  async function create(payload: KnowledgeBasePayload): Promise<KnowledgeBase> {
    current.value = await knowledgeBaseApi.create(payload)
    return current.value
  }

  async function update(id: number, payload: KnowledgeBasePayload): Promise<void> {
    current.value = await knowledgeBaseApi.update(id, payload)
  }

  async function remove(id: number): Promise<void> {
    await knowledgeBaseApi.remove(id)
    if (current.value?.id === id) current.value = null
  }

  async function fetchMembers(id: number): Promise<void> {
    members.value = await knowledgeBaseApi.members(id)
  }

  async function changeRole(id: number, userId: number, memberRole: EditableMemberRole): Promise<void> {
    await knowledgeBaseApi.updateMemberRole(id, userId, memberRole)
    await fetchMembers(id)
  }

  async function removeMember(id: number, userId: number): Promise<void> {
    await knowledgeBaseApi.removeMember(id, userId)
    await fetchMembers(id)
  }

  async function fetchResources(id: number): Promise<void> {
    resources.value = await knowledgeBaseApi.resources(id)
  }

  async function addResource(id: number, payload: ResourceAddPayload): Promise<void> {
    await knowledgeBaseApi.addResource(id, payload)
    await fetchResources(id)
  }

  async function removeResource(id: number, resourceType: string, resourceId: number): Promise<void> {
    await knowledgeBaseApi.removeResource(id, resourceType, resourceId)
    await fetchResources(id)
  }

  function reset(): void {
    list.value = []
    current.value = null
    members.value = []
    resources.value = []
    loading.value = false
  }

  return {
    list,
    current,
    members,
    resources,
    loading,
    role,
    isOwner,
    canEditResources,
    fetchList,
    fetchOne,
    create,
    update,
    remove,
    fetchMembers,
    changeRole,
    removeMember,
    fetchResources,
    addResource,
    removeResource,
    reset,
  }
})
