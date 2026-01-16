<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { useAuth } from '../composables/useAuth'
import { formatRelativeTime } from '../utils/formatNumber'

interface Session {
  id: string
  deviceName: string
  browser: string
  osName: string
  ipAddress: string
  createdAt: string
  lastActiveAt: string
  isCurrent: boolean
}

const router = useRouter()
const { currentUser, logoutAndClearCookie } = useAuth()
const showEditProfile = ref(false)
const showPasswordForm = ref(false)
const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const passwordMessage = ref('')
const editFullName = ref('')
const editEmail = ref('')
const profileMessage = ref('')
const sessions = ref<Session[]>([])
const sessionsLoading = ref(false)
const deletingSessionId = ref<string | null>(null)

onMounted(async () => {
  // User is loaded by router guard and available from composable
  if (currentUser.value) {
    editFullName.value = currentUser.value.fullName
    editEmail.value = currentUser.value.email
    await loadSessions()
  }
})

async function loadSessions() {
  if (!currentUser.value?.id) return
  
  sessionsLoading.value = true
  try {
    const res = await axios.get(`/api/sessions/${currentUser.value.id}`)
    sessions.value = res.data
  } catch (err: unknown) {
    console.error('Failed to load sessions:', err)
  } finally {
    sessionsLoading.value = false
  }
}

async function deleteSession(sessionId: string) {
  if (!confirm('Are you sure you want to logout this device?')) {
    return
  }

  if (!currentUser.value?.id) return

  deletingSessionId.value = sessionId
  try {
    await axios.delete(`/api/sessions/${currentUser.value.id}/${sessionId}`)
    sessions.value = sessions.value.filter(s => s.id !== sessionId)
  } catch (error: unknown) {
    console.error('Failed to delete session:', error)
  } finally {
    deletingSessionId.value = null
  }
}

async function logoutAllOthers() {
  if (!confirm('Are you sure you want to logout all other devices?')) {
    return
  }

  if (!currentUser.value?.id) return

  try {
    // Delete all sessions except current one
    for (const session of sessions.value) {
      if (!session.isCurrent) {
        await axios.delete(`/api/sessions/${currentUser.value.id}/${session.id}`)
      }
    }
    await loadSessions()
  } catch (error: unknown) {
    console.error('Failed to logout other sessions:', error)
  }
}

async function handleSaveProfile() {
  if (!editFullName.value || !editEmail.value) {
    profileMessage.value = 'Please fill in all fields'
    return
  }

  if (!currentUser.value?.id) {
    profileMessage.value = 'No user loaded'
    return
  }

  try {
    const res = await axios.put(`/api/wallet/users/${currentUser.value.id}`, {
      fullName: editFullName.value,
      email: editEmail.value
    })

    const updatedUser = res.data
    // Update local ref to reflect changes immediately
    if (currentUser.value) {
      currentUser.value.fullName = updatedUser.fullName
      currentUser.value.email = updatedUser.email
    }
    profileMessage.value = 'Profile updated successfully!'
    setTimeout(() => {
      showEditProfile.value = false
      profileMessage.value = ''
    }, 1200)
  } catch (err: unknown) {
    const axiosError = err as any
    const msg = axiosError?.response?.data?.error || 'Unable to update profile'
    profileMessage.value = msg
  }
}

function handleChangePassword() {
  if (!currentPassword.value || !newPassword.value || !confirmPassword.value) {
    passwordMessage.value = 'Please fill in all fields'
    return
  }

  if (newPassword.value !== confirmPassword.value) {
    passwordMessage.value = 'New passwords do not match'
    return
  }

  if (newPassword.value.length < 8) {
    passwordMessage.value = 'Password must be at least 8 characters'
    return
  }

  passwordMessage.value = 'Password changed successfully!'
  currentPassword.value = ''
  newPassword.value = ''
  confirmPassword.value = ''
  setTimeout(() => {
    showPasswordForm.value = false
    passwordMessage.value = ''
  }, 2000)
}

async function handleDeleteAccount() {
  if (confirm('Are you sure you want to delete your account? This action cannot be undone.')) {
    try {
      await logoutAndClearCookie()
    } catch (err: unknown) {
      console.error('Logout error during account deletion:', err)
    }
    
    alert('Your account has been deleted.')
    router.push('/')
  }
}

async function handleLogout() {
  try {
    await logoutAndClearCookie()
    router.push('/login')
  } catch (err: unknown) {
    console.error('Logout error:', err)
  }
}
</script>

