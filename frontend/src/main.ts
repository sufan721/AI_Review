import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { setUnauthorizedHandler } from './api/request'
import { useAuthStore } from './stores/auth'
import './styles/theme.css'
import './styles/ui.css'

setUnauthorizedHandler(() => {
  useAuthStore().logout()
  const current = router.currentRoute.value
  if (current.name !== 'login') {
    void router.replace({ name: 'login', query: { redirect: current.fullPath } })
  }
})

createApp(App).use(createPinia()).use(router).mount('#app')
