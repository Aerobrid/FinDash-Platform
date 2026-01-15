<script setup lang="ts">
import { RouterView, useRouter, useRoute } from 'vue-router'
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import axios from 'axios'
import { formatCurrencyCompact } from './utils/formatNumber'

interface User {
  id: string
  fullName: string
  email: string
}

interface Transaction {
  id: string
  senderId: string
  receiverId: string
  amount: number
  status: string
  timestamp: string
}

interface ActivityItem {
  id: string
  isIncoming: boolean
  amount: number
  otherName: string
  timestamp: string
  status: string
}

const router = useRouter()
const route = useRoute()

const currentUser = ref<any>(null)
const authState = ref(!!sessionStorage.getItem('userId'))
const isAuthenticated = computed(() => authState.value)
const isDarkMode = ref(false)
const showNotifications = ref(false)
const notifications = ref<ActivityItem[]>([])
const notificationsLoading = ref(false)
const hasUnread = ref(false)
const notificationPanelRef = ref<HTMLElement | null>(null)
const notificationBellRef = ref<HTMLElement | null>(null)
const NOTIFICATION_LAST_SEEN_KEY = 'notifications_last_seen'

const currentPage = computed(() => {
  const pages: Record<string, string> = {
    'dashboard': 'Dashboard',
    'contacts': 'Contacts',
    'analytics': 'Analytics',
    'settings': 'Settings'
  }
  return pages[route.name as string] || 'Dashboard'
})

function loadUser() {
  const user = sessionStorage.getItem('currentUser')
  if (user) {
    currentUser.value = JSON.parse(user)
  }
}

function toggleDarkMode() {
  isDarkMode.value = !isDarkMode.value
  const root = document.documentElement
  root.classList.toggle('dark', isDarkMode.value)
  localStorage.setItem('theme', isDarkMode.value ? 'dark' : 'light')
}

function loadDarkModePreference() {
  const saved = localStorage.getItem('theme')
  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
  isDarkMode.value = saved ? saved === 'dark' : prefersDark
  document.documentElement.classList.toggle('dark', isDarkMode.value)
}

function formatRelativeTime(timestamp: string) {
  const now = new Date()
  const then = new Date(timestamp)
  const diffMs = now.getTime() - then.getTime()
  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMs / 3600000)
  const diffDays = Math.floor(diffMs / 86400000)

  if (diffMins < 1) return 'Just now'
  if (diffMins < 60) return `${diffMins}m ago`
  if (diffHours < 24) return `${diffHours}h ago`
  if (diffDays < 7) return `${diffDays}d ago`
  return then.toLocaleDateString()
}

function markNotificationsSeen() {
  const now = new Date().toISOString()
  localStorage.setItem(NOTIFICATION_LAST_SEEN_KEY, now)
  hasUnread.value = false
}

function updateUnreadFlag() {
  const lastSeen = localStorage.getItem(NOTIFICATION_LAST_SEEN_KEY)
  const lastSeenTime = lastSeen ? new Date(lastSeen).getTime() : 0
  const latestTime = notifications.value.reduce((max, n) => Math.max(max, new Date(n.timestamp).getTime()), 0)
  hasUnread.value = notifications.value.length > 0 && latestTime > lastSeenTime
}

async function loadNotifications() {
  const userId = sessionStorage.getItem('userId')
  if (!userId) return

  notificationsLoading.value = true
  try {
    const [txRes, usersRes] = await Promise.all([
      axios.get(`/api/transaction/history/${userId}`),
      axios.get('/api/wallet/users')
    ])

    const usersById = new Map<string, User>(usersRes.data.map((u: User) => [u.id, u]))

    notifications.value = txRes.data
      .sort((a: Transaction, b: Transaction) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime())
      .slice(0, 12)
      .map((tx: Transaction) => {
        const isIncoming = tx.receiverId === userId
        const otherId = isIncoming ? tx.senderId : tx.receiverId
        const other = usersById.get(otherId)

        return {
          id: tx.id,
          isIncoming,
          amount: tx.amount,
          otherName: other?.fullName || 'Unknown',
          timestamp: tx.timestamp,
          status: tx.status
        }
      })
    updateUnreadFlag()
  } catch (err) {
    console.error('Failed to load notifications', err)
  } finally {
    notificationsLoading.value = false
  }
}

function toggleNotifications() {
  showNotifications.value = !showNotifications.value
  if (showNotifications.value) {
    markNotificationsSeen()
  }
}

async function refreshNotifications() {
  await loadNotifications()
  markNotificationsSeen()
}

