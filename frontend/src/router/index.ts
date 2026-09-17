import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '../components/layout/AppLayout.vue'
import LoginView from '../views/LoginView.vue'
import NoteEditView from '../views/NoteEditView.vue'
import NoteListView from '../views/NoteListView.vue'
import RegisterView from '../views/RegisterView.vue'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: AppLayout,
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: { name: 'notes' } },
        { path: 'notes', name: 'notes', component: NoteListView },
        { path: 'notes/new', name: 'note-new', component: NoteEditView },
        { path: 'notes/:id', name: 'note-edit', component: NoteEditView, props: true },
      ],
    },
    { path: '/login', name: 'login', component: LoginView },
    { path: '/register', name: 'register', component: RegisterView },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  // 刷新页面后本地令牌还在，先恢复用户信息再判定路由
  await auth.restoreSession()

  if (to.meta.requiresAuth === true && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if ((to.name === 'login' || to.name === 'register') && auth.isAuthenticated) {
    return { name: 'notes' }
  }
  return true
})

export default router
