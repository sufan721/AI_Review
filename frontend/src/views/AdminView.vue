<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { adminApi, identityProviderApi, instanceSettingApi } from '../api/admin'
import { useAuthStore } from '../stores/auth'
import { CRON_OPTIONS, usePersistenceStore } from '../stores/persistence'
import { useAttachmentStorageStore } from '../stores/attachmentStorage'
import type { BackupRecord, PersistenceSetting } from '../types/persistence'
import type {
  AttachmentItem,
  AttachmentStorageSetting,
} from '../types/attachmentStorage'
import type {
  AdminUser,
  GeneralSetting,
  IdentityProvider,
  IdentityProviderPayload,
  InstanceSetting,
  RagConfig,
} from '../types/admin'

type Section = 'users' | 'policies' | 'rag' | 'persistence' | 'attachments' | 'providers' | 'settings'
type RoleChoice = 'ROLE_ADMIN' | 'ROLE_USER'

const SECTION_IDS: Section[] = ['users', 'policies', 'rag', 'persistence', 'attachments', 'providers', 'settings']

const route = useRoute()
const auth = useAuthStore()
const persistence = usePersistenceStore()
const attachmentStorage = useAttachmentStorageStore()

// 分区由左侧全站侧边栏的链接驱动（/admin?section=xxx），页面内不再放第二层侧栏
const section = computed<Section>(() => {
  const value = route.query.section
  return typeof value === 'string' && SECTION_IDS.includes(value as Section)
    ? value as Section
    : 'users'
})

/** 当前登录用户：禁止对自己降权 / 禁用 / 删除，避免把实例锁在外面。 */
const currentUserId = computed(() => auth.user?.id ?? null)
function isSelf(user: AdminUser): boolean {
  return user.id === currentUserId.value
}

/** 初始管理员（首位注册账号）：任何管理员都不能更新或删除，只能本人维护自己。 */
function isInitialAdmin(user: AdminUser): boolean {
  return user.initialAdmin === true
}

const sectionTitles: Record<Section, { title: string; sub: string }> = {
  users: { title: '成员管理', sub: '管理实例成员，调整角色、重置密码与用户名' },
  policies: { title: '访问与策略', sub: '控制该实例的注册、登录、资料编辑和日历默认行为' },
  rag: { title: 'RAG 系统', sub: '配置检索增强生成服务的 API 接入' },
  persistence: { title: '数据持久化', sub: '管理数据快照的目录、周期、历史与恢复' },
  attachments: { title: '附件存储', sub: '选择新附件的保存位置：本地文件系统或应用数据库' },
  providers: { title: '身份源', sub: '管理 OIDC / SSO 身份提供商' },
  settings: { title: '原始设置', sub: '实例设置键值表，同名 key 会被覆盖' },
}

const error = ref('')
const notice = ref('')

async function run(action: () => Promise<void>): Promise<void> {
  error.value = ''
  notice.value = ''
  try {
    await action()
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '操作失败'
  }
}

function loadSection(next: Section): void {
  error.value = ''
  notice.value = ''
  if (next !== 'persistence') persistence.cancelPolling()
  if (next === 'users') void loadUsers()
  else if (next === 'policies') void loadGeneral()
  else if (next === 'rag') void loadRag()
  else if (next === 'persistence') void loadPersistencePanel()
  else if (next === 'attachments') void loadAttachmentStoragePanel()
  else if (next === 'providers') void loadProviders()
  else void loadSettings()
}

/**
 * 分区切换时重新拉取数据；同一组件复用不会重新挂载。
 * 注意：首次加载放到 onMounted 而非 watch 的 immediate 回调——immediate 会在 setup
 * 阶段同步触发 loadSection，此时下方用 const 声明的 users/general/rag 等 ref 尚在
 * 暂时性死区，访问会抛 "Cannot access 'x' before initialization"。
 */
watch(section, loadSection)

// ---- 成员管理 ----
const users = ref<AdminUser[]>([])
const editingUser = ref<AdminUser | null>(null)
const savingEdit = ref(false)
const editForm = ref<{ role: RoleChoice; username: string; password: string }>({
  role: 'ROLE_USER', username: '', password: '',
})
const creating = ref(false)
const savingCreate = ref(false)
const createForm = ref({ username: '', password: '' })

async function loadUsers(): Promise<void> {
  await run(async () => { users.value = await adminApi.users() })
}

function openCreate(): void {
  error.value = ''
  notice.value = ''
  createForm.value = { username: '', password: '' }
  creating.value = true
}

function closeCreate(): void {
  creating.value = false
  error.value = ''
}

async function saveCreate(): Promise<void> {
  if (savingCreate.value) return
  const username = createForm.value.username.trim()
  const password = createForm.value.password
  if (username.length < 3 || username.length > 32) { error.value = '用户名长度需在 3-32 之间'; return }
  if (password.length < 6 || password.length > 64) { error.value = '密码长度需在 6-64 之间'; return }
  savingCreate.value = true
  error.value = ''
  try {
    await adminApi.createUser({ username, password })
    creating.value = false
    await loadUsers()
    notice.value = `成员「${username}」已创建`
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '创建失败'
  } finally {
    savingCreate.value = false
  }
}

function isAdmin(user: AdminUser): boolean {
  return user.roles.includes('ROLE_ADMIN')
}

function userStatus(status: number): string {
  return status === 1 ? '启用' : '禁用'
}

function openEdit(user: AdminUser): void {
  if (isInitialAdmin(user)) {
    error.value = '初始管理员不可被其他管理员修改，只能由本人维护自己的资料与密码'
    return
  }
  if (isSelf(user)) {
    error.value = '不能通过成员管理修改自己的账号，请前往个人资料页'
    return
  }
  error.value = ''
  editingUser.value = user
  editForm.value = {
    role: isAdmin(user) ? 'ROLE_ADMIN' : 'ROLE_USER',
    username: user.username,
    password: '',
  }
}

function closeEdit(): void {
  editingUser.value = null
  error.value = ''
}

