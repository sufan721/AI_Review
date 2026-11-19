import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { persistenceApi } from '../api/persistence'
import type {
  BackupRecord,
  PersistenceSetting,
  PersistenceStatus,
  PersistenceTask,
  RestoreResult,
  VerifyResult,
} from '../types/persistence'

/** 每 2 秒轮询一次，最多 60 次（2 分钟），避免页面挂着永不结束的定时器。 */
const POLL_INTERVAL = 2000
const POLL_LIMIT = 60

export const CRON_OPTIONS = [
  { label: '每天 03:00', value: '0 0 3 * * *' },
  { label: '每 6 小时', value: '0 0 */6 * * *' },
  { label: '每周一 03:00', value: '0 0 3 * * 1' },
  { label: '每小时', value: '0 0 * * * *' },
] as const

export const usePersistenceStore = defineStore('persistence', () => {
  const setting = ref<PersistenceSetting | null>(null)
  const status = ref<PersistenceStatus | null>(null)
  const records = ref<BackupRecord[]>([])
  const total = ref(0)
  const page = ref(1)
  const task = ref<PersistenceTask | null>(null)
  const lastRestore = ref<RestoreResult | null>(null)
  const verifyResult = ref<VerifyResult | null>(null)
  const busy = ref(false)
  let cancelled = false

  const running = computed(() => task.value?.status === 'RUNNING')

  async function loadSetting(): Promise<void> {
    setting.value = await persistenceApi.setting()
  }

  async function loadStatus(): Promise<void> {
    status.value = await persistenceApi.status()
  }

  async function loadRecords(targetPage = page.value): Promise<void> {
    const result = await persistenceApi.backups(targetPage, 20)
    records.value = result.records
    total.value = result.total
    page.value = result.page
  }

  async function loadAll(): Promise<void> {
    await Promise.all([loadSetting(), loadStatus(), loadRecords()])
  }

  async function saveSetting(payload: PersistenceSetting): Promise<void> {
    setting.value = await persistenceApi.updateSetting(payload)
    await loadStatus()
  }

  async function poll(id: number, kind: 'BACKUP' | 'RESTORE'): Promise<void> {
    cancelled = false
    for (let attempt = 0; attempt < POLL_LIMIT; attempt++) {
      if (cancelled) return
      await new Promise((resolve) => setTimeout(resolve, attempt === 0 ? 300 : POLL_INTERVAL))
      if (cancelled) return
      const current = await persistenceApi.task(id)
      task.value = current
      if (current.status !== 'RUNNING') {
        await loadAll()
        if (current.status === 'FAILED') throw new Error(current.message || '任务失败')
        if (kind === 'RESTORE') lastRestore.value = await persistenceApi.restoreResult()
        return
      }
    }
    throw new Error('任务超时，请到备份历史查看结果')
  }

  async function startBackup(): Promise<void> {
    busy.value = true
    try {
      const started = await persistenceApi.startBackup()
      await poll(started.id, 'BACKUP')
    } finally {
      busy.value = false
    }
  }

  async function startRestore(id: number): Promise<void> {
    busy.value = true
    try {
      const started = await persistenceApi.startRestore(id)
      await poll(started.taskId, 'RESTORE')
    } finally {
      busy.value = false
    }
  }

  async function verify(id: number): Promise<VerifyResult> {
    verifyResult.value = await persistenceApi.verify(id)
    return verifyResult.value
  }

  async function remove(id: number): Promise<void> {
    await persistenceApi.remove(id)
    await loadAll()
  }

  async function download(id: number, fileName: string): Promise<void> {
    await persistenceApi.download(id, fileName)
  }

  /** 组件卸载/切走分区时调用，停止轮询但保留已拿到的数据。 */
  function cancelPolling(): void {
    cancelled = true
    busy.value = false
  }

  return {
    setting, status, records, total, page, task, lastRestore, verifyResult, busy, running,
    loadAll, loadSetting, loadStatus, loadRecords, saveSetting, startBackup, startRestore,
    verify, remove, download, cancelPolling,
  }
})
