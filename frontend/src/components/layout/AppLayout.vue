<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const router = useRouter()

const navItems = [
  {
    label: '笔记', to: '/notes',
    icon: 'M5 3h10l4 4v14H5zM15 3v4h4M8 12h8M8 16h5',
  },
  {
    label: '文件', to: '/documents',
    icon: 'M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z',
  },
  {
    label: '知识库', to: '/knowledge-bases',
    icon: 'M4 5a2 2 0 0 1 2-2h13v18H6a2 2 0 0 1-2-2zM4 17h15M9 7h5',
  },
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
    <!-- 左侧全高图标条：贴屏幕左边缘竖排，只显示图标，hover 时向右弹出文字 -->
    <aside class="sidebar" aria-label="主导航">
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

      <RouterLink class="btn btn-primary new-btn" :to="{ name: 'note-new' }" data-tip="新建">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round">
          <path d="M12 5v14M5 12h14" />
        </svg>
        <span>新建</span>
      </RouterLink>

      <nav class="side-nav" aria-label="功能导航">
        <p class="side-nav__label">工作区</p>
        <RouterLink
          v-for="item in navItems"
          :key="item.to"
          class="side-nav__link"
          :to="item.to"
          :data-tip="item.label"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path :d="item.icon" />
          </svg>
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>

      <div class="side-foot">
        <div ref="menuRef" class="menu-wrap">
          <button
            class="user"
            :class="{ 'menu-open': menuOpen }"
            type="button"
            :data-tip="displayName"
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
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M4 6h16M4 12h16M4 18h16M8 4v4M16 10v4M10 16v4" />
              </svg>
              设置
            </RouterLink>
            <hr class="menu__sep" />
            <button class="menu__item menu__item--danger" type="button" role="menuitem" @click="logout">
              退出登录
            </button>
          </div>
        </div>
      </div>
    </aside>

    <main class="body">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.shell {
  display: flex;
  min-height: 100vh;
  background: var(--bg-canvas);
}

/* 左侧全高图标条：贴屏幕左边缘，只保留图标，文字由 hover 提示承载 */
.sidebar {
  position: sticky;
  top: 0;
  display: flex;
  flex: none;
  flex-direction: column;
  align-items: center;
  gap: var(--space-4);
  width: 64px;
  height: 100vh;
  padding: var(--space-5) var(--space-2);
  border-right: 1px solid var(--border);
  background: var(--bg);
}

/* 图标条：隐藏所有文字，仅保留图标 */
.brand__name,
.new-btn span,
.side-nav__label,
.side-nav__link span,
.user__name,
.user__caret {
  display: none;
}

.brand {
  display: grid;
  place-items: center;
  padding: 0;
  color: var(--fg);
}

.brand:hover {
  opacity: 1;
}

.brand__mark {
  display: grid;
  place-items: center;
  flex: none;
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

.new-btn {
  flex: none;
  justify-content: center;
  width: 40px;
  padding: 0;
}

.new-btn svg {
  flex: none;
  width: 16px;
  height: 16px;
}

.side-nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.side-nav__link {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0;
  width: 40px;
  height: 40px;
  padding: 0;
  border-radius: var(--radius);
  color: var(--fg-muted);
  white-space: nowrap;
  transition: background-color var(--dur-fast) var(--ease), color var(--dur-fast) var(--ease);
}

.side-nav__link svg {
  flex: none;
  width: 18px;
  height: 18px;
}

.side-nav__link:hover {
  background: var(--bg-soft);
  color: var(--fg);
  opacity: 1;
}

.side-nav__link.router-link-active,
.side-nav__link.is-active {
  background: var(--accent-soft);
  color: var(--accent);
}

/* 侧栏不设 overflow：账户菜单与 hover 提示都从侧栏右侧弹出，任何裁剪都会切掉它们。
   导航项数量有限（3 项），无需内部滚动。 */
.side-foot {
  margin-top: auto;
  min-width: 0;
}

.menu-wrap {
  position: relative;
}

.user {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0;
  width: 40px;
  height: 40px;
  padding: 0;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-sunken);
  color: var(--fg);
  transition: background-color var(--dur-fast) var(--ease), border-color var(--dur-fast) var(--ease);
}

.user:hover {
  border-color: var(--border-strong);
  background: var(--bg-soft);
}

/* 菜单从账户按钮向右弹出，避免被侧栏裁切 */
.menu {
  position: absolute;
  bottom: 0;
  left: calc(100% + var(--space-2));
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
    transform: translateY(4px) scale(0.98);
  }
}

.menu__item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
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

.menu__item svg {
  flex: none;
  width: 15px;
  height: 15px;
  color: var(--fg-subtle);
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
  min-width: 0;
  padding: var(--space-8) var(--space-6) var(--space-12);
}

/* 悬停文字提示：从图标右侧由左向右滑出 */
[data-tip] {
  position: relative;
}

[data-tip]::after {
  content: attr(data-tip);
  position: absolute;
  left: calc(100% + 12px);
  top: 50%;
  z-index: 50;
  padding: 6px 10px;
  border-radius: 6px;
  background: var(--fg);
  color: var(--bg);
  font-size: 12.5px;
  font-weight: 500;
  line-height: 1;
  white-space: nowrap;
  pointer-events: none;
  opacity: 0;
  transform: translateY(-50%) translateX(-8px);
  transform-origin: left center;
  transition: opacity var(--dur-fast) var(--ease), transform var(--dur-fast) var(--ease);
}

[data-tip]::before {
  content: '';
  position: absolute;
  left: calc(100% + 9px);
  top: 50%;
  z-index: 51;
  width: 7px;
  height: 7px;
  border-radius: 1px;
  background: var(--fg);
  pointer-events: none;
  opacity: 0;
  transform: translateY(-50%) translateX(-8px) rotate(45deg);
  transition: opacity var(--dur-fast) var(--ease), transform var(--dur-fast) var(--ease);
}

[data-tip]:hover::after,
[data-tip]:focus-visible::after {
  opacity: 1;
  transform: translateY(-50%) translateX(0);
}

[data-tip]:hover::before,
[data-tip]:focus-visible::before {
  opacity: 1;
  transform: translateY(-50%) translateX(0) rotate(45deg);
}

/* 账户菜单展开时隐藏头像提示，避免与菜单重叠 */
.user.menu-open::after,
.user.menu-open::before {
  display: none;
}

@media (max-width: 640px) {
  .body {
    padding: var(--space-6) var(--space-4) var(--space-10);
  }
}
</style>