async function saveEdit(): Promise<void> {
  const target = editingUser.value
  if (target === null || savingEdit.value) return
  if (isInitialAdmin(target)) {
    error.value = '初始管理员不可被其他管理员修改，只能由本人维护自己的资料与密码'
    return
  }
  if (isSelf(target)) {
    error.value = '不能通过成员管理修改自己的账号，请前往个人资料页'
    return
  }
  const nextUsername = editForm.value.username.trim()
  const nextPassword = editForm.value.password
  if (nextUsername === '') { error.value = '用户名不能为空'; return }
  if (nextPassword !== '' && (nextPassword.length < 6 || nextPassword.length > 64)) {
    error.value = '密码长度需在 6-64 之间'; return
  }
  savingEdit.value = true
  error.value = ''
  try {
    if (nextUsername !== target.username) {
      await adminApi.changeUsername(target.id, nextUsername)
    }
    if (nextPassword !== '') {
      await adminApi.changePassword(target.id, nextPassword)
    }
    const wasAdmin = isAdmin(target)
    if (editForm.value.role === 'ROLE_ADMIN' && !wasAdmin) await adminApi.grantAdmin(target.id)
    else if (editForm.value.role === 'ROLE_USER' && wasAdmin) await adminApi.revokeAdmin(target.id)
    editingUser.value = null
    await loadUsers()
    notice.value = '成员信息已更新'
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '更新失败'
  } finally {
    savingEdit.value = false
  }
}

async function removeUser(user: AdminUser): Promise<void> {
  if (isInitialAdmin(user)) {
    error.value = '初始管理员不可删除，该账号是实例的原始管理员'
    return
  }
  if (isSelf(user)) {
    error.value = '不能删除自己的账号，请由其他管理员操作'
    return
  }
  if (!window.confirm(`确定删除成员「${user.nickname || user.username}」吗？此操作不可撤销。`)) return
  await run(async () => {
    await adminApi.deleteUser(user.id)
    await loadUsers()
    notice.value = '成员已删除'
  })
}

// ---- 访问与策略开关 ----
const general = ref<GeneralSetting>({
  disallowUserRegistration: false,
  disallowPasswordAuth: false,
  forbidChangeUsername: false,
  forbidChangeNickname: false,
})
const policyFields: { key: keyof GeneralSetting; name: string; hint: string }[] = [
  { key: 'disallowUserRegistration', name: '禁用用户注册', hint: '阻止新用户从注册页面创建账号。' },
  { key: 'disallowPasswordAuth', name: '禁用密码登录', hint: '要求用户通过身份提供商登录，而不是使用密码登录。' },
  { key: 'forbidChangeUsername', name: '禁止修改用户名', hint: '阻止用户修改登录用户名。管理员代改不受此限制。' },
  { key: 'forbidChangeNickname', name: '禁止修改用户昵称', hint: '阻止用户修改显示昵称。管理员代改不受此限制。' },
]

async function loadGeneral(): Promise<void> {
  await run(async () => { general.value = await instanceSettingApi.general() })
}

async function saveGeneral(): Promise<void> {
  await run(async () => {
    general.value = await instanceSettingApi.updateGeneral(general.value)
    notice.value = '访问与策略已保存'
  })
}

// ---- RAG 配置（仍走通用 KV，见 RAG_API）----
const rag = ref<RagConfig>({ enabled: false, baseUrl: '', apiKey: '', model: '' })

async function loadRag(): Promise<void> {
  await run(async () => {
    const all = await instanceSettingApi.list()
    const ragSetting = all.find((item) => item.name === 'RAG_API')
    if (ragSetting?.value) {
      try { rag.value = { ...rag.value, ...JSON.parse(ragSetting.value) as Partial<RagConfig> } } catch { /* 忽略损坏的配置 */ }
    }
  })
}

async function saveRag(): Promise<void> {
  await run(async () => {
    await instanceSettingApi.upsert('RAG_API', {
      value: JSON.stringify(rag.value), description: 'RAG 系统 API 配置',
    })
    notice.value = 'RAG 配置已保存'
  })
}

// ---- 数据快照 ----
const settingDraft = ref<PersistenceSetting>({
  enabled: true, storagePath: '', autoBackup: false, backupCron: '0 0 3 * * *', retentionDays: 30,
})
const restoreTarget = ref<BackupRecord | null>(null)
const restoreConfirm = ref('')
const restoreOpen = ref(false)

async function loadPersistencePanel(): Promise<void> {
  persistence.cancelPolling()
  await run(async () => {
    await persistence.loadAll()
    if (persistence.setting !== null) settingDraft.value = { ...persistence.setting }
  })
}

async function savePersistenceSetting(): Promise<void> {
  await run(async () => {
    await persistence.saveSetting({ ...settingDraft.value })
    if (persistence.setting !== null) settingDraft.value = { ...persistence.setting }
    notice.value = '快照策略已保存'
  })
}

const customCron = computed(() =>
  !CRON_OPTIONS.some((option) => option.value === settingDraft.value.backupCron))

async function doBackup(): Promise<void> {
  await run(async () => {
    await persistence.startBackup()
    notice.value = '备份完成，已加入历史列表'
  })
}

async function doVerify(id: number): Promise<void> {
  await run(async () => {
    const result = await persistence.verify(id)
    notice.value = result.valid ? '归档校验通过' : `归档校验失败：${result.reason ?? ''}`
  })
}

async function doRemove(record: BackupRecord): Promise<void> {
  if (!window.confirm(`删除归档「${record.fileName}」？该操作不可撤销。`)) return
  await run(async () => {
    await persistence.remove(record.id)
    notice.value = '归档已删除'
  })
}

async function doDownload(record: BackupRecord): Promise<void> {
  await run(async () => { await persistence.download(record.id, record.fileName) })
}

function openRestore(record: BackupRecord): void {
  restoreTarget.value = record
  restoreConfirm.value = ''
  restoreOpen.value = true
}

async function doRestore(): Promise<void> {
  if (restoreConfirm.value !== 'RESTORE' || restoreTarget.value === null) return
  const id = restoreTarget.value.id
  await run(async () => {
    await persistence.startRestore(id)
    restoreOpen.value = false
    restoreTarget.value = null
    notice.value = '恢复完成，向量索引已在后台重建'
  })
}