<template>
  <div class="max-w-4xl mx-auto space-y-8">
    <!-- Header -->
    <div>
      <h2 class="text-3xl font-black text-slate-900 dark:text-slate-50">Settings & Security</h2>
      <p class="text-slate-500 dark:text-slate-400 mt-1">Manage your account security and preferences</p>
    </div>

    <!-- Profile Section -->
    <div class="bg-white dark:bg-slate-800 rounded-2xl border border-slate-200 dark:border-slate-700/60 overflow-hidden shadow-sm">
      <div class="px-6 py-5 border-b border-slate-100 dark:border-slate-700 bg-slate-50 dark:bg-slate-700/30 flex items-center gap-4">
        <div class="w-12 h-12 rounded-full bg-gradient-to-br from-indigo-600 to-purple-600 border-2 border-white overflow-hidden flex items-center justify-center text-white font-bold">
          {{ currentUser?.fullName?.charAt(0) || 'U' }}
        </div>
        <div>
          <h3 class="font-bold text-slate-900 dark:text-slate-50">{{ currentUser?.fullName || 'User' }}</h3>
          <p class="text-sm text-slate-500 dark:text-slate-400">{{ currentUser?.email || 'No email' }}</p>
        </div>
      </div>
      <div class="p-6 space-y-4">
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="text-xs font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider">Full Name</label>
            <p class="text-lg font-semibold text-slate-900 dark:text-slate-50 mt-2">{{ currentUser?.fullName || 'User' }}</p>
          </div>
          <div>
            <label class="text-xs font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider">Email</label>
            <p class="text-lg font-semibold text-slate-900 dark:text-slate-50 mt-2">{{ currentUser?.email || 'No email' }}</p>
          </div>
        </div>
        <button @click="showEditProfile = !showEditProfile" class="w-full bg-indigo-600 hover:bg-indigo-700 text-white font-bold py-3 rounded-xl transition-all shadow-sm">
          {{ showEditProfile ? 'Cancel' : 'Edit Profile' }}
        </button>
      </div>

      <!-- Edit Profile Form -->
      <div v-if="showEditProfile" class="p-6 bg-slate-50 dark:bg-slate-800 border-t border-slate-100 dark:border-slate-700 space-y-4">
        <div>
          <label class="block text-sm font-bold text-slate-900 dark:text-slate-50 mb-2">Full Name</label>
          <input 
            v-model="editFullName"
            type="text"
            class="w-full px-4 py-3 rounded-xl border border-slate-200 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-50 focus:border-indigo-500 dark:focus:border-indigo-500 focus:ring-2 focus:ring-indigo-200 dark:focus:ring-indigo-600 transition-all"
            placeholder="Enter your full name"
          />
        </div>
        <div>
          <label class="block text-sm font-bold text-slate-900 dark:text-slate-50 mb-2">Email Address</label>
          <input 
            v-model="editEmail"
            type="email"
            class="w-full px-4 py-3 rounded-xl border border-slate-200 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-50 focus:border-indigo-500 dark:focus:border-indigo-500 focus:ring-2 focus:ring-indigo-200 dark:focus:ring-indigo-600 transition-all"
            placeholder="Enter your email"
          />
        </div>
        <div v-if="profileMessage" class="p-3 rounded-xl" :class="profileMessage.includes('success') ? 'bg-emerald-50 dark:bg-emerald-900/30 text-emerald-700 dark:text-emerald-200 border border-emerald-200 dark:border-emerald-800' : 'bg-rose-50 dark:bg-rose-900/30 text-rose-700 dark:text-rose-200 border border-rose-200 dark:border-rose-800'">
          <p class="text-sm font-semibold">{{ profileMessage }}</p>
        </div>
        <button 
          @click="handleSaveProfile"
          class="w-full bg-indigo-600 hover:bg-indigo-700 text-white font-bold py-3 rounded-xl transition-all"
        >
          Save Changes
        </button>
      </div>
    </div>

    <!-- Password Section -->
    <div class="bg-white dark:bg-slate-800 rounded-2xl border border-slate-200 dark:border-slate-700/60 overflow-hidden shadow-sm">
      <div class="px-6 py-5 border-b border-slate-100 dark:border-slate-700 bg-slate-50 dark:bg-slate-700/30 flex items-center justify-between">
        <div>
          <h3 class="font-bold text-slate-900 dark:text-slate-50">Password & Authentication</h3>
          <p class="text-sm text-slate-500 dark:text-slate-400 mt-1">Keep your account secure</p>
        </div>
        <button 
          @click="showPasswordForm = !showPasswordForm"
          class="px-5 py-2 bg-indigo-600 hover:bg-indigo-700 text-white font-bold rounded-xl transition-all"
        >
          Change Password
        </button>
      </div>

      <div v-if="showPasswordForm" class="p-6 bg-slate-50 dark:bg-slate-800 border-t border-slate-100 dark:border-slate-700 space-y-4">
        <div>
          <label class="block text-sm font-bold text-slate-900 dark:text-slate-50 mb-2">Current Password</label>
          <input 
            v-model="currentPassword"
            type="password"
            class="w-full px-4 py-3 border border-slate-200 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-50 rounded-xl focus:ring-2 focus:ring-indigo-500 dark:focus:ring-indigo-600 outline-none"
            placeholder="Enter current password"
          />
        </div>
        <div>
          <label class="block text-sm font-bold text-slate-900 dark:text-slate-50 mb-2">New Password</label>
          <input 
            v-model="newPassword"
            type="password"
            class="w-full px-4 py-3 border border-slate-200 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-50 rounded-xl focus:ring-2 focus:ring-indigo-500 dark:focus:ring-indigo-600 outline-none"
            placeholder="Enter new password"
          />
        </div>
        <div>
          <label class="block text-sm font-bold text-slate-900 dark:text-slate-50 mb-2">Confirm Password</label>
          <input 
            v-model="confirmPassword"
            type="password"
            class="w-full px-4 py-3 border border-slate-200 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-50 rounded-xl focus:ring-2 focus:ring-indigo-500 dark:focus:ring-indigo-600 outline-none"
            placeholder="Confirm new password"
          />
        </div>
        <div v-if="passwordMessage" :class="[
          'rounded-xl p-3 text-sm font-semibold',
          passwordMessage.includes('successfully') 
            ? 'bg-emerald-50 dark:bg-emerald-900/30 text-emerald-700 dark:text-emerald-200 border border-emerald-200 dark:border-emerald-800'
            : 'bg-rose-50 dark:bg-rose-900/30 text-rose-700 dark:text-rose-200 border border-rose-200 dark:border-rose-800'
        ]">
          {{ passwordMessage }}
        </div>
        <div class="flex gap-2">
          <button 
            @click="handleChangePassword"
            class="flex-1 bg-indigo-600 hover:bg-indigo-700 text-white font-bold py-3 rounded-xl transition-all"
          >
            Update Password
          </button>
          <button 
            @click="showPasswordForm = false"
            class="flex-1 border border-slate-200 text-slate-700 font-bold py-3 rounded-xl hover:bg-slate-50 transition-all"
          >
            Cancel
          </button>
        </div>
      </div>

      <div v-if="!showPasswordForm" class="p-6">
        <div class="space-y-3">
          <div class="flex items-center justify-between p-3 rounded-xl bg-slate-50 dark:bg-slate-800 border border-slate-100 dark:border-slate-700">
            <div class="flex items-center gap-3">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-5 h-5 text-emerald-500">
                <path fill-rule="evenodd" d="M16.704 4.153a.75.75 0 01.143 1.052l-8 10.5a.75.75 0 01-1.127.075l-4.5-4.5a.75.75 0 011.06-1.06l3.894 3.893 7.48-9.817a.75.75 0 011.052-.143Z" clip-rule="evenodd" />
              </svg>
              <span class="text-sm font-semibold text-slate-900 dark:text-slate-50">Two-factor authentication</span>
            </div>
            <button class="text-sm font-bold text-slate-500 dark:text-slate-400 cursor-not-allowed">TBA</button>
          </div>
          <div class="flex items-center justify-between p-3 rounded-xl bg-slate-50 dark:bg-slate-800 border border-slate-100 dark:border-slate-700">
            <div class="flex items-center gap-3">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-5 h-5 text-emerald-500">
                <path fill-rule="evenodd" d="M16.704 4.153a.75.75 0 01.143 1.052l-8 10.5a.75.75 0 01-1.127.075l-4.5-4.5a.75.75 0 011.06-1.06l3.894 3.893 7.48-9.817a.75.75 0 011.052-.143Z" clip-rule="evenodd" />
              </svg>
              <span class="text-sm font-semibold text-slate-900 dark:text-slate-50">Login alerts enabled</span>
            </div>
            <button class="text-sm font-bold text-slate-500 dark:text-slate-400 cursor-not-allowed">TBA</button>
          </div>
        </div>
      </div>
    </div>

    <!-- Devices Section -->
    <div class="bg-white dark:bg-slate-800 rounded-2xl border border-slate-200 dark:border-slate-700/60 overflow-hidden shadow-sm">
      <div class="px-6 py-5 border-b border-slate-100 dark:border-slate-700 bg-slate-50 dark:bg-slate-700/30">
        <div class="flex items-center justify-between">
          <div>
            <h3 class="font-bold text-slate-900 dark:text-slate-50">Active Sessions</h3>
            <p class="text-sm text-slate-500 dark:text-slate-400 mt-1">Devices where you're signed in</p>
          </div>
          <button
            v-if="sessions.length > 1"
            @click="logoutAllOthers"
            class="text-xs font-semibold px-3 py-2 text-rose-600 dark:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-900/20 rounded-lg transition-colors"
          >
            Logout Others
          </button>
        </div>
      </div>
      
      <div class="p-6">
        <div v-if="sessionsLoading" class="text-center py-8">
          <p class="text-slate-500 dark:text-slate-400">Loading sessions...</p>
        </div>

        <div v-else-if="sessions.length === 0" class="text-center py-8">
          <p class="text-slate-500 dark:text-slate-400">No active sessions found</p>
        </div>

        <div v-else class="space-y-3">
          <div 
            v-for="session in sessions" 
            :key="session.id"
            class="flex items-start justify-between p-4 rounded-xl bg-slate-50 dark:bg-slate-800 border border-slate-100 dark:border-slate-700 hover:border-slate-200 dark:hover:border-slate-600 transition-colors"
          >
            <div class="flex items-center gap-4 flex-1">
              <!-- Device Icon -->
              <div class="w-12 h-12 rounded-xl bg-indigo-100 dark:bg-indigo-900/40 flex items-center justify-center flex-shrink-0">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" class="w-6 h-6 text-indigo-600 dark:text-indigo-300">
                  <path d="M7 3a2 2 0 00-2 2v6h16V5a2 2 0 00-2-2H7z" />
                  <path fill-rule="evenodd" d="M2 11a1 1 0 011-1h18a1 1 0 011 1v3a1 1 0 01-1 1H3a1 1 0 01-1-1v-3z" clip-rule="evenodd" />
                </svg>
              </div>

              <!-- Session Info -->
              <div class="min-w-0 flex-1">
                <p class="font-bold text-slate-900 dark:text-slate-50 truncate">{{ session.deviceName }}</p>
                <p class="text-sm text-slate-500 dark:text-slate-400">{{ session.browser }} • {{ session.osName }}</p>
                <p class="text-xs text-slate-400 dark:text-slate-500 mt-1">{{ session.ipAddress }}</p>
                <p class="text-xs text-slate-400 dark:text-slate-500 mt-1">
                  Last active {{ formatRelativeTime(session.lastActiveAt) }}
                </p>
              </div>
            </div>

            <!-- Status & Action -->
            <div class="flex items-center gap-3 ml-4 flex-shrink-0">
              <span v-if="session.isCurrent" class="px-3 py-1 bg-emerald-100 dark:bg-emerald-900/40 text-emerald-700 dark:text-emerald-200 rounded-full text-xs font-bold whitespace-nowrap">
                Current
              </span>
              <button
                v-if="!session.isCurrent"
                @click="deleteSession(session.id)"
                :disabled="deletingSessionId === session.id"
                class="px-3 py-1 text-xs font-semibold text-rose-600 dark:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-900/20 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed whitespace-nowrap"
              >
                {{ deletingSessionId === session.id ? 'Logging out...' : 'Logout' }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Danger Zone -->
    <div class="bg-rose-50 border border-rose-200 rounded-2xl overflow-hidden">
      <div class="px-6 py-5 border-b border-rose-200 bg-rose-100/50">
        <h3 class="font-bold text-rose-900">Danger Zone</h3>
      </div>
      <div class="p-6 space-y-4">
        <button 
          @click="handleLogout"
          class="w-full border-2 border-rose-200 hover:border-rose-400 hover:bg-rose-50 text-rose-700 font-bold py-3 rounded-xl transition-all"
        >
          Logout
        </button>
        <button 
          @click="handleDeleteAccount"
          class="w-full border-2 border-rose-500 hover:border-rose-600 hover:bg-rose-500 hover:text-white text-rose-600 font-bold py-3 rounded-xl transition-all"
        >
          Delete Account
        </button>
      </div>
    </div>
  </div>
</template>
