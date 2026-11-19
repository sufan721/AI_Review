/** 附件存储后端：LOCAL_FS=服务器文件系统（默认·推荐），DATABASE=应用数据库。 */
export type AttachmentStorageMode = 'LOCAL_FS' | 'DATABASE'

/** 附件存储设置（后端 instance_setting.STORAGE 的类型化表示）。 */
export type AttachmentStorageSetting = {
  mode: AttachmentStorageMode
  /** 仅 LOCAL_FS 生效；后端返回规范化的绝对路径。 */
  localDir: string
  /** 单附件大小上限（MB），1-200。 */
  maxSizeMb: number
}

/** 管理员看到的存储用量概览。 */
export type AttachmentStorageStatus = {
  mode: AttachmentStorageMode
  localDir: string
  directoryWritable: boolean
  fsCount: number
  fsBytes: number
  dbCount: number
  dbBytes: number
}

/** 附件元数据视图（不含二进制）。 */
export type AttachmentItem = {
  id: number
  originalName: string
  contentType: string
  sizeBytes: number
  sha256: string
  storageMode: AttachmentStorageMode
  createdAt: string
}
