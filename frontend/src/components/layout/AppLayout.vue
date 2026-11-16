<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const router = useRouter()

async function logout() {
  auth.logout()
  await router.replace({ name: 'login' })
}
</script>

<template>
  <div class="shell">
    <header class="topbar">
      <RouterLink class="brand" to="/">备忘录</RouterLink>
      <nav class="nav">
        <RouterLink to="/notes">笔记</RouterLink>
        <RouterLink to="/documents">文件</RouterLink>
        <RouterLink to="/knowledge-bases">知识库</RouterLink>
      </nav>
      <RouterLink class="btn btn-primary create-note" :to="{ name: 'note-new' }">新建笔记</RouterLink>
      <div class="account">
        <span class="name">{{ auth.user?.nickname ?? auth.user?.username }}</span>
        <button class="btn" type="button" @click="logout">退出</button>
      </div>
    </header>
    <div class="body">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
.topbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 12px 24px;
  border-bottom: 1px solid var(--border);
  background: var(--bg-soft);
}
.brand {
  font-weight: 600;
  color: var(--fg);
}
.nav { display: flex; gap: 16px; margin-left: auto; }
.nav a { color: var(--fg-muted); }
.nav a.router-link-active { color: var(--accent); font-weight: 600; }
.account {
  display: flex;
  align-items: center;
  gap: 12px;
}
.name {
  color: var(--fg-muted);
}
.body {
  flex: 1;
  padding: 24px;
}
@media (max-width: 640px) {
  .topbar { align-items: center; padding: 10px 16px; }
  .nav { order: 3; width: 100%; margin-left: 0; }
  .create-note { margin-left: auto; }
  .name { max-width: 88px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .body { padding: 16px; }
}
</style>
