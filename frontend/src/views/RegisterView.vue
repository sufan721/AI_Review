<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AuthShell from '../components/auth/AuthShell.vue'
import { instanceApi } from '../api/admin'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()

const username = ref('')
const password = ref('')
const showPassword = ref(false)
const isTyping = ref(false)
const error = ref('')
const submitting = ref(false)
const firstRun = ref(false)

onMounted(async () => {
  try {
    const status = await instanceApi.bootstrap()
    firstRun.value = !status.initialized
  } catch {
    // 引导接口失败时按常规注册展示，不阻塞注册
  }
})

async function submit() {
  if (submitting.value) return
  error.value = ''
  submitting.value = true
  try {
    await auth.register({
      username: username.value.trim(),
      password: password.value,
    })
    await router.replace('/')
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : '注册失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AuthShell
    :title="firstRun ? '注册管理员' : '创建账号'"
    :subtitle="firstRun ? '首次注册，请注册管理员' : '注册后即可开始记录你的笔记'"
    :show-password="showPassword"
    :password-length="password.length"
    :is-typing="isTyping"
  >
    <div v-if="firstRun" class="bootstrap-hint" role="note">
      <strong>首次注册，请注册管理员</strong>
      <span>当前实例还没有任何用户，第一个注册的账号将自动成为管理员。</span>
    </div>

    <form class="auth-form" @submit.prevent="submit">
      <div class="auth-field">
        <label class="auth-label" for="register-username">用户名</label>
        <input
          id="register-username"
          v-model="username"
          class="auth-input"
          type="text"
          name="username"
          autocomplete="username"
          autocapitalize="off"
          spellcheck="false"
          placeholder="3-32 位字母、数字或下划线"
          :readonly="submitting"
          @focus="isTyping = true"
          @blur="isTyping = false"
          required
        />
      </div>

      <div class="auth-field">
        <label class="auth-label" for="register-password">密码</label>
        <span class="auth-input-wrap">
          <input
            id="register-password"
            v-model="password"
            class="auth-input"
            :type="showPassword ? 'text' : 'password'"
            name="password"
            autocomplete="new-password"
            placeholder="请输入密码"
            :readonly="submitting"
            required
          />
          <button
            type="button"
            class="auth-eye-btn"
            :aria-label="showPassword ? '隐藏密码' : '显示密码'"
            :aria-pressed="showPassword"
            @click="showPassword = !showPassword"
          >
            <svg v-if="showPassword" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <path d="M9.9 9.9a3 3 0 1 0 4.2 4.2" />
              <path d="M10.7 5.1A10.4 10.4 0 0 1 12 5c7 0 10 7 10 7a13.2 13.2 0 0 1-1.7 2.7" />
              <path d="M6.6 6.6A13.5 13.5 0 0 0 2 12s3 7 10 7a9.7 9.7 0 0 0 5.4-1.6" />
              <path d="M2 2l20 20" />
            </svg>
            <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <path d="M2.1 12.3a1 1 0 0 1 0-.7 10.8 10.8 0 0 1 19.9 0 1 1 0 0 1 0 .7 10.8 10.8 0 0 1-19.9 0" />
              <circle cx="12" cy="12" r="3" />
            </svg>
          </button>
        </span>
      </div>

      <p v-if="error" class="auth-error" role="alert">{{ error }}</p>

      <button class="auth-submit" type="submit" :disabled="submitting">
        <span>{{ submitting ? '注册中…' : (firstRun ? '注册管理员' : '注册') }}</span>
        <svg class="auth-submit-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M5 12h14" />
          <path d="m12 5 7 7-7 7" />
        </svg>
      </button>
    </form>

    <p class="auth-alt">已有账号？<RouterLink to="/login">登录</RouterLink></p>
  </AuthShell>
</template>

<style scoped>
.bootstrap-hint {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: var(--space-4);
  padding: 10px 14px;
  border: 1px solid color-mix(in srgb, var(--accent) 26%, transparent);
  border-radius: var(--radius);
  background: var(--accent-soft);
  font-size: 13px;
  line-height: 1.55;
}

.bootstrap-hint strong {
  color: var(--accent);
  font-size: 13.5px;
  font-weight: 600;
}

.bootstrap-hint span {
  color: var(--fg-muted);
  font-size: 12.5px;
}
</style>
