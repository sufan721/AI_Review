<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useUserStore } from '../stores/user'

const user = useUserStore()

const displayName = ref('')
const description = ref('')
const avatarUrl = ref('')
const error = ref('')
const saved = ref(false)
const saving = ref(false)

const previewUrl = computed(() => avatarUrl.value.trim())
const initial = computed(() =>
  (displayName.value.trim() || user.profile?.username || '?').slice(0, 1).toUpperCase(),
)
const shownName = computed(() => displayName.value.trim() || user.profile?.username || '')

async function run(action: () => Promise<void>): Promise<void> {
  error.value = ''
  try {
    await action()
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '操作失败'
  }
}

function load(): Promise<void> {
  return run(async () => {
    const profile = await user.fetchProfile()
    displayName.value = profile.displayName ?? ''
    description.value = profile.description ?? ''
    avatarUrl.value = profile.avatarUrl ?? ''
  })
}

async function save(): Promise<void> {
  saved.value = false
  await run(async () => {
    saving.value = true
    try {
      await user.updateProfile({
        displayName: displayName.value,
        description: description.value,
        avatarUrl: avatarUrl.value,
      })
      saved.value = true
    } finally {
      saving.value = false
    }
  })
}

function formatTime(ts: number): string {
  const date = new Date(ts)
  return Number.isNaN(date.getTime()) ? '' : date.toLocaleDateString('zh-CN')
}

/** 后端返回 ROLE_ADMIN / ROLE_USER 之类的原始值，展示层转成可读中文。 */
function roleLabel(role: string): string {
  if (role === 'ROLE_ADMIN') return '管理员'
  if (role === 'ROLE_USER') return '普通用户'
  return role
}

onMounted(load)
</script>

<template>
  <section class="page page--mid">
    <header class="page-head">
      <div class="page-head__text">
        <h1 class="page-title">个人资料</h1>
        <p class="page-sub">这些信息会展示在评论、分享页与成员列表里</p>
      </div>
    </header>

    <p v-if="error !== ''" class="form-error">{{ error }}</p>

    <div v-if="user.profile" class="layout">
      <aside class="card profile">
        <div class="profile__hero" aria-hidden="true" />
        <div class="profile__body">
          <img v-if="previewUrl !== ''" :src="previewUrl" alt="头像" class="avatar avatar--lg profile__avatar" />
          <div v-else class="avatar avatar--lg profile__avatar">{{ initial }}</div>

          <h2 class="profile__name">{{ shownName }}</h2>
          <p class="profile__handle">@{{ user.profile.username }}</p>

          <div class="profile__roles">
            <span v-for="role in user.profile.roles" :key="role" class="chip chip--accent">{{ roleLabel(role) }}</span>
          </div>

          <p class="profile__desc">{{ description || '还没有个人描述' }}</p>

          <dl class="profile__meta">
            <div>
              <dt>注册时间</dt>
              <dd>{{ formatTime(user.profile.createdAt) }}</dd>
            </div>
            <div>
              <dt>用户 ID</dt>
              <dd class="mono">#{{ user.profile.id }}</dd>
            </div>
          </dl>
        </div>
      </aside>

      <section class="card form-card">
        <div class="card__head">
          <h2 class="card__title">编辑资料</h2>
          <span class="card__hint">留空则回退到默认值</span>
        </div>

        <div class="card__body">
          <label class="field">
            <span>显示名称</span>
            <input v-model="displayName" type="text" maxlength="64" placeholder="例如：张三" />
          </label>
          <label class="field">
            <span>个人描述</span>
            <textarea v-model="description" class="control" maxlength="512" rows="3" placeholder="一句话介绍自己" />
            <small class="counter">{{ description.length }} / 512</small>
          </label>
          <label class="field">
            <span>头像 URL</span>
            <input v-model="avatarUrl" type="text" maxlength="512" placeholder="https://…" />
          </label>
        </div>

        <div class="card__foot">
          <Transition name="fade">
            <span v-if="saved" class="chip chip--success">已保存</span>
          </Transition>
          <div class="spacer" />
          <button class="btn btn-primary" type="button" :disabled="saving" @click="save">
            {{ saving ? '保存中…' : '保存资料' }}
          </button>
        </div>
      </section>
    </div>

    <div v-else class="card loading-card">
      <span class="spinner" aria-hidden="true" />
      正在加载资料…
    </div>
  </section>
</template>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: var(--space-4);
  align-items: start;
}

/* 左侧资料卡：顶部一条浅色渐变带，头像压在分界线上 */
.profile {
  overflow: hidden;
}

.profile__hero {
  height: 62px;
  background: linear-gradient(120deg, #dfe9ff, #eef3fe 55%, #f7f9fd);
}

.profile__body {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0 var(--space-5) var(--space-5);
  text-align: center;
}

.profile__avatar {
  margin-top: -32px;
  border: 3px solid var(--bg);
  box-shadow: var(--shadow-sm);
}

.profile__name {
  margin: var(--space-3) 0 2px;
  font-size: 17px;
  font-weight: 650;
  letter-spacing: -0.01em;
}

.profile__handle {
  margin: 0;
  color: var(--fg-subtle);
  font-size: 12.5px;
}

.profile__roles {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 5px;
  margin-top: var(--space-3);
}

.profile__desc {
  margin: var(--space-3) 0 0;
  color: var(--fg-muted);
  font-size: 13px;
  line-height: 1.6;
}

.profile__meta {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-3);
  width: 100%;
  margin: var(--space-4) 0 0;
  padding-top: var(--space-3);
  border-top: 1px solid var(--border);
  text-align: left;
}

.profile__meta dt {
  color: var(--fg-subtle);
  font-size: 11.5px;
}

.profile__meta dd {
  margin: 2px 0 0;
  font-size: 13px;
  font-weight: 500;
}

.form-card .card__body {
  padding-bottom: var(--space-2);
}

.counter {
  align-self: flex-end;
  color: var(--fg-subtle);
  font-size: 11.5px;
  font-variant-numeric: tabular-nums;
}

.card__foot {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid var(--border);
  background: var(--bg-sunken);
  border-radius: 0 0 var(--radius-lg) var(--radius-lg);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity var(--dur) var(--ease);
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.loading-card {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-6);
  color: var(--fg-muted);
  font-size: 13.5px;
}

.spinner {
  width: 14px;
  height: 14px;
  border: 2px solid var(--border-strong);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 820px) {
  .layout {
    grid-template-columns: 1fr;
  }
}
</style>
