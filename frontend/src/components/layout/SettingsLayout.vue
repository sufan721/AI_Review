<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../../stores/auth'

type AdminSection = 'users' | 'policies' | 'rag' | 'persistence' | 'providers' | 'settings'

const route = useRoute()
const auth = useAuthStore()

const isAdmin = computed(() => auth.user?.roles.includes('ROLE_ADMIN') ?? false)

const adminItems: { id: AdminSection; label: string; icon: string }[] = [
  { id: 'users', label: '成员管理', icon: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75' },
  { id: 'policies', label: '访问与策略', icon: 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z' },
  { id: 'rag', label: 'RAG 系统', icon: 'M12 2v4M12 18v4M4.9 4.9l2.8 2.8M16.3 16.3l2.8 2.8M2 12h4M18 12h4M4.9 19.1l2.8-2.8M16.3 7.7l2.8-2.8M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8z' },
  { id: 'persistence', label: '数据持久化', icon: 'M4 7c0-2 2.7-3 8-3s8 1 8 3-2.7 3-8 3-8-1-8-3zM4 7v5c0 2 2.7 3 8 3s8-1 8-3V7M4 12v5c0 2 2.7 3 8 3s8-1 8-3v-5' },
  { id: 'providers', label: '身份源', icon: 'M9 12a3 3 0 1 0 6 0 3 3 0 0 0-6 0M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6l1.4 1.4M17 17l1.4 1.4M18.4 5.6L17 7M7 17l-1.4 1.4' },
  { id: 'settings', label: '原始设置', icon: 'M4 6h16M4 12h16M4 18h16M8 4v4M16 10v4M10 16v4' },
]

const adminSection = computed<AdminSection>(() => {
  const value = route.query.section
  return typeof value === 'string' && adminItems.some((item) => item.id === value)
    ? value as AdminSection
    : 'users'
})

const accountItems = [
  { name: 'profile', label: '个人资料', icon: 'M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z' },
  { name: 'settings', label: '偏好设置', icon: 'M4 6h16M4 12h16M4 18h16M8 4v4M16 10v4M10 16v4' },
]
</script>

<template>
  <div class="settings-shell">
    <aside class="side" aria-label="设置导航">
      <div class="side__head">
        <span class="side__title">设置</span>
      </div>
      <nav class="side__nav">
        <p class="side__label">账号</p>
        <RouterLink
          v-for="item in accountItems"
          :key="item.name"
          class="side__item"
          :class="{ 'is-active': route.name === item.name }"
          :to="{ name: item.name }"
        >
          <svg class="side__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path :d="item.icon" />
          </svg>
          <span>{{ item.label }}</span>
        </RouterLink>

        <template v-if="isAdmin">
          <p class="side__label side__label--group">实例管理</p>
          <RouterLink
            v-for="item in adminItems"
            :key="item.id"
            class="side__item"
            :class="{ 'is-active': route.name === 'admin' && adminSection === item.id }"
            :to="{ name: 'admin', query: { section: item.id } }"
          >
            <svg class="side__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <path :d="item.icon" />
            </svg>
            <span>{{ item.label }}</span>
          </RouterLink>
        </template>
      </nav>
    </aside>

    <div class="content">
      <RouterView />
    </div>
  </div>
</template>

<style scoped>
.settings-shell {
  display: grid;
  grid-template-columns: 208px minmax(0, 1fr);
  gap: var(--space-6);
  align-items: start;
  width: 100%;
  max-width: 1280px;
  margin: 0 auto;
}

.side {
  position: sticky;
  top: var(--space-6);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: var(--bg);
  box-shadow: var(--shadow-xs);
  padding: var(--space-4) var(--space-3);
}

.side__head {
  padding: 0 var(--space-2) var(--space-3);
  border-bottom: 1px solid var(--border);
}

.side__title {
  font-size: 15px;
  font-weight: 650;
}

.side__nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-top: var(--space-3);
}

.side__label {
  margin: 0 0 var(--space-1);
  padding: 0 var(--space-2);
  color: var(--fg-subtle);
  font-size: 11.5px;
  font-weight: 600;
  letter-spacing: 0.06em;
}

.side__label--group {
  margin-top: var(--space-3);
  padding-top: var(--space-3);
  border-top: 1px solid var(--border);
}

.side__item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  height: 36px;
  padding: 0 var(--space-3);
  border-radius: var(--radius);
  color: var(--fg-muted);
  font-size: 13.5px;
  font-weight: 500;
  white-space: nowrap;
  transition: background-color var(--dur-fast) var(--ease), color var(--dur-fast) var(--ease);
}

.side__item:hover {
  background: var(--bg-soft);
  color: var(--fg);
  opacity: 1;
}

.side__item.is-active {
  background: var(--accent-soft);
  color: var(--accent);
  font-weight: 600;
}

.side__icon {
  flex: none;
  width: 16px;
  height: 16px;
}

.content {
  min-width: 0;
}

@media (max-width: 900px) {
  .settings-shell {
    grid-template-columns: 1fr;
    gap: var(--space-4);
  }

  .side {
    position: static;
  }

  .side__nav {
    flex-direction: row;
    flex-wrap: wrap;
    gap: var(--space-1);
  }

  .side__label {
    display: none;
  }

  .side__item {
    width: auto;
    padding: 7px var(--space-3);
  }
}
</style>
