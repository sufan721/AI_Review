<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const router = useRouter()

const navItems = [
  { label: '笔记', to: '/notes' },
  { label: '文件', to: '/documents' },
  { label: '知识库', to: '/knowledge-bases' },
]

const displayName = computed(() => auth.user?.nickname ?? auth.user?.username ?? '未登录')
const initial = computed(() => displayName.value.slice(0, 1).toUpperCase())

const menuOpen = ref(false)
const menuRef = ref<HTMLElement | null>(null)

function closeMenu(event: MouseEvent): void {
  if (menuRef.value !== null && !menuRef.value.contains(event.target as Node)) menuOpen.value = false
}

async function logout(): Promise<void> {
  menuOpen.value = false
  auth.logout()
  await router.replace({ name: 'login' })
}

onMounted(() => document.addEventListener('click', closeMenu))
onBeforeUnmount(() => document.removeEventListener('click', closeMenu))
</script>

<template>
  <div class="shell">
    <header class="topbar">
      <div class="topbar__inner">
        <RouterLink class="brand" to="/">
          <span class="brand__mark" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M4 5a2 2 0 0 1 2-2h8l6 6v10a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z" />
              <path d="M14 3v6h6" />
              <path d="M8 13h8M8 17h5" />
            </svg>
          </span>
          <span class="brand__name">备忘录</span>
        </RouterLink>

        <nav class="nav" aria-label="主导航">
          <RouterLink v-for="item in navItems" :key="item.to" class="nav__link" :to="item.to">
            {{ item.label }}
          </RouterLink>
        </nav>

        <div class="account">
          <RouterLink class="btn btn-primary btn-sm new-btn" :to="{ name: 'note-new' }">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round">
              <path d="M12 5v14M5 12h14" />
            </svg>
            新建
          </RouterLink>

          <div ref="menuRef" class="menu-wrap">
            <button
              class="user"
              type="button"
              :aria-expanded="menuOpen"
              aria-haspopup="menu"
              @click="menuOpen = !menuOpen"
            >
              <span class="avatar avatar--sm">{{ initial }}</span>
              <span class="user__name">{{ displayName }}</span>
              <svg class="user__caret" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="m6 9 6 6 6-6" />
              </svg>
            </button>

            <div v-if="menuOpen" class="menu" role="menu">
              <RouterLink class="menu__item" :to="{ name: 'profile' }" role="menuitem" @click="menuOpen = false">
                个人资料
              </RouterLink>
              <RouterLink class="menu__item" :to="{ name: 'settings' }" role="menuitem" @click="menuOpen = false">
                偏好设置
              </RouterLink>
              <RouterLink class="menu__item" :to="{ name: 'admin' }" role="menuitem" @click="menuOpen = false">
                管理后台
              </RouterLink>
              <hr class="menu__sep" />
              <button class="menu__item menu__item--danger" type="button" role="menuitem" @click="logout">
                退出登录
              </button>
            </div>
          </div>
        </div>
      </div>
    </header>

    <main class="body">
      <!-- 嵌套路由出口：AppLayout 作为父路由组件，子页面必须由 RouterView 渲染 -->
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg-canvas);
}

/* 顶栏：半透明 + 毛玻璃，滚动时内容从下方穿过 */
.topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  border-bottom: 1px solid var(--border);
  background: color-mix(in srgb, var(--bg) 88%, transparent);
  backdrop-filter: saturate(180%) blur(12px);
}

.topbar__inner {
  display: flex;
  align-items: center;
  gap: var(--space-5);
  width: 100%;
  max-width: 1180px;
  margin: 0 auto;
  padding: 0 var(--space-6);
  height: 56px;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  flex: none;
  color: var(--fg);
  font-size: 15px;
  font-weight: 650;
  letter-spacing: -0.01em;
}

.brand:hover {
  opacity: 1;
}

.brand__mark {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: linear-gradient(135deg, #5b8cf7, var(--accent));
  color: #fff;
  box-shadow: 0 2px 6px color-mix(in srgb, var(--accent) 35%, transparent);
}

.brand__mark svg {
  width: 16px;
  height: 16px;
}

.nav {
  display: flex;
  align-items: center;
  gap: 2px;
  min-width: 0;
}

.nav__link {
  padding: 6px 10px;
  border-radius: var(--radius-sm);
  color: var(--fg-muted);
  font-size: 13.5px;
  font-weight: 500;
  white-space: nowrap;
  transition: background-color var(--dur-fast) var(--ease), color var(--dur-fast) var(--ease);
}

.nav__link:hover {
  background: var(--bg-soft);
  color: var(--fg);
  opacity: 1;
}

.nav__link.router-link-active {
  background: var(--accent-soft);
  color: var(--accent);
  font-weight: 600;
}

.account {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-left: auto;
  flex: none;
}

.new-btn svg {
  width: 14px;
  height: 14px;
}

.menu-wrap {
  position: relative;
}

.user {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  height: 34px;
  padding: 0 6px 0 4px;
  border: 1px solid transparent;
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--fg);
  transition: background-color var(--dur-fast) var(--ease), border-color var(--dur-fast) var(--ease);
}

.user:hover {
  border-color: var(--border);
  background: var(--bg);
}

.user__name {
  max-width: 110px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13.5px;
  font-weight: 500;
}

.user__caret {
  width: 13px;
  height: 13px;
  color: var(--fg-subtle);
}

.menu {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 30;
  min-width: 168px;
  padding: 5px;
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: var(--bg);
  box-shadow: var(--shadow-lg);
  animation: menu-in 0.14s var(--ease);
}

@keyframes menu-in {
  from {
    opacity: 0;
    transform: translateY(-4px) scale(0.98);
  }
}

.menu__item {
  display: flex;
  align-items: center;
  width: 100%;
  height: 32px;
  padding: 0 10px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--fg);
  font-size: 13.5px;
  text-align: left;
  transition: background-color var(--dur-fast) var(--ease), color var(--dur-fast) var(--ease);
}

.menu__item:hover {
  background: var(--bg-soft);
  opacity: 1;
}

.menu__item--danger {
  color: var(--danger);
}

.menu__item--danger:hover {
  background: var(--danger-soft);
}

.menu__sep {
  height: 1px;
  margin: 5px 4px;
  border: none;
  background: var(--border);
}

.body {
  flex: 1;
  width: 100%;
  padding: var(--space-8) var(--space-6) var(--space-12);
}

@media (max-width: 1080px) {
  .topbar__inner {
    gap: var(--space-3);
    padding: 0 var(--space-4);
  }

  .nav {
    overflow-x: auto;
    scrollbar-width: none;
  }

  .nav::-webkit-scrollbar {
    display: none;
  }

  .user__name {
    display: none;
  }
}

@media (max-width: 760px) {
  .topbar__inner {
    height: auto;
    padding: var(--space-2) var(--space-4) 0;
    flex-wrap: wrap;
    row-gap: var(--space-2);
  }

  .nav {
    order: 3;
    width: 100%;
    padding-bottom: var(--space-2);
  }

  .body {
    padding: var(--space-5) var(--space-4) var(--space-10);
  }
}
</style>
