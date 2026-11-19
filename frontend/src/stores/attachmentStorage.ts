import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { attachmentStorageApi } from '../api/attachmentStorage'
import type {
  AttachmentItem,
  AttachmentStorageSetting,
  AttachmentStorageStatus,
} from '../types/attachmentStorage'

/** 附件存储面板状态。错误由组件层 run() 统一捕获，store 只负责 busy 复位。 */
export const useAttachmentStorageStore = defineStore('attachmentStorage', () => {
  const setting = ref<AttachmentStorageSetting | null>(null)
  const status = ref<AttachmentStorageStatus | null>(null)
  const items = ref<AttachmentItem[]>([])
  const busy = ref(false)

  const mode = computed(() => setting.value?.mode ?? 'LOCAL_FS')

  async function loadAll(): Promise<void> {
    const [nextSetting, nextStatus, nextItems] = await Promise.all([
      attachmentStorageApi.setting(),
      attachmentStorageApi.status(),
      attachmentStorageApi.list(),
    ])
    setting.value = nextSetting
    status.value = nextStatus
    items.value = nextItems
  }

  /** 保存配置；返回后端规范化后的值由调用方回填表单。 */
  async function saveSetting(payload: AttachmentStorageSetting): Promise<AttachmentStorageSetting> {
    busy.value = true
    try {
      const saved = await attachmentStorageApi.updateSetting(payload)
      setting.value = saved
      status.value = await attachmentStorageApi.status()
      return saved
    } finally {
      busy.value = false
    }
  }

  async function upload(file: File): Promise<AttachmentItem> {
    const created = await attachmentStorageApi.upload(file)
    items.value = [created, ...items.value]
    return created
  }

  async function remove(id: number): Promise<void> {
    await attachmentStorageApi.remove(id)
    items.value = items.value.filter((item) => item.id !== id)
  }

  async function download(id: number, fileName: string): Promise<void> {
    await attachmentStorageApi.download(id, fileName)
  }

  return {
    setting, status, items, busy, mode,
    loadAll, saveSetting, upload, remove, download,
  }
})
