<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { authApi } from '../api/auth'

const router = useRouter()

const account = ref('')
const resetCode = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const sent = ref(false)
const error = ref('')
const hint = ref('')
const sending = ref(false)
const submitting = ref(false)

async function sendCode() {
  error.value = ''
  hint.value = ''
  sending.value = true
  try {
    await authApi.forgotSendCode(account.value.trim())
    sent.value = true
    hint.value = '如果该账号存在，重置验证码已发送（请查看服务端终端输出）。'
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : '发送失败，请稍后重试'
  } finally {
    sending.value = false
  }
}

async function submit() {
  error.value = ''
  if (newPassword.value !== confirmPassword.value) {
    error.value = '两次输入的密码不一致'
    return
  }
  submitting.value = true
  try {
    await authApi.forgotReset({
      account: account.value.trim(),
      resetCode: resetCode.value,
      newPassword: newPassword.value,
      confirmPassword: confirmPassword.value,
    })
    await router.replace('/login')
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : '重置失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="auth">
    <h1>找回密码</h1>
    <p class="hint">输入注册时的手机号或邮箱，获取重置验证码。</p>
    <p v-if="error" class="form-error">{{ error }}</p>
    <form @submit.prevent="submit">
      <label class="field">
        账号（手机号或邮箱）
        <input v-model="account" name="account" autocomplete="username" required />
      </label>
      <div class="field">
        重置验证码
        <div class="code-row">
          <input v-model="resetCode" inputmode="numeric" maxlength="6" autocomplete="one-time-code" required />
          <button type="button" class="btn" :disabled="sending" @click="sendCode">
            {{ sending ? '发送中…' : '发送验证码' }}
          </button>
        </div>
      </div>
      <label class="field">
        新密码
        <input v-model="newPassword" type="password" autocomplete="new-password" required />
      </label>
      <label class="field">
        确认新密码
        <input v-model="confirmPassword" type="password" autocomplete="new-password" required />
      </label>
      <button class="btn btn-primary submit" type="submit" :disabled="submitting || !sent">
        {{ submitting ? '重置中…' : '重置密码' }}
      </button>
    </form>
    <p v-if="hint" class="sent-hint">{{ hint }}</p>
    <p class="alt"><RouterLink to="/login">返回登录</RouterLink></p>
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