function handleLogout() {
  sessionStorage.removeItem('currentUser')
  sessionStorage.removeItem('userId')
  sessionStorage.removeItem('token')
  currentUser.value = null
  authState.value = false
  notifications.value = []
  hasUnread.value = false
  router.push('/login')
}

function handleGlobalClick(event: MouseEvent) {
  if (!showNotifications.value) return
  const target = event.target as Node
  const insidePanel = notificationPanelRef.value?.contains(target)
  const insideBell = notificationBellRef.value?.contains(target)
  if (!insidePanel && !insideBell) {
    showNotifications.value = false
  }
}

// Watch route to update auth state
watch(async () => route.path, async () => {
  authState.value = !!sessionStorage.getItem('userId')
  if (authState.value) {
    loadUser()
    await loadNotifications()
    // Only mark as seen if the notifications dropdown is not currently showing
    if (!showNotifications.value) {
      markNotificationsSeen()
    }
  }
  showNotifications.value = false
})

onMounted(() => {
  loadUser()
  loadNotifications()
  loadDarkModePreference()
  document.addEventListener('click', handleGlobalClick)
})

onUnmounted(() => {
  document.removeEventListener('click', handleGlobalClick)
})
</script>

<template>
  <div v-if="isAuthenticated" class="min-h-screen bg-gradient-to-br from-slate-50 to-slate-100 dark:from-slate-950 dark:to-slate-900 text-slate-950 dark:text-slate-50">
    <!-- Modern Side Navigation -->
    <div class="flex h-screen overflow-hidden">
      <!-- Sidebar -->
      <aside class="hidden md:flex w-64 flex-col border-r border-slate-200/80 dark:border-slate-700/80 bg-white/80 dark:bg-slate-900/80 backdrop-blur-xl shadow-xl">
        <div class="flex h-16 items-center border-b border-slate-200/50 dark:border-slate-700/50 px-6 bg-white/50 dark:bg-slate-900/50">
          <div class="flex items-center gap-2.5">
            <div class="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-indigo-600 to-indigo-700 text-white shadow-lg shadow-indigo-500/30">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" class="h-5 w-5">
                <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" />
              </svg>
            </div>
            <span class="text-xl font-black tracking-tight bg-gradient-to-r from-slate-900 dark:from-slate-50 to-slate-700 dark:to-slate-200 bg-clip-text text-transparent">FinDash</span>
          </div>
        </div>
        
        <div class="flex flex-1 flex-col gap-2 p-4">
          <nav class="space-y-1.5">
            <router-link to="/dashboard" class="flex items-center gap-3 rounded-xl px-4 py-3 text-sm font-bold transition-all" :class="route.name === 'dashboard' ? 'bg-gradient-to-r from-indigo-600 to-indigo-700 text-white shadow-lg shadow-indigo-500/30' : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 hover:text-slate-900 dark:hover:text-slate-50'">
              <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect width="7" height="9" x="3" y="3" rx="1"/><rect width="7" height="5" x="14" y="3" rx="1"/><rect width="7" height="9" x="14" y="12" rx="1"/><rect width="7" height="5" x="3" y="16" rx="1"/></svg>
              Dashboard
            </router-link>
            <router-link to="/contacts" class="flex items-center gap-3 rounded-xl px-4 py-3 text-sm font-bold transition-all" :class="route.name === 'contacts' ? 'bg-gradient-to-r from-indigo-600 to-indigo-700 text-white shadow-lg shadow-indigo-500/30' : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 hover:text-slate-900 dark:hover:text-slate-50'">
              <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
              Contacts
            </router-link>
            <router-link to="/analytics" class="flex items-center gap-3 rounded-xl px-4 py-3 text-sm font-bold transition-all" :class="route.name === 'analytics' ? 'bg-gradient-to-r from-indigo-600 to-indigo-700 text-white shadow-lg shadow-indigo-500/30' : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 hover:text-slate-900 dark:hover:text-slate-50'">
              <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 3v18h18"/><path d="m19 9-5 5-4-4-3 3"/></svg>
              Analytics
            </router-link>
            <router-link to="/settings" class="flex items-center gap-3 rounded-xl px-4 py-3 text-sm font-bold transition-all" :class="route.name === 'settings' ? 'bg-gradient-to-r from-indigo-600 to-indigo-700 text-white shadow-lg shadow-indigo-500/30' : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 hover:text-slate-900 dark:hover:text-slate-50'">
              <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"/><circle cx="12" cy="12" r="3"/></svg>
              Settings
            </router-link>
          </nav>
        </div>

        <div class="mt-auto border-t border-slate-200/50 dark:border-slate-700/50 p-4 bg-white/50 dark:bg-slate-900/50 space-y-2">
          <router-link to="/settings" class="w-full flex items-center gap-3 px-2 py-2.5 rounded-xl hover:bg-slate-100 dark:hover:bg-slate-800 transition-all cursor-pointer group">
            <div class="h-9 w-9 rounded-full bg-gradient-to-br from-indigo-600 to-purple-600 border-2 border-white overflow-hidden shadow-lg">
              <img :src="`https://ui-avatars.com/api/?name=${currentUser?.fullName || 'User'}&background=4f46e5&color=fff`" alt="User" />
            </div>
            <div class="flex flex-col flex-1 text-left">
              <span class="text-xs font-bold text-slate-900 dark:text-slate-50">{{ currentUser?.fullName || 'User' }}</span>
              <span class="text-[10px] font-medium text-slate-500 dark:text-slate-400">{{ currentUser?.email || '' }}</span>
            </div>
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-4 h-4 text-slate-400 dark:text-slate-500 group-hover:text-slate-600 dark:group-hover:text-slate-300 transition-colors">
              <path fill-rule="evenodd" d="M8.22 5.22a.75.75 0 0 1 1.06 0l4.25 4.25a.75.75 0 0 1 0 1.06l-4.25 4.25a.75.75 0 0 1-1.06-1.06L11.94 10 8.22 6.28a.75.75 0 0 1 0-1.06Z" clip-rule="evenodd" />
            </svg>
          </router-link>
          <button @click="handleLogout" class="w-full flex items-center gap-3 px-4 py-2.5 rounded-xl text-sm font-bold text-rose-600 dark:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-900/20 transition-all">
            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
              <polyline points="16 17 21 12 16 7"/>
              <line x1="21" y1="12" x2="9" y2="12"/>
            </svg>
            Logout
          </button>
        </div>
      </aside>

      <!-- Main Content -->
      <main class="flex-1 overflow-y-auto bg-gradient-to-br from-slate-50 to-slate-100 dark:from-slate-950 dark:to-slate-900">
        <header class="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-slate-200/60 dark:border-slate-700/60 bg-white/70 dark:bg-slate-900/70 backdrop-blur-xl px-8 shadow-sm">
          <div class="flex items-center gap-3">
            <div class="text-sm font-semibold text-slate-500 dark:text-slate-400">Dashboard</div>
            <span class="text-slate-300 dark:text-slate-600">/</span> 
            <div class="text-sm font-bold text-slate-900 dark:text-slate-50">{{ currentPage }}</div>
          </div>
          <div class="flex items-center gap-4">
            <button ref="notificationBellRef" @click="toggleNotifications" class="relative p-2.5 rounded-xl border border-slate-200 dark:border-slate-700 text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-100 hover:bg-white dark:hover:bg-slate-800 hover:border-slate-300 dark:hover:border-slate-600 transition-all" title="Notifications">
              <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"/></svg>
              <span v-if="hasUnread" class="absolute top-1.5 right-1.5 w-2 h-2 bg-rose-500 rounded-full border-2 border-white dark:border-slate-900"></span>
            </button>
            <button @click="toggleDarkMode" class="relative p-2.5 rounded-xl border border-slate-200 text-slate-500 hover:text-slate-900 hover:bg-white hover:border-slate-300 transition-all" :title="isDarkMode ? 'Light Mode' : 'Dark Mode'">
              <svg v-if="!isDarkMode" xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"></path></svg>
              <svg v-else xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="5"></circle><line x1="12" y1="1" x2="12" y2="3"></line><line x1="12" y1="21" x2="12" y2="23"></line><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"></line><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"></line><line x1="1" y1="12" x2="3" y2="12"></line><line x1="21" y1="12" x2="23" y2="12"></line><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"></line><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"></line></svg>
            </button>
            <div class="flex items-center gap-2 px-3 py-1.5 rounded-full bg-emerald-50 dark:bg-emerald-900/30 border border-emerald-200 dark:border-emerald-700">
              <div class="w-2 h-2 bg-emerald-500 dark:bg-emerald-400 rounded-full animate-pulse"></div>
              <span class="text-xs font-bold text-emerald-700 dark:text-emerald-300">Live</span>
            </div>
          </div>
          
          <!-- Notifications Dropdown -->
          <div v-if="showNotifications" ref="notificationPanelRef" class="absolute top-16 right-8 w-96 bg-white dark:bg-slate-900 rounded-2xl border border-slate-200 dark:border-slate-700 shadow-xl z-50 overflow-hidden">
            <div class="p-4 border-b border-slate-100 dark:border-slate-800 sticky top-0 bg-white dark:bg-slate-900 rounded-t-2xl flex items-center justify-between">
              <div>
                <h3 class="text-sm font-bold text-slate-900 dark:text-slate-50">Activity Feed</h3>
                <p class="text-xs text-slate-500 dark:text-slate-400">Latest transfers and status</p>
              </div>
              <button @click="refreshNotifications" class="p-2 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-100 transition-colors" title="Refresh">
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8" />
                  <path d="M21 3v5h-5" />
                </svg>
              </button>
            </div>

            <div class="max-h-96 overflow-y-auto divide-y divide-slate-100 dark:divide-slate-800">
              <div v-if="notificationsLoading" class="p-4 flex items-center gap-2 text-sm text-slate-500 dark:text-slate-400">
                <svg class="w-4 h-4 animate-spin" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z"></path>
                </svg>
                Loading activity...
              </div>

              <div v-else-if="!notifications.length" class="p-4 text-sm text-slate-600 dark:text-slate-400">
                <p class="font-semibold text-slate-900 dark:text-slate-50">No recent activity</p>
                <p class="text-xs text-slate-500 dark:text-slate-500 mt-1">Transfers will appear here.</p>
              </div>

              <div v-else>
                <div 
                  v-for="item in notifications" 
                  :key="item.id"
                  class="flex items-start gap-3 p-4 hover:bg-slate-50 dark:hover:bg-slate-800/60 transition-colors"
                >
                  <div :class="[
                    'w-9 h-9 rounded-xl flex items-center justify-center text-white shadow-md',
                    item.isIncoming ? 'bg-emerald-500' : 'bg-indigo-500'
                  ]">
                    <svg v-if="item.isIncoming" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-4 h-4">
                      <path fill-rule="evenodd" d="M10 3a.75.75 0 01.75.75v10.638l3.96-4.158a.75.75 0 111.08 1.04l-5.25 5.5a.75.75 0 01-1.08 0l-5.25-5.5a.75.75 0 111.08-1.04l3.96 4.158V3.75A.75.75 0 0110 3z" clip-rule="evenodd" />
                    </svg>
                    <svg v-else xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-4 h-4">
                      <path fill-rule="evenodd" d="M10 17a.75.75 0 01-.75-.75V5.612L5.29 9.77a.75.75 0 01-1.08-1.04l5.25-5.5a.75.75 0 011.08 0l5.25 5.5a.75.75 0 11-1.08 1.04l-3.96-4.158V16.25A.75.75 0 0110 17z" clip-rule="evenodd" />
                    </svg>
                  </div>
                  <div class="flex-1 min-w-0">
                    <div class="flex items-center justify-between gap-2">
                      <p class="text-sm font-bold text-slate-900 dark:text-slate-50 truncate">
                        {{ item.isIncoming ? 'Received' : 'Sent' }} {{ formatCurrencyCompact(item.amount) }}
                      </p>
                      <span class="text-[10px] font-semibold px-2 py-1 rounded-full" :class="item.status === 'COMPLETED' ? 'bg-emerald-50 dark:bg-emerald-900/30 text-emerald-700 dark:text-emerald-200 border border-emerald-100 dark:border-emerald-800' : 'bg-amber-50 dark:bg-amber-900/30 text-amber-700 dark:text-amber-200 border border-amber-100 dark:border-amber-800'">
                        {{ item.status || 'PENDING' }}
                      </span>
                    </div>
                    <p class="text-xs text-slate-600 dark:text-slate-400 truncate">{{ item.isIncoming ? 'from' : 'to' }} {{ item.otherName }}</p>
                    <p class="text-[11px] text-slate-400 dark:text-slate-500 mt-1">{{ formatRelativeTime(item.timestamp) }}</p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </header>

        <div class="p-8">
          <RouterView />
        </div>
      </main>
    </div>
  </div>

  <!-- Not Authenticated -->
  <RouterView v-else />
</template>

<style>
@import url('https://fonts.googleapis.com/css2?family=Geist:wght@100;200;300;400;500;600;700;800;900&display=swap');

:root {
  font-family: 'Geist', sans-serif;
  -webkit-font-smoothing: antialiased;
}

/* shadcn-like scrollbar */
::-webkit-scrollbar {
  width: 6px;
}
::-webkit-scrollbar-track {
  background: transparent;
}
::-webkit-scrollbar-thumb {
  background: #e2e8f0;
  border-radius: 10px;
}
::-webkit-scrollbar-thumb:hover {
  background: #cbd5e1;
}
</style>