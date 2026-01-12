<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { getDeviceInfo } from '../utils/deviceDetection'

const router = useRouter()
const email = ref('')
const password = ref('')
const fullName = ref('')
const loading = ref(false)
const error = ref('')
const activeTab = ref('login') // 'login' or 'signup'
const isAuthenticated = computed(() => !!localStorage.getItem('userId'))
const currentUser = ref<any>(null)

onMounted(() => {
  if (isAuthenticated.value) {
    const user = localStorage.getItem('currentUser')
    if (user) {
      currentUser.value = JSON.parse(user)
    }
  }
})

async function createSession(userId: string) {
  try {
    const deviceInfo = getDeviceInfo()
    // Get IP address from a simple API call
    const ipRes = await axios.get('https://api.ipify.org?format=json').catch(() => ({ data: { ip: 'Unknown' } }))
    const ipAddress = ipRes.data.ip

    await axios.post(`/api/sessions/${userId}`, {
      deviceName: deviceInfo.deviceName,
      browser: deviceInfo.browser,
      osName: deviceInfo.osName,
      ipAddress
    })
  } catch (err) {
    console.error('Failed to create session:', err)
  }
}

async function handleLogin() {
  if (!email.value || !password.value) {
    error.value = 'Please enter email and password'
    return
  }

  loading.value = true
  error.value = ''

  try {
    const res = await axios.post('/api/wallet/login', {
      email: email.value,
      password: password.value
    })
    
    if (res.status === 200) {
      const data = res.data
      // Store JWT token and user info
      localStorage.setItem('token', data.token)
      localStorage.setItem('userId', data.userId)
      localStorage.setItem('currentUser', JSON.stringify({
        id: data.userId,
        fullName: data.fullName,
        email: data.email
      }))
      
      // Set axios default header for future requests
      axios.defaults.headers.common['Authorization'] = `Bearer ${data.token}`
      
      // Create session record
      await createSession(data.userId)
      
      router.push('/dashboard')
    }
  } catch (err: any) {
    error.value = err.response?.data?.error || 'Invalid credentials'
    console.error('Login error:', err)
  } finally {
    loading.value = false
  }
}

async function handleSignup() {
  if (!fullName.value || !email.value || !password.value) {
    error.value = 'Please fill in all fields'
    return
  }

  loading.value = true
  error.value = ''

  try {
    const res = await axios.post('/api/wallet/users', {
      fullName: fullName.value,
      email: email.value,
      password: password.value
    })

    if (res.status === 200) {
      const data = res.data
      // Store JWT token and user info
      localStorage.setItem('token', data.token)
      localStorage.setItem('userId', data.userId)
      localStorage.setItem('currentUser', JSON.stringify({
        id: data.userId,
        fullName: data.fullName,
        email: data.email
      }))
      
      // Set axios default header
      axios.defaults.headers.common['Authorization'] = `Bearer ${data.token}`
      
      // Create session record
      await createSession(data.userId)
      
      router.push('/dashboard')
    }
  } catch (err: any) {
    error.value = err.response?.data?.error || 'Signup failed. Please try again.'
    console.error('Signup error:', err)
  } finally {
    loading.value = false
  }
}

function switchTab(tab: string) {
  activeTab.value = tab
  error.value = ''
}

function handleLogout() {
  localStorage.removeItem('currentUser')
  localStorage.removeItem('userId')
  error.value = ''
  activeTab.value = 'login'
  email.value = ''
  password.value = ''
  fullName.value = ''
  currentUser.value = null
}
</script>

