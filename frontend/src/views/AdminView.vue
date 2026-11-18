<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { adminApi, identityProviderApi, instanceSettingApi } from '../api/admin'
import type {
  AdminUser,
  IdentityProvider,
  IdentityProviderPayload,
  InstanceSetting,
} from '../types/admin'

type Tab = 'providers' | 'settings' | 'users'
const tab = ref<Tab>('providers')

const error = ref('')

// 身份源
const providers = ref<IdentityProvider[]>([])
const providerForm = ref({ name: '', type: 'OIDC', identifierFilter: '', config: '' })
const editingProviderUid = ref<string | null>(null)

// 实例设置
const settings = ref<InstanceSetting[]>([])
const settingName = ref('')
const settingValue = ref('')
const settingDescription = ref('')

// 用户
const users = ref<AdminUser[]>([])

async function run(action: () => Promise<void>): Promise<void> {
  error.value = ''
  try {
    await action()
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '操作失败'
  }
}

async function loadProviders(): Promise<void> {
  await run(async () => { providers.value = await identityProviderApi.list() })
}

async function loadSettings(): Promise<void> {
  await run(async () => { settings.value = await instanceSettingApi.list() })
}

async function loadUsers(): Promise<void> {
  await run(async () => { users.value = await adminApi.users() })
}

function switchTab(next: Tab): void {
  tab.value = next
  if (next === 'providers') void loadProviders()
  else if (next === 'settings') void loadSettings()
  else void loadUsers()
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

async function saveSetting(): Promise<void> {
  if (settingName.value.trim() === '') {
    error.value = '设置名不能为空'
    return
  }
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

function userStatus(status: number): string {
  return status === 1 ? '启用' : String(status)
}

onMounted(loadProviders)
</script>

<template>
  <section class="page">
    <header class="page-head">
      <div class="page-head__text">
        <h1 class="page-title">管理后台</h1>
        <p class="page-sub">身份源、实例设置与用户管理，需要管理员权限</p>
      </div>
      <div class="page-head__actions">
        <span class="chip chip--warning">仅管理员</span>
      </div>
    </header>

    <nav class="seg tabs" role="tablist" aria-label="管理模块">
      <button
        class="seg__item"
        :class="{ 'is-active': tab === 'providers' }"
        type="button"
        role="tab"
        :aria-selected="tab === 'providers'"
        @click="switchTab('providers')"
      >
        身份源
      </button>
      <button
        class="seg__item"
        :class="{ 'is-active': tab === 'settings' }"
        type="button"
        role="tab"
        :aria-selected="tab === 'settings'"
        @click="switchTab('settings')"
      >
        实例设置
      </button>
      <button
        class="seg__item"
        :class="{ 'is-active': tab === 'users' }"
        type="button"
        role="tab"
        :aria-selected="tab === 'users'"
        @click="switchTab('users')"
      >
        用户
      </button>
    </nav>

    <p v-if="error !== ''" class="form-error">{{ error }}</p>

    <!-- 身份源 -->
    <section v-if="tab === 'providers'" class="card">
      <div class="card__head">
        <h2 class="card__title">{{ editingProviderUid === null ? '新建身份源' : '编辑身份源' }}</h2>
        <button v-if="editingProviderUid !== null" class="btn btn-sm btn-ghost" type="button" @click="resetProviderForm">
          取消编辑
        </button>
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
          <button class="btn btn-primary btn-sm" type="submit">
            {{ editingProviderUid === null ? '新建' : '保存修改' }}
          </button>
        </div>
      </form>

      <div v-if="providers.length === 0" class="panel-empty">暂无身份源，先在上方创建。</div>
      <table v-else class="table">
        <thead>
          <tr><th>名称</th><th>类型</th><th>标识符过滤</th><th class="col-action">操作</th></tr>
        </thead>
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

    <!-- 实例设置 -->
    <section v-else-if="tab === 'settings'" class="card">
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
        <thead>
          <tr><th>Key</th><th>值</th><th>描述</th><th class="col-action">操作</th></tr>
        </thead>
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

    <!-- 用户 -->
    <section v-else class="card">
      <div class="card__head">
        <h2 class="card__title">用户列表</h2>
        <span class="card__hint">{{ users.length }} 位用户</span>
      </div>

      <div v-if="users.length === 0" class="panel-empty">暂无用户数据（后端要求 ROLE_ADMIN）。</div>
      <table v-else class="table">
        <thead>
          <tr><th>用户</th><th>ID</th><th>状态</th></tr>
        </thead>
        <tbody>
          <tr v-for="user in users" :key="user.id">
            <td>
              <div class="user-cell">
                <span class="avatar avatar--sm">{{ (user.nickname || user.username).slice(0, 1).toUpperCase() }}</span>
                <div class="user-cell__text">
                  <span class="cell-strong">{{ user.nickname || user.username }}</span>
                  <span class="cell-muted">@{{ user.username }}</span>
                </div>
              </div>
            </td>
            <td><span class="mono cell-muted">#{{ user.id }}</span></td>
            <td>
              <span class="chip" :class="user.status === 1 ? 'chip--success' : 'chip--danger'">
                {{ userStatus(user.status) }}
              </span>
            </td>
          </tr>
        </tbody>
      </table>
    </section>
  </section>
</template>

<style scoped>
.tabs {
  align-self: flex-start;
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

@media (max-width: 720px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .card {
    overflow-x: auto;
  }
}
</style>
