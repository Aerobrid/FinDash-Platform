import { createRouter, createWebHistory } from 'vue-router'
import { useAuth } from '../composables/useAuth'
import Login from '../views/Login.vue'
import Dashboard from '../views/Dashboard.vue'
import Contacts from '../views/Contacts.vue'
import ContactDetail from '../views/ContactDetail.vue'
import Analytics from '../views/Analytics.vue'
import Security from '../views/Security.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/dashboard'
    },
    {
      path: '/login',
      name: 'login',
      component: Login
    },
    {
      path: '/dashboard',
      name: 'dashboard',
      component: Dashboard,
      meta: { requiresAuth: true }
    },
    {
      path: '/contacts',
      name: 'contacts',
      component: Contacts,
      meta: { requiresAuth: true }
    },
    {
      path: '/contacts/:id',
      name: 'contact-detail',
      component: ContactDetail,
      meta: { requiresAuth: true }
    },
    {
      path: '/analytics',
      name: 'analytics',
      component: Analytics,
      meta: { requiresAuth: true }
    },
    {
      path: '/settings',
      name: 'settings',
      component: Security,
      meta: { requiresAuth: true }
    }
  ]
})

// Single auth instance
const auth = useAuth()

router.beforeEach((to, from, next) => {
  // Public routes - proceed immediately
  if (!to.meta.requiresAuth) {
    next()
    return
  }

  // Protected route - check current auth state (no async call)
  if (auth.isAuthenticated.value) {
    next()
  } else {
    next('/login')
  }
})

export default router