<template>
  <!-- Logged In View -->
  <div v-if="isAuthenticated" class="min-h-screen bg-gradient-to-br from-slate-50 to-slate-100 flex items-center justify-center p-4">
    <div class="w-full max-w-md">
      <div class="bg-white rounded-2xl shadow-2xl p-8 space-y-6">
        <div class="text-center">
          <div class="h-20 w-20 rounded-full bg-gradient-to-br from-indigo-600 to-purple-600 border-4 border-white overflow-hidden shadow-lg mx-auto mb-4">
            <img :src="`https://ui-avatars.com/api/?name=${currentUser?.fullName || 'User'}&background=4f46e5&color=fff&size=80`" alt="User" />
          </div>
          <h2 class="text-2xl font-black text-slate-900 mb-2">{{ currentUser?.fullName }}</h2>
          <p class="text-slate-500 text-sm font-medium">{{ currentUser?.email }}</p>
        </div>

        <div class="bg-gradient-to-r from-indigo-50 to-purple-50 border border-indigo-200 rounded-xl p-4">
          <p class="text-sm text-slate-700 font-semibold text-center">You are logged in. Return to dashboard or log out.</p>
        </div>

        <div class="space-y-3">
          <button 
            @click="router.push('/dashboard')"
            class="w-full bg-indigo-600 hover:bg-indigo-700 text-white font-bold py-3 rounded-xl transition-all active:scale-95"
          >
            Return to Dashboard
          </button>
          <button 
            @click="handleLogout"
            class="w-full bg-slate-200 hover:bg-slate-300 text-slate-900 font-bold py-3 rounded-xl transition-all active:scale-95"
          >
            Logout
          </button>
        </div>
      </div>
    </div>
  </div>

  <!-- Login/Signup View -->
  <div v-else class="min-h-screen bg-gradient-to-br from-indigo-600 to-purple-700 flex items-center justify-center p-4">
    <div class="w-full max-w-md">
      <!-- Logo -->
      <div class="text-center mb-8">
        <div class="flex items-center justify-center gap-3 mb-6">
          <div class="w-12 h-12 rounded-xl bg-white/20 backdrop-blur-xl flex items-center justify-center">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" class="w-6 h-6">
              <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" />
            </svg>
          </div>
          <div>
            <h1 class="text-3xl font-black text-white">FinDash</h1>
            <p class="text-indigo-200 text-sm font-medium">Financial Dashboard</p>
          </div>
        </div>
      </div>

      <!-- Card -->
      <div class="bg-white rounded-2xl shadow-2xl p-8 space-y-6">
        <!-- Tabs -->
        <div class="flex gap-2 border-b border-slate-200">
          <button
            @click="switchTab('login')"
            :class="[
              'flex-1 py-3 font-bold text-center transition-all',
              activeTab === 'login'
                ? 'text-indigo-600 border-b-2 border-indigo-600'
                : 'text-slate-500 hover:text-slate-700'
            ]"
          >
            Sign In
          </button>
          <button
            @click="switchTab('signup')"
            :class="[
              'flex-1 py-3 font-bold text-center transition-all',
              activeTab === 'signup'
                ? 'text-indigo-600 border-b-2 border-indigo-600'
                : 'text-slate-500 hover:text-slate-700'
            ]"
          >
            Create Account
          </button>
        </div>

        <!-- Error Message -->
        <div v-if="error" class="bg-rose-50 border border-rose-200 rounded-xl p-4 text-rose-800 text-sm font-semibold flex items-center gap-3">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-5 h-5">
            <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.28 7.22a.75.75 0 00-1.06 1.06L8.94 10l-1.72 1.72a.75.75 0 101.06 1.06L10 11.06l1.72 1.72a.75.75 0 101.06-1.06L11.06 10l1.72-1.72a.75.75 0 00-1.06-1.06L10 8.94 8.28 7.22z" clip-rule="evenodd" />
          </svg>
          {{ error }}
        </div>

        <!-- Login Tab -->
        <div v-if="activeTab === 'login'" class="space-y-4">
          <div>
            <h2 class="text-2xl font-black text-slate-900 mb-2">Welcome back</h2>
            <p class="text-slate-500 text-sm">Sign in to your account to continue</p>
          </div>

          <form @submit.prevent="handleLogin" class="space-y-4">
            <div>
              <label class="block text-sm font-bold text-slate-900 mb-2">Email</label>
              <input 
                v-model="email"
                type="email"
                placeholder="your@email.com"
                class="w-full px-4 py-3 border border-slate-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:border-transparent outline-none transition-all"
                :disabled="loading"
              />
            </div>

            <div>
              <label class="block text-sm font-bold text-slate-900 mb-2">Password</label>
              <input 
                v-model="password"
                type="password"
                placeholder="••••••••"
                class="w-full px-4 py-3 border border-slate-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:border-transparent outline-none transition-all"
                :disabled="loading"
              />
            </div>

            <button 
              type="submit"
              :disabled="loading"
              class="w-full bg-indigo-600 hover:bg-indigo-700 disabled:bg-slate-300 text-white font-bold py-3 rounded-xl transition-all active:scale-95"
            >
              {{ loading ? 'Signing in...' : 'Sign In' }}
            </button>
          </form>
        </div>

        <!-- Signup Tab -->
        <div v-if="activeTab === 'signup'" class="space-y-4">
          <div>
            <h2 class="text-2xl font-black text-slate-900 mb-2">Create Account</h2>
            <p class="text-slate-500 text-sm">Join FinDash to start managing your finances</p>
          </div>

          <form @submit.prevent="handleSignup" class="space-y-4">
            <div>
              <label class="block text-sm font-bold text-slate-900 mb-2">Full Name</label>
              <input 
                v-model="fullName"
                type="text"
                placeholder="John Doe"
                class="w-full px-4 py-3 border border-slate-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:border-transparent outline-none transition-all"
                :disabled="loading"
              />
            </div>

            <div>
              <label class="block text-sm font-bold text-slate-900 mb-2">Email</label>
              <input 
                v-model="email"
                type="email"
                placeholder="john@example.com"
                class="w-full px-4 py-3 border border-slate-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:border-transparent outline-none transition-all"
                :disabled="loading"
              />
            </div>

            <div>
              <label class="block text-sm font-bold text-slate-900 mb-2">Password</label>
              <input 
                v-model="password"
                type="password"
                placeholder="••••••••"
                class="w-full px-4 py-3 border border-slate-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:border-transparent outline-none transition-all"
                :disabled="loading"
              />
            </div>

            <button 
              type="submit"
              :disabled="loading"
              class="w-full bg-indigo-600 hover:bg-indigo-700 disabled:bg-slate-300 text-white font-bold py-3 rounded-xl transition-all active:scale-95"
            >
              {{ loading ? 'Creating account...' : 'Create Account' }}
            </button>
          </form>
        </div>
      </div>

    </div>
  </div>
</template>
