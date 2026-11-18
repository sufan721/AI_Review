<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AuthShell from '../components/auth/AuthShell.vue'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const username = ref('')
const password = ref('')
const showPassword = ref(false)
const isTyping = ref(false)
const submitting = ref(false)
const error = ref('')

async function submit() {
  if (username.value.trim() === '' || password.value === '' || submitting.value) return
  error.value = ''
  submitting.value = true
  try {
    // 后端只认用户名 + 密码，图形验证码默认关闭
    await auth.login({ username: username.value.trim(), password: password.value })
    const redirect = route.query.redirect
    await router.replace(typeof redirect === 'string' && redirect !== '' ? redirect : '/')
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : '登录失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AuthShell
    title="欢迎回来"
    subtitle="登录后继续管理你的笔记与知识库"
    :show-password="showPassword"
    :password-length="password.length"
    :is-typing="isTyping"
  >
    <form class="auth-form" @submit.prevent="submit">
      <div class="auth-field">
        <label class="auth-label" for="login-username">用户名</label>
        <input
          id="login-username"
          v-model="username"
          class="auth-input"
          type="text"
          name="username"
          autocomplete="username"
          autocapitalize="off"
          spellcheck="false"
          placeholder="请输入用户名"
          :readonly="submitting"
          @focus="isTyping = true"
          @blur="isTyping = false"
          required
        />
      </div>

      <div class="auth-field">
        <span class="auth-label-row">
          <label class="auth-label" for="login-password">密码</label>
          <RouterLink class="auth-link-quiet" to="/forgot">忘记密码？</RouterLink>
        </span>
        <span class="auth-input-wrap">
          <input
            id="login-password"
            v-model="password"
            class="auth-input"
            :type="showPassword ? 'text' : 'password'"
            name="password"
            autocomplete="current-password"
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
        <span>{{ submitting ? '登录中…' : '登录' }}</span>
        <svg class="auth-submit-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M5 12h14" />
          <path d="m12 5 7 7-7 7" />
        </svg>
      </button>
    </form>

    <p class="auth-alt">还没有账号？<RouterLink to="/register">注册</RouterLink></p>
  </AuthShell>
</template>