function formatBytes(bytes: number): string {
  if (!bytes) return '—'
  return bytes < 1024 * 1024
    ? `${(bytes / 1024).toFixed(1)} KB`
    : `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function formatTime(value: string | null): string {
  if (!value) return '—'
  const date = new Date(value.includes('T') ? value : value.replace(' ', 'T'))
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN')
}

// ---- 附件存储 ----
const storageDraft = ref<AttachmentStorageSetting>({
  mode: 'LOCAL_FS', localDir: './data/attachments', maxSizeMb: 20,
})
/** 保存前待确认的模式切换：null 表示当前保存不改变模式，无需弹窗。 */
const pendingMode = ref<'LOCAL_FS' | 'DATABASE' | null>(null)
const switchOpen = ref(false)
const uploadBusy = ref(false)

async function loadAttachmentStoragePanel(): Promise<void> {
  await run(async () => {
    await attachmentStorage.loadAll()
    if (attachmentStorage.setting !== null) storageDraft.value = { ...attachmentStorage.setting }
  })
}

/** 点保存：模式没变直接保存；变了先弹二次确认。 */
function saveAttachmentSetting(): void {
  const original = attachmentStorage.setting?.mode ?? 'LOCAL_FS'
  if (storageDraft.value.mode !== original) {
    pendingMode.value = storageDraft.value.mode
    switchOpen.value = true
    return
  }
  void commitAttachmentSetting()
}

async function commitAttachmentSetting(): Promise<void> {
  switchOpen.value = false
  pendingMode.value = null
  await run(async () => {
    const saved = await attachmentStorage.saveSetting({ ...storageDraft.value })
    storageDraft.value = { ...saved }
    notice.value = '附件存储方式已更新，仅对新附件生效'
  })
}

async function doUploadAttachment(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploadBusy.value = true
  await run(async () => {
    await attachmentStorage.upload(file)
    await attachmentStorage.loadAll()
    notice.value = '附件已上传'
  })
  uploadBusy.value = false
  input.value = ''
}

async function doRemoveAttachment(item: AttachmentItem): Promise<void> {
  if (!window.confirm(`删除附件「${item.originalName}」？该操作不可撤销。`)) return
  await run(async () => {
    await attachmentStorage.remove(item.id)
    notice.value = '附件已删除'
  })
}

async function doDownloadAttachment(item: AttachmentItem): Promise<void> {
  await run(async () => { await attachmentStorage.download(item.id, item.originalName) })
}

// ---- 身份源 ----
const providers = ref<IdentityProvider[]>([])
const providerForm = ref({ name: '', type: 'OIDC', identifierFilter: '', config: '' })
const editingProviderUid = ref<string | null>(null)

async function loadProviders(): Promise<void> {
  await run(async () => { providers.value = await identityProviderApi.list() })
}

function resetProviderForm(): void {
  editingProviderUid.value = null
  providerForm.value = { name: '', type: 'OIDC', identifierFilter: '', config: '' }
}

function editProvider(provider: IdentityProvider): void {
  editingProviderUid.value = provider.uid
  providerForm.value = {
    name: provider.name,
    type: provider.type,
    identifierFilter: provider.identifierFilter ?? '',
    config: provider.config ?? '',
  }
}

async function saveProvider(): Promise<void> {
  if (providerForm.value.name.trim() === '' || providerForm.value.type.trim() === '') {
    error.value = '名称与类型不能为空'
    return
  }
  const payload: IdentityProviderPayload = {
    name: providerForm.value.name.trim(),
    type: providerForm.value.type.trim(),
    identifierFilter: providerForm.value.identifierFilter,
    config: providerForm.value.config,
  }
  await run(async () => {
    if (editingProviderUid.value === null) await identityProviderApi.create(payload)
    else await identityProviderApi.update(editingProviderUid.value, payload)
    resetProviderForm()
    providers.value = await identityProviderApi.list()
  })
}

async function removeProvider(uid: string): Promise<void> {
  if (!window.confirm('确定删除该身份源吗？')) return
  await run(async () => {
    await identityProviderApi.remove(uid)
    providers.value = await identityProviderApi.list()
  })
}

// ---- 原始设置 KV ----
const settings = ref<InstanceSetting[]>([])
const settingName = ref('')
const settingValue = ref('')
const settingDescription = ref('')

async function loadSettings(): Promise<void> {
  await run(async () => { settings.value = await instanceSettingApi.list() })
}

async function saveSetting(): Promise<void> {
  if (settingName.value.trim() === '') { error.value = '设置名不能为空'; return }
  await run(async () => {
    await instanceSettingApi.upsert(settingName.value.trim(), {
      value: settingValue.value,
      description: settingDescription.value,
    })
    settingName.value = ''
    settingValue.value = ''
    settingDescription.value = ''
    settings.value = await instanceSettingApi.list()
  })
}

function editSetting(setting: InstanceSetting): void {
  settingName.value = setting.name
  settingValue.value = setting.value ?? ''
  settingDescription.value = setting.description ?? ''
}

onMounted(() => loadSection(section.value))
onUnmounted(() => persistence.cancelPolling())

</script>

<template>
  <div class="admin-shell">
    <div class="content">
      <header class="page-head">
        <div class="page-head__text">
          <h1 class="page-title">{{ sectionTitles[section].title }}</h1>
          <p class="page-sub">{{ sectionTitles[section].sub }}</p>
        </div>
      </header>

      <p v-if="error !== ''" class="form-error">{{ error }}</p>
      <p v-if="notice !== ''" class="form-notice">{{ notice }}</p>

      <!-- 成员管理 -->
      <section v-if="section === 'users'" class="card">
        <div class="card__head">
          <h2 class="card__title">成员列表</h2>
          <span class="card__hint">{{ users.length }} 位成员</span>
          <div class="spacer" />
          <button class="btn btn-primary btn-sm" type="button" @click="openCreate">＋ 新增成员</button>
        </div>
        <div v-if="users.length === 0" class="panel-empty">暂无成员数据（需 ROLE_ADMIN 权限）。</div>
        <table v-else class="table">
          <thead>
            <tr><th>成员</th><th>ID</th><th>角色</th><th>状态</th><th class="col-action">操作</th></tr>
          </thead>
          <tbody>
            <tr v-for="user in users" :key="user.id">
              <td>
                <div class="user-cell">
                  <span class="avatar avatar--sm">{{ (user.nickname || user.username).slice(0, 1).toUpperCase() }}</span>
                  <div class="user-cell__text">
                    <span class="cell-strong">
                      {{ user.nickname || user.username }}
                      <span v-if="isInitialAdmin(user)" class="chip chip--outline chip--inline">初始管理员</span>
                    </span>
                    <span class="cell-muted">@{{ user.username }}</span>
                  </div>
                </div>
              </td>
              <td><span class="mono cell-muted">#{{ user.id }}</span></td>
              <td>
                <span class="chip" :class="isAdmin(user) ? 'chip--accent' : ''">
                  {{ isAdmin(user) ? '管理员' : '成员' }}
                </span>
              </td>
              <td>
                <span class="chip" :class="user.status === 1 ? 'chip--success' : 'chip--danger'">
                  {{ userStatus(user.status) }}
                </span>
              </td>
              <td class="col-action">
                <span v-if="isSelf(user)" class="chip chip--outline" title="请前往个人资料页修改自己的信息">当前账号</span>
                <span v-else-if="isInitialAdmin(user)" class="chip chip--outline" title="初始管理员不可被其他管理员更新或删除">受保护</span>
                <template v-else>
                  <button class="btn btn-ghost btn-sm" type="button" @click="openEdit(user)">更新</button>
                  <button class="btn btn-ghost btn-sm danger" type="button" @click="removeUser(user)">删除</button>
                </template>
              </td>
            </tr>
          </tbody>
        </table>
      </section>

      <!-- 访问与策略 -->
      <section v-else-if="section === 'policies'" class="card">
        <div class="card__head">
          <h2 class="card__title">访问与策略</h2>
          <span class="card__hint">控制该实例的注册、登录、资料编辑和日历默认行为</span>
        </div>
        <div class="policies">
          <div v-for="field in policyFields" :key="field.key" class="policy">
            <div class="policy__text">
              <span class="policy__name">{{ field.name }}</span>
              <span class="policy__hint">{{ field.hint }}</span>
            </div>
            <button
              type="button"
              class="switch"
              :class="{ 'is-on': general[field.key] }"
              role="switch"
              :aria-checked="general[field.key]"
              :aria-label="field.name"
              @click="general[field.key] = !general[field.key]"
            >
              <span class="switch__dot" />
            </button>
          </div>
        </div>
        <div class="card__foot">
          <span class="card__hint">保存后立即对所有成员生效</span>
          <div class="spacer" />
          <button class="btn btn-primary btn-sm" type="button" @click="saveGeneral">保存设置</button>
        </div>
      </section>

      <!-- RAG 系统 -->
      <section v-else-if="section === 'rag'" class="card">
        <div class="card__head">
          <h2 class="card__title">RAG 系统 API</h2>
          <span class="card__hint">用于检索增强生成的外部服务</span>
        </div>
        <div class="card__body form-stack">
          <div class="policy">
            <div class="policy__text">
              <span class="policy__name">启用 RAG 服务</span>
              <span class="policy__hint">开启后笔记索引与问答将调用下列 API。</span>
            </div>
            <button type="button" class="switch" :class="{ 'is-on': rag.enabled }" role="switch" :aria-checked="rag.enabled" aria-label="启用 RAG 服务" @click="rag.enabled = !rag.enabled">
              <span class="switch__dot" />
            </button>
          </div>
          <label class="field">
            <span>API Base URL</span>
            <input v-model="rag.baseUrl" class="control" type="text" placeholder="https://api.example.com/v1" />
          </label>
          <label class="field">
            <span>API Key</span>
            <input v-model="rag.apiKey" class="control" type="password" placeholder="sk-…" autocomplete="off" />
          </label>
          <label class="field">
            <span>模型名称</span>
            <input v-model="rag.model" class="control" type="text" placeholder="例如 text-embedding-3-small" />
          </label>
        </div>
        <div class="card__foot">
          <div class="spacer" />
          <button class="btn btn-primary btn-sm" type="button" @click="saveRag">保存配置</button>
        </div>
      </section>

      <!-- 数据持久化：概览 / 策略 / 备份历史 -->
      <section v-else-if="section === 'persistence'" class="snapshot-page">
        <div class="card">
          <div class="card__head">
            <div>
              <h2 class="card__title">数据快照概览</h2>
              <span class="card__hint">业务数据由数据库持久化；这里管理整库快照、备份计划与恢复</span>
            </div>
            <button class="btn btn-primary btn-sm" type="button"
              :disabled="persistence.busy || persistence.status?.runningBackupId != null || persistence.status?.directoryWritable === false"
              @click="doBackup">
              {{ persistence.busy ? '执行中…' : '立即备份' }}
            </button>
          </div>
          <div class="card__body">
            <p v-if="persistence.status !== null && !persistence.status.directoryWritable" class="form-error">
              备份目录无法写入：{{ persistence.status.storagePath }}。请确认该路径是目录，且运行服务的账号对其有写权限。
            </p>
            <div v-if="persistence.status !== null" class="snapshot-grid">
              <div class="snapshot-stat"><span>数据库占用</span><strong>{{ formatBytes(persistence.status.databaseSizeBytes) }}</strong></div>
              <div class="snapshot-stat"><span>业务表 / 行数</span><strong>{{ persistence.status.tables.length }} / {{ persistence.status.totalRows }}</strong></div>
              <div class="snapshot-stat"><span>索引切片</span><strong>{{ persistence.status.indexChunks }}</strong></div>
              <div class="snapshot-stat"><span>上次备份</span><strong>{{ persistence.status.lastBackup === null ? '尚无' : formatTime(persistence.status.lastBackup.finishedAt) }}</strong></div>
              <div class="snapshot-stat"><span>下次自动备份</span><strong>{{ persistence.status.nextRunAt === null ? '未安排' : formatTime(persistence.status.nextRunAt) }}</strong></div>
            </div>
            <p v-if="persistence.status !== null" class="snapshot-line">
              备份目录 <code>{{ persistence.status.storagePath }}</code>
              <span class="chip chip--outline">{{ persistence.status.directoryWritable ? '可写' : '不可写' }}</span>
              <span v-if="!persistence.status.autoBackupEnabled" class="chip chip--outline">运维已暂停自动备份</span>
            </p>
            <p v-if="persistence.task !== null" class="task-line">
              任务 {{ persistence.task.kind }}：{{ persistence.task.message }}
              <span v-if="persistence.task.totalTables > 1">（{{ persistence.task.completedTables }}/{{ persistence.task.totalTables }} 张表）</span>
            </p>
          </div>
        </div>

        <div class="card">
          <div class="card__head">
            <h2 class="card__title">备份策略</h2>
            <span class="card__hint">仅对当前实例生效，保存后由服务端校验并规范化</span>
          </div>
          <form class="card__body form-stack" @submit.prevent="savePersistenceSetting">
            <div class="policy">
              <div class="policy__text">
                <span class="policy__name">启用快照备份</span>
                <span class="policy__hint">关闭后停止自动快照；业务数据仍由数据库持久化，不受影响。</span>
              </div>
              <button type="button" class="switch" :class="{ 'is-on': settingDraft.enabled }" role="switch"
                :aria-checked="settingDraft.enabled" aria-label="启用快照备份"
                @click="settingDraft.enabled = !settingDraft.enabled"><span class="switch__dot" /></button>
            </div>
            <label class="field">
              <span>备份目录</span>
              <input v-model="settingDraft.storagePath" class="control" type="text" placeholder="./data/backups 或 /var/lib/aireview/backups" />
            </label>
            <div class="policy">
              <div class="policy__text">
                <span class="policy__name">自动备份</span>
                <span class="policy__hint">按下方周期生成整库快照，超出保留天数的归档会被清理。</span>
              </div>
              <button type="button" class="switch" :class="{ 'is-on': settingDraft.autoBackup }" role="switch"
                :aria-checked="settingDraft.autoBackup" aria-label="自动备份"
                @click="settingDraft.autoBackup = !settingDraft.autoBackup"><span class="switch__dot" /></button>
            </div>
            <label class="field">
              <span>备份周期</span>
              <select v-model="settingDraft.backupCron" class="control">
                <option v-for="option in CRON_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option>
                <option v-if="customCron" :value="settingDraft.backupCron">自定义：{{ settingDraft.backupCron }}</option>
              </select>
            </label>
            <label class="field">
              <span>保留天数</span>
              <input v-model.number="settingDraft.retentionDays" class="control" type="number" min="1" max="3650" />
            </label>
            <p class="modal__hint">归档内含密码哈希与访问令牌摘要，请只放在受控目录，勿外传。</p>
            <div class="card__foot">
              <div class="spacer" />
              <button class="btn btn-primary btn-sm" type="submit">保存策略</button>
            </div>
          </form>
        </div>

        <div class="card">
          <div class="card__head">
            <h2 class="card__title">备份历史</h2>
            <span class="card__hint">共 {{ persistence.total }} 份归档</span>
          </div>
          <div class="card__body">
            <p v-if="persistence.records.length === 0" class="panel-empty">还没有备份，点右上角「立即备份」生成第一份快照。</p>
            <table v-else class="table">
              <thead>
                <tr><th>生成时间</th><th>来源</th><th>状态</th><th>大小</th><th>行数</th><th class="col-action">操作</th></tr>
              </thead>
              <tbody>
                <tr v-for="record in persistence.records" :key="record.id">
                  <td>{{ formatTime(record.startedAt) }}</td>
                  <td>
                    <span class="chip chip--outline">{{ record.triggerType === 'AUTO' ? '自动' : record.triggerType === 'PRE_RESTORE' ? '恢复前' : '手动' }}</span>
                    <span v-if="record.keepForever" class="chip chip--accent">长期保留</span>
                  </td>
                  <td>
                    <span :class="['chip', record.status === 'SUCCESS' ? 'chip--accent' : record.status === 'FAILED' ? 'chip--danger' : 'chip--outline']">{{ record.status }}</span>
                    <span v-if="record.errorMessage" class="cell-muted" :title="record.errorMessage"> · {{ record.errorMessage }}</span>
                  </td>
                  <td>{{ formatBytes(record.sizeBytes) }}</td>
                  <td>{{ record.rowCount || '—' }}</td>
                  <td class="col-action">
                    <template v-if="record.status === 'SUCCESS'">
                      <button class="btn btn-ghost btn-sm" type="button" @click="doDownload(record)">下载</button>
                      <button class="btn btn-ghost btn-sm" type="button" @click="doVerify(record.id)">校验</button>
                      <button class="btn btn-ghost btn-sm" type="button" @click="openRestore(record)">恢复</button>
                    </template>
                    <button class="btn btn-ghost btn-sm" type="button" @click="doRemove(record)">删除</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div v-if="restoreOpen && restoreTarget !== null" class="modal-mask" @click.self="restoreOpen = false">
          <div class="modal card" role="dialog" aria-modal="true" aria-labelledby="restore-title">
            <div class="card__head">
              <h2 id="restore-title" class="card__title">恢复数据快照</h2>
              <button class="btn btn-ghost btn-sm" type="button" @click="restoreOpen = false">关闭</button>
            </div>
            <div class="card__body form-stack">
              <p class="modal__hint">
                将用 <code>{{ restoreTarget.fileName }}</code> 覆盖当前 18 张业务表。
                系统会先自动生成一份「恢复前」快照作为回退点；恢复期间请勿操作实例。
              </p>
              <label class="field">
                <span>输入 RESTORE 确认</span>
                <input v-model="restoreConfirm" class="control mono" type="text" placeholder="RESTORE" />
              </label>
            </div>
            <div class="card__foot">
              <div class="spacer" />
              <button class="btn btn-ghost btn-sm" type="button" @click="restoreOpen = false">取消</button>
              <button class="btn btn-danger btn-sm" type="button" :disabled="restoreConfirm !== 'RESTORE' || persistence.busy" @click="doRestore">
                确认恢复
              </button>
            </div>
          </div>
        </div>
      </section>

      <!-- 附件存储：存储方式（仅管理员）/ 用量 / 附件列表 -->
      <section v-else-if="section === 'attachments'" class="snapshot-page">
        <div class="card">
          <div class="card__head">
            <div>
              <h2 class="card__title">附件存储</h2>
              <span class="card__hint">仅管理员可更改；切换方式只影响之后新上传的附件</span>
            </div>
          </div>
          <form class="card__body form-stack" @submit.prevent="saveAttachmentSetting">
            <div class="storage-mode-list" role="radiogroup" aria-label="附件存储方式">
              <label class="storage-mode" :class="{ 'is-active': storageDraft.mode === 'LOCAL_FS' }">
                <input v-model="storageDraft.mode" class="storage-mode__radio" type="radio" name="storage-mode" value="LOCAL_FS" />
                <span class="storage-mode__body">
                  <span class="storage-mode__name">本地文件系统 <span class="chip chip--outline">默认 · 推荐</span></span>
                  <span class="storage-mode__hint">将新附件保存在服务器文件系统中。新实例默认使用这种方式</span>
                </span>
              </label>
              <label class="storage-mode" :class="{ 'is-active': storageDraft.mode === 'DATABASE' }">
                <input v-model="storageDraft.mode" class="storage-mode__radio" type="radio" name="storage-mode" value="DATABASE" />
                <span class="storage-mode__body">
                  <span class="storage-mode__name">数据库</span>
                  <span class="storage-mode__hint">将附件二进制内容直接存入应用数据库。</span>
                </span>
              </label>
            </div>
            <label v-if="storageDraft.mode === 'LOCAL_FS'" class="field">
              <span>附件目录</span>
              <input v-model="storageDraft.localDir" class="control" type="text" placeholder="./data/attachments 或 /var/lib/aireview/attachments" />
            </label>
            <label class="field">
              <span>单附件上限（MB）</span>
              <input v-model.number="storageDraft.maxSizeMb" class="control" type="number" min="1" max="200" />
            </label>
            <p class="modal__hint">切换到数据库模式会让数据库与备份归档增大；已存附件仍按各自原方式读取，不受影响。</p>
            <div class="card__foot">
              <div class="spacer" />
              <button class="btn btn-primary btn-sm" type="submit" :disabled="attachmentStorage.busy">保存存储方式</button>
            </div>
          </form>
        </div>

        <div class="card">
          <div class="card__head">
            <div>
              <h2 class="card__title">存储概览</h2>
              <span class="card__hint">按附件自身记录的方式统计，不随当前配置变化</span>
            </div>
          </div>
          <div class="card__body">
            <p v-if="attachmentStorage.status !== null && !attachmentStorage.status.directoryWritable" class="form-error">
              附件目录无法写入：{{ attachmentStorage.status.localDir }}。请确认该路径是目录，且运行服务的账号对其有写权限。
            </p>
            <div v-if="attachmentStorage.status !== null" class="snapshot-grid">
              <div class="snapshot-stat"><span>当前方式</span><strong>{{ attachmentStorage.status.mode === 'DATABASE' ? '数据库' : '本地文件系统' }}</strong></div>
              <div class="snapshot-stat"><span>文件系统</span><strong>{{ attachmentStorage.status.fsCount }} 个 · {{ formatBytes(attachmentStorage.status.fsBytes) }}</strong></div>
              <div class="snapshot-stat"><span>数据库</span><strong>{{ attachmentStorage.status.dbCount }} 个 · {{ formatBytes(attachmentStorage.status.dbBytes) }}</strong></div>
            </div>
            <p v-if="attachmentStorage.status !== null" class="snapshot-line">
              附件目录 <code>{{ attachmentStorage.status.localDir }}</code>
              <span class="chip chip--outline">{{ attachmentStorage.status.directoryWritable ? '可写' : '不可写' }}</span>
            </p>
          </div>
        </div>

        <div class="card">
          <div class="card__head">
            <div>
              <h2 class="card__title">我的附件</h2>
              <span class="card__hint">可按当前存储方式上传；下载不区分存储位置</span>
            </div>
            <label class="btn btn-primary btn-sm" :class="{ 'is-disabled': uploadBusy }">
              {{ uploadBusy ? '上传中…' : '上传附件' }}
              <input type="file" class="visually-hidden" :disabled="uploadBusy" @change="doUploadAttachment" />
            </label>
          </div>
          <div class="card__body">
            <p v-if="attachmentStorage.items.length === 0" class="panel-empty">还没有附件，点右上角「上传附件」添加第一个。</p>
            <table v-else class="table">
              <thead>
                <tr><th>文件名</th><th>大小</th><th>存储方式</th><th>上传时间</th><th>操作</th></tr>
              </thead>
              <tbody>
                <tr v-for="item in attachmentStorage.items" :key="item.id">
                  <td>{{ item.originalName }}</td>
                  <td>{{ formatBytes(item.sizeBytes) }}</td>
                  <td><span class="chip chip--outline">{{ item.storageMode === 'DATABASE' ? '数据库' : '文件系统' }}</span></td>
                  <td>{{ formatTime(item.createdAt) }}</td>
                  <td>
                    <button class="btn btn-ghost btn-sm" type="button" @click="doDownloadAttachment(item)">下载</button>
                    <button class="btn btn-danger btn-sm" type="button" @click="doRemoveAttachment(item)">删除</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div v-if="switchOpen && pendingMode !== null" class="modal-mask" @click.self="switchOpen = false">
          <div class="modal card" role="dialog" aria-modal="true" aria-labelledby="switch-title">
            <div class="card__head">
              <h2 id="switch-title" class="card__title">{{ pendingMode === 'DATABASE' ? '切换到数据库存储？' : '切换到本地文件系统？' }}</h2>
              <button class="btn btn-ghost btn-sm" type="button" @click="switchOpen = false">关闭</button>
            </div>
            <div class="card__body form-stack">
              <p v-if="pendingMode === 'LOCAL_FS'" class="modal__hint">
                将新附件保存在服务器文件系统中。新实例默认使用这种方式。注意：容器部署必须挂载持久卷到附件目录，否则重建后文件丢失；已存附件不受影响，仍按各自原方式读取。
              </p>
              <p v-else class="modal__hint">
                将附件二进制内容直接存入应用数据库。注意：数据库体积与备份归档会增大，单附件上限 {{ storageDraft.maxSizeMb }}MB；已存附件不受影响，仍按各自原方式读取。
              </p>
            </div>
            <div class="card__foot">
              <div class="spacer" />
              <button class="btn btn-ghost btn-sm" type="button" @click="switchOpen = false">取消</button>
              <button class="btn btn-primary btn-sm" type="button" @click="commitAttachmentSetting">确认切换</button>
            </div>
          </div>
        </div>
      </section>

      <!-- 身份源 -->
      <section v-else-if="section === 'providers'" class="card">
        <div class="card__head">
          <h2 class="card__title">{{ editingProviderUid === null ? '新建身份源' : '编辑身份源' }}</h2>
          <button v-if="editingProviderUid !== null" class="btn btn-sm btn-ghost" type="button" @click="resetProviderForm">取消编辑</button>
        </div>
        <form class="form-grid" @submit.prevent="saveProvider">
          <div class="form-col">
            <label class="label" for="provider-name">名称</label>
            <input id="provider-name" v-model="providerForm.name" class="control" type="text" maxlength="128" placeholder="例如：企业 OIDC" />
          </div>
          <div class="form-col">
            <label class="label" for="provider-type">类型</label>
            <input id="provider-type" v-model="providerForm.type" class="control" type="text" placeholder="OIDC / OAUTH2" />
          </div>
          <div class="form-col form-col--wide">
            <label class="label" for="provider-filter">标识符过滤</label>
            <input id="provider-filter" v-model="providerForm.identifierFilter" class="control" type="text" placeholder="可选，如邮箱域名" />
          </div>
          <div class="form-col form-col--wide">
            <label class="label" for="provider-config">配置 JSON</label>
            <textarea id="provider-config" v-model="providerForm.config" class="control mono" rows="3" placeholder='{"issuer":"https://…"}' />
          </div>
          <div class="form-actions">
            <button class="btn btn-primary btn-sm" type="submit">{{ editingProviderUid === null ? '新建' : '保存修改' }}</button>
          </div>
        </form>
        <div v-if="providers.length === 0" class="panel-empty">暂无身份源，先在上方创建。</div>
        <table v-else class="table">
          <thead><tr><th>名称</th><th>类型</th><th>标识符过滤</th><th class="col-action">操作</th></tr></thead>
          <tbody>
            <tr v-for="provider in providers" :key="provider.uid">
              <td><span class="cell-strong">{{ provider.name }}</span></td>
              <td><span class="chip chip--accent">{{ provider.type }}</span></td>
              <td><span class="cell-muted">{{ provider.identifierFilter || '—' }}</span></td>
              <td class="col-action">
                <button class="btn btn-ghost btn-sm" type="button" @click="editProvider(provider)">编辑</button>
                <button class="btn btn-ghost btn-sm danger" type="button" @click="removeProvider(provider.uid)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </section>

      <!-- 原始设置 -->
      <section v-else class="card">
        <div class="card__head">
          <h2 class="card__title">实例设置</h2>
          <span class="card__hint">同名 key 会被覆盖</span>
        </div>
        <form class="form-grid" @submit.prevent="saveSetting">
          <div class="form-col">
            <label class="label" for="setting-name">设置名（key）</label>
            <input id="setting-name" v-model="settingName" class="control mono" type="text" placeholder="site.title" />
          </div>
          <div class="form-col">
            <label class="label" for="setting-value">设置值</label>
            <input id="setting-value" v-model="settingValue" class="control" type="text" placeholder="备忘录" />
          </div>
          <div class="form-col form-col--wide">
            <label class="label" for="setting-desc">描述</label>
            <input id="setting-desc" v-model="settingDescription" class="control" type="text" placeholder="可选说明" />
          </div>
          <div class="form-actions">
            <button class="btn btn-primary btn-sm" type="submit">新增 / 更新</button>
          </div>
        </form>
        <div v-if="settings.length === 0" class="panel-empty">暂无实例设置。</div>
        <table v-else class="table">
          <thead><tr><th>Key</th><th>值</th><th>描述</th><th class="col-action">操作</th></tr></thead>
          <tbody>
            <tr v-for="setting in settings" :key="setting.name">
              <td><span class="mono cell-strong">{{ setting.name }}</span></td>
              <td><span class="cell-muted">{{ setting.value ?? '（空）' }}</span></td>
              <td><span class="cell-muted">{{ setting.description || '—' }}</span></td>
              <td class="col-action">
                <button class="btn btn-ghost btn-sm" type="button" @click="editSetting(setting)">编辑</button>
              </td>
            </tr>
          </tbody>
        </table>
      </section>
    </div>

    <!-- 新增成员弹窗 -->
    <div v-if="creating" class="modal-mask" @click.self="closeCreate">
      <div class="modal card" role="dialog" aria-modal="true" aria-labelledby="create-title">
        <div class="card__head">
          <h2 id="create-title" class="card__title">新增成员</h2>
          <button class="btn btn-ghost btn-sm" type="button" @click="closeCreate">关闭</button>
        </div>
        <div class="card__body">
          <p v-if="error !== ''" class="form-error">{{ error }}</p>
          <label class="field">
            <span>登录用户名</span>
            <input v-model="createForm.username" class="control" type="text" maxlength="32" placeholder="3-32 位，字母、数字与下划线" />
          </label>
          <label class="field">
            <span>初始密码</span>
            <input v-model="createForm.password" class="control" type="password" autocomplete="new-password" placeholder="6-64 位" />
          </label>
          <p class="modal__hint">新成员以「成员」角色创建，创建后可在列表里提权为管理员。</p>
        </div>
        <div class="card__foot">
          <div class="spacer" />
          <button class="btn btn-ghost btn-sm" type="button" @click="closeCreate">取消</button>
          <button class="btn btn-primary btn-sm" type="button" :disabled="savingCreate" @click="saveCreate">
            {{ savingCreate ? '创建中…' : '创建成员' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 更新成员弹窗 -->
    <div v-if="editingUser !== null" class="modal-mask" @click.self="closeEdit">
      <div class="modal card" role="dialog" aria-modal="true" aria-labelledby="edit-title">
        <div class="card__head">
          <h2 id="edit-title" class="card__title">更新成员 · @{{ editingUser.username }}</h2>
          <button class="btn btn-ghost btn-sm" type="button" @click="closeEdit">关闭</button>
        </div>
        <div class="card__body">
          <p v-if="error !== ''" class="form-error">{{ error }}</p>
          <div class="field">
            <span>角色（提权 / 降权）</span>
            <div class="seg" role="group" aria-label="成员角色">
              <button type="button" class="seg__item" :class="{ 'is-active': editForm.role === 'ROLE_USER' }" @click="editForm.role = 'ROLE_USER'">设为成员</button>
              <button type="button" class="seg__item" :class="{ 'is-active': editForm.role === 'ROLE_ADMIN' }" @click="editForm.role = 'ROLE_ADMIN'">设为管理员</button>
            </div>
          </div>
          <label class="field">
            <span>修改用户名</span>
            <input v-model="editForm.username" class="control" type="text" maxlength="32" placeholder="3-32 位" />
          </label>
          <label class="field">
            <span>修改密码（留空则不修改）</span>
            <input v-model="editForm.password" class="control" type="password" autocomplete="new-password" placeholder="6-64 位" />
          </label>
          <p class="modal__hint">用户名、密码或角色变更后，该成员的旧会话会立即失效，需要重新登录。</p>
        </div>
        <div class="card__foot">
          <div class="spacer" />
          <button class="btn btn-ghost btn-sm" type="button" @click="closeEdit">取消</button>
          <button class="btn btn-primary btn-sm" type="button" :disabled="savingEdit" @click="saveEdit">
            {{ savingEdit ? '保存中…' : '保存更新' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin-shell {
  width: 100%;
  max-width: 1120px;
  margin: 0 auto;
}

.content {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  min-width: 0;
}

.form-notice {
  margin: 0;
  padding: 10px 14px;
  border: 1px solid color-mix(in srgb, var(--success) 24%, transparent);
  border-radius: var(--radius);
  background: var(--success-soft);
  color: var(--success);
  font-size: 13.5px;
}

.policies {
  display: flex;
  flex-direction: column;
}

.policy {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-5);
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--border);
}

.policy:last-child {
  border-bottom: none;
}

.policy__text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.policy__name {
  font-size: 14px;
  font-weight: 500;
  color: var(--fg);
}

.policy__hint {
  margin-top: 3px;
  color: var(--fg-muted);
  font-size: 12.5px;
  line-height: 1.6;
}

.switch {
  flex: none;
  width: 38px;
  height: 22px;
  padding: 2px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-pill);
  background: var(--bg-soft);
  transition: background-color var(--dur) var(--ease), border-color var(--dur) var(--ease);
}

.switch__dot {
  display: block;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--bg);
  box-shadow: var(--shadow-xs);
  transition: transform var(--dur) var(--ease);
}

.switch.is-on {
  border-color: var(--accent);
  background: var(--accent);
}

.switch.is-on .switch__dot {
  transform: translateX(16px);
}

.form-stack {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.form-stack .policy {
  padding: var(--space-3) 0;
}

.card__foot {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-5);
  border-top: 1px solid var(--border);
  background: var(--bg-sunken);
  border-radius: 0 0 var(--radius-lg) var(--radius-lg);
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-3) var(--space-4);
  padding: var(--space-5);
  border-bottom: 1px solid var(--border);
  background: var(--bg-sunken);
}

.form-col {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.form-col--wide {
  grid-column: 1 / -1;
}

.form-grid textarea.control {
  min-height: 68px;
}

.form-actions {
  grid-column: 1 / -1;
  display: flex;
  gap: var(--space-2);
}

.panel-empty {
  padding: var(--space-6) var(--space-5);
  color: var(--fg-subtle);
  font-size: 13px;
}

.cell-strong {
  font-weight: 500;
}

/* 行内「初始管理员」小标签，跟随昵称同行显示 */
.chip--inline {
  height: 19px;
  margin-left: 6px;
  padding: 0 6px;
  font-size: 11.5px;
  font-weight: 400;
  vertical-align: middle;
}

.cell-muted {
  color: var(--fg-muted);
  font-size: 13px;
}

.col-action {
  width: 132px;
  text-align: right;
  white-space: nowrap;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.user-cell__text {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
}

.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: grid;
  place-items: center;
  padding: var(--space-5);
  background: color-mix(in srgb, var(--fg) 38%, transparent);
}

.modal {
  width: min(460px, 100%);
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: var(--shadow-lg);
}

.modal__hint {
  margin: 0;
  padding: var(--space-3);
  border-radius: var(--radius);
  background: var(--bg-sunken);
  color: var(--fg-muted);
  font-size: 12.5px;
  line-height: 1.6;
}

/* ---- 数据快照 ---- */
.snapshot-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.snapshot-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: var(--space-3);
}

.snapshot-stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-soft);
}

.snapshot-stat span {
  color: var(--fg-muted);
  font-size: 12px;
}

.snapshot-stat strong {
  font-size: 16px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.snapshot-line,
.task-line {
  margin: var(--space-3) 0 0;
  color: var(--fg-muted);
  font-size: 12.5px;
}

.task-line {
  color: var(--accent);
}

.snapshot-line code {
  font-family: var(--font-mono, monospace);
}

.btn-danger {
  color: var(--danger);
  border-color: var(--danger);
}

.storage-mode-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.storage-mode {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg);
  cursor: pointer;
  transition: border-color var(--dur-fast) var(--ease), background-color var(--dur-fast) var(--ease);
}

.storage-mode:hover {
  border-color: var(--border-strong);
}

.storage-mode.is-active {
  border-color: var(--accent);
  background: var(--accent-soft);
}

.storage-mode__radio {
  flex: none;
  margin: 3px 0 0;
  accent-color: var(--accent);
}

.storage-mode__body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.storage-mode__name {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: 14px;
  font-weight: 600;
  color: var(--fg);
}

.storage-mode__hint {
  color: var(--fg-muted);
  font-size: 12.5px;
  line-height: 1.6;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.btn.is-disabled {
  opacity: 0.6;
  pointer-events: none;
}

@media (max-width: 720px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .card {
    overflow-x: auto;
  }

  /* 保证操作列不被压缩，改为横向滚动查看 */
  .table {
    min-width: 660px;
  }
}
</style>
