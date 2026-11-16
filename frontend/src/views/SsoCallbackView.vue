<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { setRefreshToken, setToken } from '../api/request'

const route = useRoute()
const router = useRouter()

onMounted(() => {
  const accessToken = route.query.accessToken
  const refreshToken = route.query.refreshToken
  if (typeof accessToken === 'string' && accessToken !== '') {
    setToken(accessToken)
    setRefreshToken(typeof refreshToken === 'string' ? refreshToken : null)
    void router.replace('/')
  } else {
    void router.replace({ name: 'login' })
  }
})
</script>

<template>
  <main class="auth">
    <p class="hint">正在完成登录…</p>
  </main>
</template>

<style scoped>
.auth {
  max-width: 360px;
  margin: 96px auto;
  padding: 0 20px;
  text-align: center;
}
.hint {
  color: var(--fg-muted);
}
</style>
