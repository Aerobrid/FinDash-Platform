import { createApp } from 'vue'
import './style.css'
import App from './App.vue'
import router from './router'
import './axios-config' // Configure axios interceptors
import { useAuth } from './composables/useAuth'

// Initialize auth state once on app startup
const auth = useAuth()
auth.loadUser().then(() => {
  const app = createApp(App)
  app.use(router)
  app.mount('#app')
})