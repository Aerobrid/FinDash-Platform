import { ref, computed } from 'vue'
import axios from 'axios'

interface User {
  id: string
  fullName: string
  email: string
}

const currentUser = ref<User | null>(null)
const authState = ref(false)
const isAuthLoading = ref(false)

export function useAuth() {
  const isAuthenticated = computed(() => authState.value)

  async function loadUser() {
    // Prevent multiple simultaneous loads
    if (isAuthLoading.value) {
      return authState.value
    }

    try {
      isAuthLoading.value = true
      const res = await axios.get('/api/wallet/me', {
        timeout: 5000,
        validateStatus: () => true // Accept all status codes
      })
      
      if (res.status === 200 && res.data && res.data.userId) {
        currentUser.value = {
          id: res.data.userId,
          fullName: res.data.fullName,
          email: res.data.email
        }
        authState.value = true
        return true
      }
      
      // 401 or any other error means not authenticated
      authState.value = false
      currentUser.value = null
      return false
    } catch (err) {
      authState.value = false
      currentUser.value = null
      return false
    } finally {
      isAuthLoading.value = false
    }
  }

  function logout() {
    currentUser.value = null
    authState.value = false
  }

  async function logoutAndClearCookie() {
    try {
      await axios.post('/api/wallet/logout', {}, { timeout: 5000 })
    } catch (err) {
      // Ignore errors
    }
    logout()
  }

  return {
    currentUser,
    isAuthenticated,
    isAuthLoading,
    loadUser,
    logout,
    logoutAndClearCookie
  }
}
