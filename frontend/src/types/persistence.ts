/** 数据快照设置（后端 instance_setting.PERSISTENCE 的类型化表示）。 */
export type PersistenceSetting = {
  enabled: boolean
  storagePath: string
  autoBackup: boolean
  /** Spring 六段 cron：秒 分 时 日 月 周。 */
  backupCron: string
  retentionDays: number
}

export type BackupTrigger = 'MANUAL' | 'AUTO' | 'PRE_RESTORE'
export type BackupStatus = 'RUNNING' | 'SUCCESS' | 'FAILED'
export type TaskStatus = 'RUNNING' | 'SUCCESS' | 'FAILED'

/** 一次备份/恢复任务的进度；恢复任务只填 status 与 message。 */
export type PersistenceTask = {
  id: number
  kind: 'BACKUP' | 'RESTORE'
  status: TaskStatus
  completedTables: number
  totalTables: number
  message: string
}

export type BackupRecord = {
  id: number
  fileName: string
  triggerType: BackupTrigger
  status: BackupStatus
  sizeBytes: number
  checksum: string
  tableCount: number
  rowCount: number
  keepForever: boolean
  errorMessage: string | null
  startedAt: string
  finishedAt: string | null
}

export type TableStat = {
  tableName: string
  rowEstimate: number
  sizeBytes: number
}

export type PersistenceStatus = {
  enabled: boolean
  storagePath: string
  directoryWritable: boolean
  databaseSizeBytes: number
  totalRows: number
  indexChunks: number
  tables: TableStat[]
  lastBackup: BackupRecord | null
  nextRunAt: string | null
  autoBackupEnabled: boolean
  runningBackupId: number | null
}

export type BackupPage = {
  records: BackupRecord[]
  total: number
  page: number
  size: number
}

export type VerifyResult = {
  valid: boolean
  reason: string
}

export type RestoreResult = {
  restoredRows: number
  reindexScheduled: number
}
