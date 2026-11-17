<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AnimatedCharacters from '../components/auth/AnimatedCharacters.vue'
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
const showPassword = ref(false)
const typing = ref(false)
const passwordActive = computed(() => mode.value === 'password' && password.value.length > 0)

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
  <main class="login-page">
    <aside class="login-art">
      <div class="brand"><span class="brand-mark">✦</span><span>备忘录</span></div>
      <div class="art-content">
        <h2>欢迎回来</h2>
        <p>记录想法，整理知识，让灵感随时触手可及。</p>
        <AnimatedCharacters :typing="typing" :password-active="passwordActive" :password-visible="showPassword" />
      </div>
      <div class="art-footer">你的每一份记录，都值得被认真保存。</div>
    </aside>
    <section class="login-main">
      <div class="auth">
    <div class="mobile-brand"><span class="brand-mark">✦</span>备忘录</div>
    <h1>欢迎回来！</h1>
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
          <input v-model="username" name="username" autocomplete="username" required @focus="typing = true" @blur="typing = false" />
        </label>
        <label class="field">
          密码
          <span class="password-row">
            <input v-model="password" name="password" :type="showPassword ? 'text' : 'password'" autocomplete="current-password" required @focus="typing = true" @blur="typing = false" />
            <button type="button" class="password-toggle" :aria-label="showPassword ? '隐藏密码' : '显示密码'" :aria-pressed="showPassword" @click="showPassword = !showPassword">{{ showPassword ? '隐藏' : '显示' }}</button>
          </span>
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
    <p class="alt sso"><a href="/api/auth/sso/mock">统一身份认证登录</a></p>
      </div>
    </section>
  </main>
</template>

<style scoped>
.login-page { min-height: 100vh; display: grid; grid-template-columns: minmax(0, 1fr) minmax(440px, 1fr); }
.login-art { position: relative; display: flex; flex-direction: column; justify-content: space-between; overflow: hidden; padding: 40px 48px 0; color: white; background: var(--auth-panel); }
.brand, .mobile-brand { display: flex; align-items: center; gap: 10px; font-size: 19px; font-weight: 700; }
.brand-mark { display: inline-grid; place-items: center; width: 34px; height: 34px; border-radius: 9px; background: rgb(255 255 255 / 16%); }
.art-content { display: flex; flex-direction: column; align-items: center; justify-content: flex-end; flex: 1; text-align: center; }
.art-content h2 { margin: 28px 0 3px; font-size: clamp(28px, 3vw, 40px); }
.art-content p { margin: 0 0 48px; opacity: .82; }
.art-footer { padding: 18px 0 28px; font-size: 13px; opacity: .68; }
.login-main { display: flex; align-items: center; justify-content: center; padding: 48px 32px; }
.auth {
  width: 100%; max-width: 420px;
}
h1 {
  margin: 0 0 4px; text-align: center; font-size: 30px;
}
.hint {
  margin: 0 0 32px; text-align: center;
  color: var(--fg-muted);
}
.mobile-brand { display: none; justify-content: center; margin-bottom: 48px; }
.mobile-brand .brand-mark { color: var(--accent); background: var(--bg-soft); }
.tabs {
  display: flex;
  gap: 8px; margin-bottom: 24px;
}
.tabs button {
  flex: 1; padding: 9px 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg);
  color: var(--fg-muted);
}
.tabs button.active {
  border-color: var(--accent);
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 7%, var(--bg));
}
.field { font-size: 14px; font-weight: 600; }
.field input { width: 100%; min-height: 46px; font-weight: 400; }
.password-row { position: relative; display: block; }
.password-row input { padding-right: 60px; }
.password-toggle { position: absolute; right: 9px; top: 50%; transform: translateY(-50%); border: 0; background: transparent; color: var(--fg-muted); font-size: 13px; }
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
  margin-top: 6px; min-height: 48px;
}
.sent-hint {
  margin-top: 12px;
  color: var(--fg-muted);
}
.alt {
  margin: 14px 0 0; text-align: center;
  color: var(--fg-muted);
}
.sso { margin-top: 24px; padding-top: 20px; border-top: 1px solid var(--border); }
@media (max-width: 1000px) {
  .login-page { display: block; }
  .login-art { display: none; }
  .login-main { min-height: 100vh; }
  .mobile-brand { display: flex; }
}
</style>
