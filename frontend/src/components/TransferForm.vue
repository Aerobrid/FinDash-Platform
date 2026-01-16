<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import axios from 'axios'
import { useAuth } from '../composables/useAuth'

interface User {
  id: string
  fullName: string
  email: string
}

interface Props {
  loading?: boolean
  balance?: number | null
  preselectedContact?: { id: string; fullName: string; email: string }
}

interface Emits {
  (e: 'transfer', payload: { receiverId: string; amount: number }): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

// Use shared auth composable
const { currentUser } = useAuth()
const currentUserId = computed(() => currentUser.value?.id || '')

const allUsers = ref<User[]>([])
const recipientSearch = ref('')
const recipientId = ref('')
const amount = ref<number | null>(null)
const showResults = ref(false)
const searchResults = ref<User[]>([])
const insufficientBalance = ref(false)

const isAmountValid = () => {
  if (!amount.value || amount.value <= 0) return false
  if (props.balance === null || props.balance === undefined) return true
  if (amount.value > props.balance) {
    insufficientBalance.value = true
    return false
  }
  insufficientBalance.value = false
  return true
}

onMounted(async () => {
  try {
    const res = await axios.get('/api/wallet/users')
    allUsers.value = res.data
    
    // If preselected contact is provided, set it
    if (props.preselectedContact) {
      recipientId.value = props.preselectedContact.id
      recipientSearch.value = props.preselectedContact.fullName
      showResults.value = false
    }
  } catch (err) {
    console.error('Failed to load users', err)
  }
})

const searchRecipients = () => {
  if (!recipientSearch.value.trim()) {
    searchResults.value = []
    showResults.value = false
    return
  }

  const query = recipientSearch.value.toLowerCase()
  searchResults.value = allUsers.value.filter(user =>
    user.id !== currentUserId.value && // Don't show self
    (user.fullName.toLowerCase().includes(query) ||
     user.email.toLowerCase().includes(query))
  )
  showResults.value = true
}

const selectRecipient = (user: User) => {
  recipientId.value = user.id
  recipientSearch.value = user.fullName
  showResults.value = false
}

const handleSubmit = () => {
  if (recipientId.value && isAmountValid()) {
    emit('transfer', {
      receiverId: recipientId.value,
      amount: amount.value!
    })
    // Reset form
    recipientId.value = ''
    recipientSearch.value = ''
    amount.value = null
    insufficientBalance.value = false
  }
}
</script>

<template>
  <div class="bg-gradient-to-br from-indigo-600 to-indigo-700 rounded-2xl p-8 shadow-xl relative overflow-hidden">
    <div class="absolute inset-0 bg-grid-white/[0.05] bg-[size:20px_20px]"></div>
    <div class="absolute inset-0 bg-gradient-to-t from-indigo-900/20 to-transparent"></div>
    
    <div class="relative z-10">
      <div class="flex items-center justify-between mb-6">
        <div>
          <h3 class="text-xl font-black text-white tracking-tight">New Transfer</h3>
          <p class="text-indigo-200 text-sm font-medium mt-1">Send money instantly</p>
        </div>
        <div class="w-12 h-12 rounded-xl bg-white/10 backdrop-blur-xl flex items-center justify-center">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" class="w-6 h-6 text-white">
            <path d="M3.478 2.405a.75.75 0 00-.926.94l2.432 7.905H13.5a.75.75 0 010 1.5H4.984l-2.432 7.905a.75.75 0 00.926.94 60.519 60.519 0 0018.445-8.986.75.75 0 000-1.218A60.517 60.517 0 003.478 2.405z" />
          </svg>
        </div>
      </div>

      <form @submit.prevent="handleSubmit" class="space-y-4">
        <div class="space-y-2">
          <label class="text-xs font-bold text-indigo-200 uppercase tracking-wider">Recipient</label>
          <div class="relative">
            <input 
              v-model="recipientSearch"
              @input="searchRecipients"
              type="text"
              placeholder="Search by name or email..."
              class="w-full bg-white/10 backdrop-blur-xl border border-white/20 rounded-xl px-4 py-3.5 text-sm font-semibold text-white focus:ring-2 focus:ring-white/50 focus:border-transparent outline-none transition-all hover:bg-white/15 placeholder-white/40"
            />
            
            <!-- Search Results Dropdown -->
            <div v-if="showResults" class="absolute top-full left-0 right-0 mt-2 bg-white rounded-xl shadow-lg z-10 max-h-48 overflow-y-auto">
              <div v-if="searchResults.length === 0" class="p-4 text-center text-slate-500 text-sm">
                No users found
              </div>
              <button
                v-for="user in searchResults"
                :key="user.id"
                type="button"
                @click="selectRecipient(user)"
                class="w-full text-left px-4 py-3 hover:bg-indigo-50 border-b border-slate-100 last:border-b-0 transition-all flex items-center justify-between"
              >
                <div>
                  <p class="font-semibold text-slate-900">{{ user.fullName }}</p>
                  <p class="text-xs text-slate-500">{{ user.email }}</p>
                </div>
              </button>
            </div>
          </div>
        </div>

        <div class="space-y-2">
          <div class="flex items-center justify-between">
            <label class="text-xs font-bold text-indigo-200 uppercase tracking-wider">Amount</label>
            <span v-if="props.balance !== null && props.balance !== undefined" class="text-xs text-indigo-200 font-semibold">Balance: ${{ props.balance.toFixed(2) }}</span>
          </div>
          <div class="relative">
            <span class="absolute left-4 top-1/2 -translate-y-1/2 font-bold text-white/60 text-lg">$</span>
            <input 
              v-model.number="amount"
              type="number" 
              step="0.01"
              min="0.01"
              class="w-full bg-white/10 backdrop-blur-xl border border-white/20 rounded-xl pl-10 pr-4 py-3.5 text-sm font-semibold text-white placeholder:text-white/40 focus:ring-2 focus:ring-white/50 focus:border-transparent outline-none transition-all"
              placeholder="0.00"
              required
            />
          </div>
          <p v-if="insufficientBalance" class="text-xs font-semibold text-rose-200">Insufficient balance for this transfer</p>
        </div>

        <button 
          type="submit"
          :disabled="loading || !recipientId || !isAmountValid()"
          class="w-full mt-6 bg-white text-indigo-600 hover:bg-indigo-50 active:scale-[0.98] disabled:bg-white/20 disabled:text-white/40 disabled:cursor-not-allowed rounded-xl py-4 font-black text-sm uppercase tracking-wider transition-all shadow-xl shadow-indigo-900/20"
        >
          <span v-if="loading">Processing...</span>
          <span v-else-if="insufficientBalance">Insufficient Balance</span>
          <span v-else>Send Money</span>
        </button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.bg-grid-white {
  background-image: linear-gradient(to right, white 1px, transparent 1px),
    linear-gradient(to bottom, white 1px, transparent 1px);
}

input::-webkit-outer-spin-button,
input::-webkit-inner-spin-button {
  -webkit-appearance: none;
  margin: 0;
}

input[type=number] {
  -moz-appearance: textfield;
}
</style>
