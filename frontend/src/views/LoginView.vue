<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { authApi } from '../api/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const mode = ref<'password' | 'sms' | 'email'>('password')
const error = ref('')
const submitting = ref(false)
const sending = ref(false)
const sentHint = ref('')

const username = ref('')
const password = ref('')
const phone = ref('')
const smsCode = ref('')
const email = ref('')
const emailCode = ref('')

const captchaKey = ref('')
const captchaCode = ref('')
const captchaImage = ref('')

async function loadCaptcha() {
  try {
    const captcha = await authApi.captcha()
    captchaKey.value = captcha.captchaKey
    captchaImage.value = captcha.captchaImage
  } catch {
    captchaImage.value = ''
  }
  captchaCode.value = ''
}

async function afterLogin() {
  const redirect = route.query.redirect
  await router.replace(typeof redirect === 'string' && redirect !== '' ? redirect : '/')
}

async function submit() {
  error.value = ''
  submitting.value = true
  try {
    if (mode.value === 'password') {
      await auth.login({
        username: username.value.trim(),
        password: password.value,
        captchaKey: captchaKey.value,
        captchaCode: captchaCode.value,
      })
    } else if (mode.value === 'sms') {
      await auth.smsLogin({ phone: phone.value.trim(), smsCode: smsCode.value })
    } else {
      await auth.emailLogin({ email: email.value.trim(), emailCode: emailCode.value })
    }
    await afterLogin()
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : '登录失败，请稍后重试'
    if (mode.value === 'password') {
      await loadCaptcha()
    }
  } finally {
    submitting.value = false
  }
}

async function sendCode() {
  error.value = ''
  sentHint.value = ''
  sending.value = true
  try {
    if (mode.value === 'sms') {
      await authApi.smsSend(phone.value.trim())
      sentHint.value = '验证码已发送，请查看服务端终端输出。'
    } else {
      await authApi.emailSend(email.value.trim())
      sentHint.value = '验证码已发送，请查看邮箱或服务端终端输出。'
    }
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : '发送失败，请稍后重试'
  } finally {
    sending.value = false
  }
}

onMounted(loadCaptcha)
</script>

<template>
  <main class="auth">
    <h1>登录</h1>
    <p class="hint">登录后即可管理你的笔记与知识库。</p>
    <p v-if="error" class="form-error">{{ error }}</p>
    <div class="tabs">
      <button :class="{ active: mode === 'password' }" type="button" @click="mode = 'password'">密码</button>
      <button :class="{ active: mode === 'sms' }" type="button" @click="mode = 'sms'">短信</button>
      <button :class="{ active: mode === 'email' }" type="button" @click="mode = 'email'">邮箱</button>
    </div>
    <form @submit.prevent="submit">
      <template v-if="mode === 'password'">
        <label class="field">
          用户名
          <input v-model="username" name="username" autocomplete="username" required />
        </label>
        <label class="field">
          密码
          <input v-model="password" name="password" type="password" autocomplete="current-password" required />
        </label>
        <div class="field">
          验证码
          <div class="captcha-row">
            <img v-if="captchaImage" :src="captchaImage" alt="验证码" title="点击刷新" class="captcha-img" @click="loadCaptcha" />
            <input v-model="captchaCode" inputmode="numeric" maxlength="4" autocomplete="off" required />
          </div>
        </div>
      </template>

      <template v-else-if="mode === 'sms'">
        <label class="field">
          手机号
          <input v-model="phone" name="phone" inputmode="numeric" autocomplete="tel" required />
        </label>
        <div class="field">
          验证码
          <div class="code-row">
            <input v-model="smsCode" inputmode="numeric" maxlength="6" autocomplete="one-time-code" required />
            <button type="button" class="btn" :disabled="sending" @click="sendCode">
              {{ sending ? '发送中…' : '发送验证码' }}
            </button>
          </div>
        </div>
      </template>

      <template v-else>
        <label class="field">
          邮箱
          <input v-model="email" name="email" type="email" autocomplete="email" required />
        </label>
        <div class="field">
          验证码
          <div class="code-row">
            <input v-model="emailCode" inputmode="numeric" maxlength="6" autocomplete="one-time-code" required />
            <button type="button" class="btn" :disabled="sending" @click="sendCode">
              {{ sending ? '发送中…' : '发送验证码' }}
            </button>
          </div>
        </div>
      </template>

      <button class="btn btn-primary submit" type="submit" :disabled="submitting">
        {{ submitting ? '登录中…' : '登录' }}
      </button>
    </form>
    <p v-if="sentHint" class="sent-hint">{{ sentHint }}</p>
    <p class="alt"><RouterLink to="/forgot">忘记密码？</RouterLink></p>
    <p class="alt">还没有账号？<RouterLink to="/register">注册</RouterLink></p>
    <p class="alt"><a href="/api/auth/sso/mock">统一身份认证登录</a></p>
  </main>
</template>

<style scoped>
.auth {
  max-width: 360px;
  margin: 72px auto;
  padding: 0 20px;
}
h1 {
  margin-bottom: 4px;
}
.hint {
  margin-top: 0;
  color: var(--fg-muted);
}
.tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
.tabs button {
  padding: 6px 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg);
  color: var(--fg-muted);
}
.tabs button.active {
  border-color: var(--accent);
  color: var(--accent);
}
.captcha-row,
.code-row {
  display: flex;
  gap: 8px;
  align-items: center;
}
.captcha-img {
  height: 40px;
  width: 140px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  cursor: pointer;
  background: var(--bg-soft);
}
.captcha-row input,
.code-row input {
  flex: 1;
  min-width: 0;
}
.submit {
  width: 100%;
  margin-top: 4px;
}
.sent-hint {
  margin-top: 12px;
  color: var(--fg-muted);
}
.alt {
  margin-top: 20px;
  color: var(--fg-muted);
}
</style>
