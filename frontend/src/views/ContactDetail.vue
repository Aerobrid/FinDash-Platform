<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import axios from 'axios'
import { formatCurrencyCompact } from '../utils/formatNumber'

const router = useRouter()
const route = useRoute()

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

const contact = ref<User | null>(null)
const transactions = ref<Transaction[]>([])
const currentUserId = ref('')
const loading = ref(true)
const transferAmount = ref('')
const transferLoading = ref(false)
const transferStatus = ref<{ type: 'success' | 'error' | '', message: string }>({ type: '', message: '' })
const balance = ref<number | null>(null)
const insufficientBalance = ref(false)

const isAmountValid = () => {
  if (!transferAmount.value) return false
  const amount = parseFloat(transferAmount.value)
  if (isNaN(amount) || amount <= 0) return false
  if (balance.value === null || balance.value === undefined) return true
  if (amount > balance.value) {
    insufficientBalance.value = true
    return false
  }
  insufficientBalance.value = false
  return true
}

onMounted(loadContactDetails)

async function loadContactDetails() {
  const contactId = route.params.id as string
  const userId = sessionStorage.getItem('userId')
  
  if (!userId) {
    router.push({ name: 'login' })
    return
  }
  
  currentUserId.value = userId
  loading.value = true
  
  try {
    // Load contact info
    const usersRes = await axios.get('/api/wallet/users')
    contact.value = usersRes.data.find((u: User) => u.id === contactId) || null
    
    if (!contact.value) {
      router.push({ name: 'contacts' })
      return
    }

    // Load balance
    const balanceRes = await axios.get(`/api/wallet/${userId}/balance`)
    balance.value = balanceRes.data.balance
    
    // Load transaction history with this contact
    const historyRes = await axios.get(`/api/transaction/history/${userId}`)
    const allTransactions = historyRes.data
    
    transactions.value = allTransactions
      .filter((tx: Transaction) => 
        (tx.senderId === contactId && tx.receiverId === userId) ||
        (tx.senderId === userId && tx.receiverId === contactId)
      )
      .sort((a: Transaction, b: Transaction) => 
        new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
      )
  } catch (err) {
    console.error('Failed to load contact details', err)
    router.push({ name: 'contacts' })
  } finally {
    loading.value = false
  }
}

