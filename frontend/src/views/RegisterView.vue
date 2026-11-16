<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { authApi } from '../api/auth'

const auth = useAuthStore()
const router = useRouter()

const username = ref('')
const nickname = ref('')
const phone = ref('')
const smsCode = ref('')
const email = ref('')
const password = ref('')
const confirmPassword = ref('')
const error = ref('')
const sentHint = ref('')
const sending = ref(false)
const submitting = ref(false)

async function sendCode() {
  error.value = ''
  sentHint.value = ''
  sending.value = true
  try {
    await authApi.smsSend(phone.value.trim())
    sentHint.value = '验证码已发送，请查看服务端终端输出。'
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : '发送失败，请稍后重试'
  } finally {
    sending.value = false
  }
}

async function submit() {
  error.value = ''
  if (password.value !== confirmPassword.value) {
    error.value = '两次输入的密码不一致'
    return
  }
  submitting.value = true
  try {
    await auth.register({
      username: username.value.trim(),
      password: password.value,
      confirmPassword: confirmPassword.value,
      phone: phone.value.trim(),
      smsCode: smsCode.value,
      nickname: nickname.value.trim() === '' ? undefined : nickname.value.trim(),
      email: email.value.trim() === '' ? undefined : email.value.trim(),
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
  <main class="auth">
    <h1>注册</h1>
    <p class="hint">用户名 3-32 位，仅限字母、数字和下划线；密码 8-32 位，含大小写字母、数字和特殊字符。</p>
    <p v-if="error" class="form-error">{{ error }}</p>
    <form @submit.prevent="submit">
      <label class="field">
        用户名
        <input v-model="username" name="username" autocomplete="username" required />
      </label>
      <label class="field">
        昵称（可选）
        <input v-model="nickname" name="nickname" autocomplete="nickname" />
      </label>
      <label class="field">
        手机号
        <input v-model="phone" name="phone" inputmode="numeric" maxlength="11" autocomplete="tel" required />
      </label>
      <div class="field">
        短信验证码
        <div class="code-row">
          <input v-model="smsCode" inputmode="numeric" maxlength="6" autocomplete="one-time-code" required />
          <button type="button" class="btn" :disabled="sending" @click="sendCode">
            {{ sending ? '发送中…' : '发送验证码' }}
          </button>
        </div>
      </div>
      <label class="field">
        邮箱（可选）
        <input v-model="email" name="email" type="email" autocomplete="email" />
      </label>
      <label class="field">
        密码
        <input v-model="password" name="password" type="password" autocomplete="new-password" required />
      </label>
      <label class="field">
        确认密码
        <input v-model="confirmPassword" name="confirmPassword" type="password" autocomplete="new-password" required />
      </label>
      <button class="btn btn-primary submit" type="submit" :disabled="submitting">
        {{ submitting ? '注册中…' : '注册' }}
      </button>
    </form>
    <p v-if="sentHint" class="sent-hint">{{ sentHint }}</p>
    <p class="alt">已有账号？<RouterLink to="/login">登录</RouterLink></p>
  </main>
</template>

<style scoped>
.auth {
  max-width: 360px;
  margin: 64px auto;
  padding: 0 20px;
}
h1 {
  margin-bottom: 4px;
}
.hint {
  margin-top: 0;
  color: var(--fg-muted);
}
.code-row {
  display: flex;
  gap: 8px;
  align-items: center;
}
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
