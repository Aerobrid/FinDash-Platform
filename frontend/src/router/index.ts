import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
import Dashboard from '../views/Dashboard.vue'
import Contacts from '../views/Contacts.vue'
import ContactDetail from '../views/ContactDetail.vue'
import Analytics from '../views/Analytics.vue'
import Security from '../views/Security.vue'

const isAuthenticated = () => {
  return !!localStorage.getItem('userId')
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: () => isAuthenticated() ? '/dashboard' : '/login'
    },
    {
      path: '/login',
      name: 'login',
      component: Login,
      beforeEnter: (to, from, next) => {
        if (isAuthenticated()) {
          next('/dashboard')
        } else {
          next()
        }
      }
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

router.beforeEach((to, from, next) => {
  if (to.meta.requiresAuth && !isAuthenticated()) {
    next('/login')
  } else {
    next()
  }
})

export default router