async function handleTransfer() {
  if (!contact.value || !isAmountValid()) return
  
  transferLoading.value = true
  transferStatus.value = { type: '', message: '' }
  
  try {
    const amount = parseFloat(transferAmount.value)
    const res = await axios.post('/api/transaction/transfer', {
      senderId: currentUserId.value,
      receiverId: contact.value.id,
      amount: amount
    })
    
    if (res.data.status === 'COMPLETED') {
      transferStatus.value = {
        type: 'success',
        message: `✓ Transfer successful! ${formatCurrencyCompact(amount)} sent to ${contact.value.fullName}.`
      }
      transferAmount.value = ''
      insufficientBalance.value = false
      await loadContactDetails()
    } else {
      transferStatus.value = {
        type: 'error',
        message: 'Transaction could not be completed.'
      }
    }
  } catch (err) {
    transferStatus.value = {
      type: 'error',
      message: '⚠ Error: Insufficient funds or network timeout.'
    }
  } finally {
    transferLoading.value = false
    setTimeout(() => {
      transferStatus.value = { type: '', message: '' }
    }, 5000)
  }
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

const totalSentToContact = computed(() => {
  return transactions.value
    .filter(tx => tx.senderId === currentUserId.value)
    .reduce((sum, tx) => sum + tx.amount, 0)
})

const totalReceivedFromContact = computed(() => {
  return transactions.value
    .filter(tx => tx.receiverId === currentUserId.value)
    .reduce((sum, tx) => sum + tx.amount, 0)
})

function getTransactionType(tx: Transaction) {
  return tx.senderId === currentUserId.value ? 'sent' : 'received'
}
</script>

<template>
  <div v-if="loading" class="flex items-center justify-center min-h-[400px]">
    <div class="animate-spin">
      <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" class="w-12 h-12 text-indigo-600 dark:text-indigo-400">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
      </svg>
    </div>
  </div>

  <div v-else-if="contact" class="max-w-4xl mx-auto space-y-8">
    <!-- Header with back button -->
    <div class="flex items-center gap-4">
      <button
        @click="router.back()"
        class="p-2 hover:bg-slate-100 dark:hover:bg-slate-700 rounded-lg transition-all"
        title="Go back"
      >
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" class="w-6 h-6 text-slate-600 dark:text-slate-300">
          <path fill-rule="evenodd" d="M7.72 12.53a.75.75 0 010-1.06l7.20-7.20a.75.75 0 111.06 1.06L9.31 12l6.97 6.97a.75.75 0 11-1.06 1.06l-7.20-7.20z" clip-rule="evenodd" />
        </svg>
      </button>
      <h1 class="text-3xl font-black text-slate-900 dark:text-slate-50">Contact Details</h1>
    </div>

    <!-- Status Message -->
    <div 
      v-if="transferStatus.message" 
      :class="[
        'rounded-xl p-4 flex items-center gap-3 font-semibold text-sm transition-all',
        transferStatus.type === 'success' 
          ? 'bg-emerald-50 dark:bg-emerald-900/20 border border-emerald-200 dark:border-emerald-800 text-emerald-800 dark:text-emerald-200' 
          : 'bg-rose-50 dark:bg-rose-900/20 border border-rose-200 dark:border-rose-800 text-rose-800 dark:text-rose-200'
      ]"
    >
      {{ transferStatus.message }}
    </div>

    <!-- Contact Card -->
    <div class="bg-white dark:bg-slate-800 rounded-2xl border border-slate-200 dark:border-slate-700 p-8 shadow-sm">
      <div class="flex items-start gap-6">
        <div class="w-24 h-24 rounded-2xl bg-gradient-to-br from-indigo-500 to-purple-600 flex items-center justify-center text-white text-4xl font-bold shadow-lg">
          {{ contact.fullName.charAt(0) }}
        </div>
        <div class="flex-1">
          <h2 class="text-3xl font-black text-slate-900 dark:text-slate-50 mb-2">{{ contact.fullName }}</h2>
          <p class="text-slate-500 dark:text-slate-400 mb-4">{{ contact.email }}</p>
          <div class="flex items-center gap-4">
            <div class="px-4 py-2 bg-slate-100 dark:bg-slate-700 rounded-lg">
              <p class="text-xs text-slate-500 dark:text-slate-400">Transactions</p>
              <p class="text-sm font-bold text-slate-900 dark:text-slate-50">{{ transactions.length }}</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Stats Grid -->
    <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
      <div class="bg-white dark:bg-slate-800 rounded-2xl border border-slate-200 dark:border-slate-700 p-6 shadow-sm">
        <p class="text-sm text-slate-500 dark:text-slate-400 font-medium mb-2">Total Sent</p>
        <p class="text-3xl font-black text-slate-900 dark:text-slate-50">{{ formatCurrencyCompact(totalSentToContact) }}</p>
      </div>
      <div class="bg-white dark:bg-slate-800 rounded-2xl border border-slate-200 dark:border-slate-700 p-6 shadow-sm">
        <p class="text-sm text-slate-500 dark:text-slate-400 font-medium mb-2">Total Received</p>
        <p class="text-3xl font-black text-slate-900 dark:text-slate-50">{{ formatCurrencyCompact(totalReceivedFromContact) }}</p>
      </div>
      <div class="bg-white dark:bg-slate-800 rounded-2xl border border-slate-200 dark:border-slate-700 p-6 shadow-sm">
        <p class="text-sm text-slate-500 dark:text-slate-400 font-medium mb-2">Net Balance</p>
        <p :class="[
          'text-3xl font-black',
          (totalSentToContact - totalReceivedFromContact) >= 0 
            ? 'text-rose-600 dark:text-rose-400'
            : 'text-emerald-600 dark:text-emerald-400'
        ]">
          {{ formatCurrencyCompact(Math.abs(totalSentToContact - totalReceivedFromContact)) }}
        </p>
      </div>
    </div>

    <!-- Quick Transfer Section -->
    <div class="bg-white dark:bg-slate-800 rounded-2xl border border-slate-200 dark:border-slate-700 p-6 shadow-sm">
      <h3 class="text-lg font-black text-slate-900 dark:text-slate-50 mb-4">Send Money</h3>
      <form @submit.prevent="handleTransfer" class="space-y-4">
        <div>
          <div class="flex items-center justify-between mb-2">
            <label class="block text-sm font-bold text-slate-900 dark:text-slate-50">Amount</label>
            <span v-if="balance !== null && balance !== undefined" class="text-xs text-slate-500 dark:text-slate-400 font-semibold">Balance: ${{ balance.toFixed(2) }}</span>
          </div>
          <div class="relative">
            <span class="absolute left-4 top-3 text-slate-500 dark:text-slate-400 font-bold">$</span>
            <input
              v-model="transferAmount"
              type="number"
              step="0.01"
              min="0"
              placeholder="0.00"
              class="w-full pl-8 pr-4 py-2.5 border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-700 rounded-xl text-slate-900 dark:text-slate-50 placeholder-slate-400 dark:placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              :disabled="transferLoading"
            />
          </div>
          <p v-if="insufficientBalance" class="text-xs font-semibold text-rose-600 dark:text-rose-400 mt-2">Insufficient balance for this transfer</p>
        </div>
        <button
          type="submit"
          :disabled="transferLoading || !isAmountValid()"
          class="w-full py-3 bg-indigo-600 dark:bg-indigo-500 hover:bg-indigo-700 dark:hover:bg-indigo-600 disabled:opacity-50 disabled:cursor-not-allowed text-white font-bold rounded-xl transition-all active:scale-95"
        >
          <span v-if="!transferLoading && !insufficientBalance">Send ${{ transferAmount || '0.00' }} to {{ contact?.fullName }}</span>
          <span v-else-if="!transferLoading && insufficientBalance">Insufficient Balance</span>
          <span v-else class="flex items-center justify-center gap-2">
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" class="w-5 h-5 animate-spin">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            Processing...
          </span>
        </button>
      </form>
    </div>

    <!-- Transaction History -->
    <div v-if="transactions.length > 0" class="bg-white dark:bg-slate-800 rounded-2xl border border-slate-200 dark:border-slate-700 shadow-sm overflow-hidden">
      <div class="px-6 py-5 border-b border-slate-100 dark:border-slate-700 bg-gradient-to-r from-slate-50 dark:from-slate-700/30 to-white dark:to-slate-800">
        <h3 class="text-lg font-black text-slate-900 dark:text-slate-50">Transaction History</h3>
        <p class="text-xs text-slate-500 dark:text-slate-400 font-medium mt-1">{{ transactions.length }} transaction{{ transactions.length > 1 ? 's' : '' }}</p>
      </div>

      <div class="divide-y divide-slate-100 dark:divide-slate-700">
        <div 
          v-for="tx in transactions"
          :key="tx.id"
          class="px-6 py-4 flex items-center justify-between hover:bg-slate-50 dark:hover:bg-slate-700/30 transition-all"
        >
          <div class="flex items-center gap-4 flex-1">
            <div :class="[
              'w-10 h-10 rounded-lg flex items-center justify-center text-white',
              getTransactionType(tx) === 'sent' ? 'bg-indigo-500' : 'bg-emerald-500'
            ]">
              <svg v-if="getTransactionType(tx) === 'sent'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-5 h-5">
                <path fill-rule="evenodd" d="M10 17a.75.75 0 01-.75-.75V5.612L5.29 9.77a.75.75 0 01-1.08-1.04l5.25-5.5a.75.75 0 011.08 0l5.25 5.5a.75.75 0 11-1.08 1.04l-3.96-4.158V16.25A.75.75 0 0110 17z" clip-rule="evenodd" />
              </svg>
              <svg v-else xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-5 h-5">
                <path fill-rule="evenodd" d="M10 3a.75.75 0 01.75.75v10.638l3.96-4.158a.75.75 0 111.08 1.04l-5.25 5.5a.75.75 0 01-1.08 0l-5.25-5.5a.75.75 0 111.08-1.04l3.96 4.158V3.75A.75.75 0 0110 3z" clip-rule="evenodd" />
              </svg>
            </div>
            <div>
              <p class="font-bold text-slate-900 dark:text-slate-50">{{ getTransactionType(tx) === 'sent' ? 'Sent' : 'Received' }}</p>
              <p class="text-xs text-slate-500 dark:text-slate-400">{{ formatRelativeTime(tx.timestamp) }}</p>
            </div>
          </div>

          <div class="text-right">
            <p :class="[
              'font-bold',
              getTransactionType(tx) === 'sent' 
                ? 'text-rose-600 dark:text-rose-400'
                : 'text-emerald-600 dark:text-emerald-400'
            ]">
              {{ getTransactionType(tx) === 'sent' ? '-' : '+' }}{{ formatCurrencyCompact(tx.amount) }}
            </p>
            <p :class="[
              'text-xs font-bold',
              tx.status === 'COMPLETED' ? 'text-emerald-600 dark:text-emerald-400' : 'text-amber-600 dark:text-amber-400'
            ]">
              {{ tx.status }}
            </p>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="bg-slate-50 dark:bg-slate-800 rounded-2xl border border-slate-200 dark:border-slate-700 p-12 text-center">
      <p class="text-slate-500 dark:text-slate-400">No transactions with {{ contact.fullName }} yet.</p>
    </div>
  </div>
</template>

