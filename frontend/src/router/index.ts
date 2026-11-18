import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '../components/layout/AppLayout.vue'
import LoginView from '../views/LoginView.vue'
import NoteEditView from '../views/NoteEditView.vue'
import NoteListView from '../views/NoteListView.vue'
import DocumentListView from '../views/DocumentListView.vue'
import RegisterView from '../views/RegisterView.vue'
import KnowledgeBaseListView from '../views/KnowledgeBaseListView.vue'
import KnowledgeBaseEditView from '../views/KnowledgeBaseEditView.vue'
import KnowledgeBaseDetailView from '../views/KnowledgeBaseDetailView.vue'
import ForgotPasswordView from '../views/ForgotPasswordView.vue'
import SsoCallbackView from '../views/SsoCallbackView.vue'
import ProfileView from '../views/ProfileView.vue'
import SettingsView from '../views/SettingsView.vue'
import SettingsLayout from '../components/layout/SettingsLayout.vue'
import AdminView from '../views/AdminView.vue'
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
        { path: 'documents', name: 'documents', component: DocumentListView },
        { path: 'knowledge-bases', name: 'knowledge-bases', component: KnowledgeBaseListView },
        { path: 'knowledge-bases/new', name: 'knowledge-base-new', component: KnowledgeBaseEditView },
        { path: 'knowledge-bases/:id', name: 'knowledge-base-detail', component: KnowledgeBaseDetailView, props: true },
        { path: 'knowledge-bases/:id/edit', name: 'knowledge-base-edit', component: KnowledgeBaseEditView, props: true },
        // 设置与管理统一收在「头像 → 设置」入口下，个人设置与管理分区共用同一条侧栏
        {
          path: 'settings',
          component: SettingsLayout,
          redirect: { name: 'profile' },
          children: [
            { path: 'profile', name: 'profile', component: ProfileView },
            { path: 'preferences', name: 'settings', component: SettingsView },
            { path: 'admin', name: 'admin', component: AdminView },
          ],
        },
      ],
    },
    { path: '/login', name: 'login', component: LoginView },
    { path: '/register', name: 'register', component: RegisterView },
    { path: '/forgot', name: 'forgot', component: ForgotPasswordView },
    { path: '/sso-callback', name: 'sso-callback', component: SsoCallbackView },
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
