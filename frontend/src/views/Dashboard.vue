<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import axios from 'axios'
import { useRouter } from 'vue-router'
import StatCard from '../components/StatCard.vue'
import TransactionCard from '../components/TransactionCard.vue'
import TransferForm from '../components/TransferForm.vue'
import { formatCurrencyCompact, formatNumberCompact } from '../utils/formatNumber'

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

interface Contact {
  user: User
  transactionCount: number
  lastTransaction: string
}

const currentUser = ref<User | null>(null)
const currentUserId = ref<string>('')
const transactions = ref<Transaction[]>([])
const balance = ref<number | null>(null)
const loading = ref(false)
const transferLoading = ref(false)
const transferStatus = ref<{ type: 'success' | 'error' | '', message: string }>({ type: '', message: '' })
const allUsers = ref<User[]>([])
const showAllTransactions = ref(false)

// Statistics
const totalSent = computed(() => {
  return transactions.value
    .filter(tx => tx.senderId === currentUserId.value)
    .reduce((sum, tx) => sum + tx.amount, 0)
})

const totalReceived = computed(() => {
  return transactions.value
    .filter(tx => tx.receiverId === currentUserId.value)
    .reduce((sum, tx) => sum + tx.amount, 0)
})

const transactionCount = computed(() => transactions.value.length)

const recentTransactions = computed(() => transactions.value.slice(0, 5))

// Calculate month-over-month percentage changes
const balanceTrend = computed(() => {
  const now = new Date()
  const currentMonth = now.getMonth()
  const currentYear = now.getFullYear()
  
  // Get previous month
  const prevMonth = currentMonth === 0 ? 11 : currentMonth - 1
  const prevYear = currentMonth === 0 ? currentYear - 1 : currentYear
  
  // Calculate balance for current month (received - sent this month)
  const currentMonthTxs = transactions.value.filter(tx => {
    const txDate = new Date(tx.timestamp)
    return txDate.getMonth() === currentMonth && txDate.getFullYear() === currentYear
  })
  const currentMonthReceived = currentMonthTxs.filter(tx => tx.receiverId === currentUserId.value).reduce((sum, tx) => sum + tx.amount, 0)
  const currentMonthSent = currentMonthTxs.filter(tx => tx.senderId === currentUserId.value).reduce((sum, tx) => sum + tx.amount, 0)
  const currentMonthNet = currentMonthReceived - currentMonthSent
  
  // Calculate balance for previous month
  const prevMonthTxs = transactions.value.filter(tx => {
    const txDate = new Date(tx.timestamp)
    return txDate.getMonth() === prevMonth && txDate.getFullYear() === prevYear
  })
  const prevMonthReceived = prevMonthTxs.filter(tx => tx.receiverId === currentUserId.value).reduce((sum, tx) => sum + tx.amount, 0)
  const prevMonthSent = prevMonthTxs.filter(tx => tx.senderId === currentUserId.value).reduce((sum, tx) => sum + tx.amount, 0)
  const prevMonthNet = prevMonthReceived - prevMonthSent
  
  // If no previous month data, return null (won't show trend)
  if (prevMonthNet === 0 && prevMonthTxs.length === 0) return null
  
  const percentChange = ((currentMonthNet - prevMonthNet) / Math.abs(prevMonthNet)) * 100
  return {
    percent: Math.abs(percentChange).toFixed(1),
    isUp: percentChange >= 0
  }
})

const receivedTrend = computed(() => {
  const now = new Date()
  const currentMonth = now.getMonth()
  const currentYear = now.getFullYear()
  
  // Get previous month
  const prevMonth = currentMonth === 0 ? 11 : currentMonth - 1
  const prevYear = currentMonth === 0 ? currentYear - 1 : currentYear
  
  // Calculate received for current month
  const currentMonthReceived = transactions.value
    .filter(tx => {
      const txDate = new Date(tx.timestamp)
      return txDate.getMonth() === currentMonth && txDate.getFullYear() === currentYear && tx.receiverId === currentUserId.value
    })
    .reduce((sum, tx) => sum + tx.amount, 0)
  
  // Calculate received for previous month
  const prevMonthReceived = transactions.value
    .filter(tx => {
      const txDate = new Date(tx.timestamp)
      return txDate.getMonth() === prevMonth && txDate.getFullYear() === prevYear && tx.receiverId === currentUserId.value
    })
    .reduce((sum, tx) => sum + tx.amount, 0)
  
  // If no previous month data, return null (won't show trend)
  if (prevMonthReceived === 0) return null
  
  const percentChange = ((currentMonthReceived - prevMonthReceived) / prevMonthReceived) * 100
  return {
    percent: Math.abs(percentChange).toFixed(1),
    isUp: percentChange >= 0
  }
})

// Get frequent contacts from transaction history
const frequentContacts = computed(() => {
  const contactMap = new Map<string, Contact>()
  
  transactions.value.forEach(tx => {
    const otherId = tx.senderId === currentUserId.value ? tx.receiverId : tx.senderId
    const otherUser = allUsers.value.find(u => u.id === otherId)
    
    if (otherUser) {
      if (contactMap.has(otherId)) {
        const contact = contactMap.get(otherId)!
        contact.transactionCount++
        if (new Date(tx.timestamp) > new Date(contact.lastTransaction)) {
          contact.lastTransaction = tx.timestamp
        }
      } else {
        contactMap.set(otherId, {
          user: otherUser,
          transactionCount: 1,
          lastTransaction: tx.timestamp
        })
      }
    }
  })
  
  return Array.from(contactMap.values())
    .sort((a, b) => b.transactionCount - a.transactionCount)
    .slice(0, 4)
})

// Recent activity notifications
const recentActivity = computed(() => {
  return transactions.value.slice(0, 3).map(tx => {
    const isIncoming = tx.receiverId === currentUserId.value
    const otherUser = allUsers.value.find(u => u.id === (isIncoming ? tx.senderId : tx.receiverId))
    return {
      ...tx,
      isIncoming,
      otherUser
    }
  })
})

const showQuickTransferAmount = ref<string>('')
const selectedQuickTransferContact = ref<Contact | null>(null)
const selectedTransaction = ref<Transaction | null>(null)
const showTransactionModal = ref(false)
const copiedTransactionId = ref(false)
const router = useRouter()

async function loadCurrentUser() {
  const userId = sessionStorage.getItem('userId')
  const userStr = sessionStorage.getItem('currentUser')
  
  if (!userId || !userStr) {
    console.error('No user logged in')
    return
  }
  
  currentUserId.value = userId
  currentUser.value = JSON.parse(userStr)
  
  await loadBalance()
  await loadUsers()
}

async function loadUsers() {
  try {
    const res = await axios.get('/api/wallet/users')
    allUsers.value = res.data
  } catch (err) {
    console.error('Failed to load users', err)
  }
}

async function loadBalance() {
  if (!currentUserId.value) return
  loading.value = true
  try {
    const res = await axios.get(`/api/wallet/${currentUserId.value}/balance`)
    balance.value = res.data.balance
    await loadHistory()
  } catch (err) {
    console.error('Failed to load balance', err)
  } finally {
    loading.value = false
  }
}

async function loadHistory() {
  if (!currentUserId.value) return
  try {
    const res = await axios.get(`/api/transaction/history/${currentUserId.value}`)
    transactions.value = res.data.sort((a: Transaction, b: Transaction) => 
      new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
    )
  } catch (err) {
    console.error('Failed to load history', err)
  }
}

async function handleTransfer(payload: { receiverId: string; amount: number }) {
  transferLoading.value = true
  transferStatus.value = { type: '', message: '' }
  
  try {
    const res = await axios.post('/api/transaction/transfer', {
      senderId: currentUserId.value,
      receiverId: payload.receiverId,
      amount: payload.amount
    })
    
    if (res.data.status === 'COMPLETED') {
      transferStatus.value = { 
        type: 'success', 
        message: `✓ Transfer successful! ${formatCurrencyCompact(payload.amount)} sent.` 
      }
      await loadBalance()
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
    // Clear message after 5 seconds
    setTimeout(() => {
      transferStatus.value = { type: '', message: '' }
    }, 5000)
  }
}

async function quickTransferToContact(contact: Contact) {
  router.push({ name: 'contact-detail', params: { id: contact.user.id } })
}

function toggleAllTransactions() {
  showAllTransactions.value = !showAllTransactions.value
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

function openTransactionDetail(tx: Transaction) {
  selectedTransaction.value = tx
  showTransactionModal.value = true
}

function closeTransactionModal() {
  showTransactionModal.value = false
  selectedTransaction.value = null
}

function getBalanceAtTransaction(tx: Transaction) {
  // Sort transactions by timestamp to get chronological order
  const sortedTxs = [...transactions.value].sort((a, b) => 
    new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime()
  )
  
  // Find the index of the current transaction
  const txIndex = sortedTxs.findIndex(t => t.id === tx.id)
  
  // Calculate balance up to this transaction
  let balanceAtTime = 0
  for (let i = 0; i <= txIndex; i++) {
    const transaction = sortedTxs[i]
    if (transaction.receiverId === currentUserId.value) {
      balanceAtTime += transaction.amount
    } else if (transaction.senderId === currentUserId.value) {
      balanceAtTime -= transaction.amount
    }
  }
  
  return balanceAtTime
}

function getUserById(userId: string) {
  return allUsers.value.find(u => u.id === userId)
}

function copyTransactionId(id: string) {
  try {
    navigator.clipboard.writeText(id).then(() => {
      copiedTransactionId.value = true
      setTimeout(() => {
        copiedTransactionId.value = false
      }, 2000)
    }).catch(() => {
      // Fallback for browsers that don't support clipboard API
      const textarea = document.createElement('textarea')
      textarea.value = id
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
      copiedTransactionId.value = true
      setTimeout(() => {
        copiedTransactionId.value = false
      }, 2000)
    })
  } catch (err) {
    console.error('Failed to copy:', err)
  }
}

function formatCentralTime(timestamp: string): string {
  // Ensure timestamp is treated as UTC by adding Z if not present
  const utcTimestamp = timestamp.endsWith('Z') ? timestamp : timestamp + 'Z'
  const date = new Date(utcTimestamp)
  return date.toLocaleString('en-US', {
    timeZone: 'America/Chicago',
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

onMounted(loadCurrentUser)

</script>

<template>
  <div class="max-w-7xl mx-auto space-y-8">
    
    <!-- Header Section -->
    <div class="flex flex-col lg:flex-row items-start lg:items-center justify-between gap-6">
      <div>
        <h1 class="text-4xl font-black text-slate-900 dark:text-slate-50 tracking-tight mb-2">
          Welcome back, {{ currentUser?.fullName || 'User' }}
        </h1>
        <p class="text-slate-500 dark:text-slate-400 font-medium">
          Manage your finances and track transactions in real-time
        </p>
      </div>
      <button 
        @click="loadBalance"
        :disabled="loading"
        class="flex items-center gap-2 px-5 py-2.5 bg-white dark:bg-slate-700 border border-slate-200 dark:border-slate-600 rounded-xl text-sm font-bold text-slate-700 dark:text-slate-200 hover:bg-slate-50 dark:hover:bg-slate-600 hover:border-slate-300 dark:hover:border-slate-500 transition-all active:scale-95 shadow-sm disabled:opacity-50 disabled:cursor-not-allowed"
      >
        <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" :class="loading ? 'animate-spin' : ''">
          <path d="M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8"/>
          <path d="M21 3v5h-5"/>
        </svg>
        {{ loading ? 'Refreshing...' : 'Refresh Data' }}
      </button>
    </div>

    <!-- Status Message -->
    <div 
      v-if="transferStatus.message" 
      :class="[
        'rounded-xl p-4 flex items-center gap-3 font-semibold text-sm transition-all animate-slideIn',
        transferStatus.type === 'success' 
          ? 'bg-emerald-50 dark:bg-emerald-900/20 border border-emerald-200 dark:border-emerald-800 text-emerald-800 dark:text-emerald-200' 
          : 'bg-rose-50 dark:bg-rose-900/20 border border-rose-200 dark:border-rose-800 text-rose-800 dark:text-rose-200'
      ]"
    >
      <div :class="[
        'w-8 h-8 rounded-lg flex items-center justify-center',
        transferStatus.type === 'success' ? 'bg-emerald-500' : 'bg-rose-500'
      ]">
        <svg v-if="transferStatus.type === 'success'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="white" class="w-5 h-5">
          <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.857-9.809a.75.75 0 00-1.214-.882l-3.483 4.79-1.88-1.88a.75.75 0 10-1.06 1.061l2.5 2.5a.75.75 0 001.137-.089l4-5.5z" clip-rule="evenodd" />
        </svg>
        <svg v-else xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="white" class="w-5 h-5">
          <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.28 7.22a.75.75 0 00-1.06 1.06L8.94 10l-1.72 1.72a.75.75 0 101.06 1.06L10 11.06l1.72 1.72a.75.75 0 101.06-1.06L11.06 10l1.72-1.72a.75.75 0 00-1.06-1.06L10 8.94 8.28 7.22z" clip-rule="evenodd" />
        </svg>
      </div>
      {{ transferStatus.message }}
    </div>

    <!-- User Selector & Stats Grid -->
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <StatCard 
        title="Balance"
        :value="balance === null ? '---' : formatCurrencyCompact(balance)"
        :icon="`<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='currentColor' class='w-5 h-5'>
          <path d='M12 7.5a2.25 2.25 0 100 4.5 2.25 2.25 0 000-4.5z' />
          <path fill-rule='evenodd' d='M1.5 4.875C1.5 3.839 2.34 3 3.375 3h17.25c1.035 0 1.875.84 1.875 1.875v9.75c0 1.036-.84 1.875-1.875 1.875H3.375A1.875 1.875 0 011.5 14.625v-9.75zM8.25 9.75a3.75 3.75 0 117.5 0 3.75 3.75 0 01-7.5 0zM18.75 9a.75.75 0 00-.75.75v.008c0 .414.336.75.75.75h.008a.75.75 0 00.75-.75V9.75a.75.75 0 00-.75-.75h-.008zM4.5 9.75A.75.75 0 015.25 9h.008a.75.75 0 01.75.75v.008a.75.75 0 01-.75.75H5.25a.75.75 0 01-.75-.75V9.75z' clip-rule='evenodd' />
          <path d='M2.25 18a.75.75 0 000 1.5c5.4 0 10.63.722 15.6 2.075 1.19.324 2.4-.558 2.4-1.82V18.75a.75.75 0 00-.75-.75H2.25z' />
        </svg>`"
        :trend="balanceTrend ? `${balanceTrend.isUp ? '+' : '-'}${balanceTrend.percent}%` : undefined"
        :trendUp="balanceTrend?.isUp"
        subtitle="Available funds"
      />
      
      <StatCard 
        title="Sent"
        :value="formatCurrencyCompact(totalSent)"
        :icon="`<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='currentColor' class='w-5 h-5'>
          <path d='M3.478 2.405a.75.75 0 00-.926.94l2.432 7.905H13.5a.75.75 0 010 1.5H4.984l-2.432 7.905a.75.75 0 00.926.94 60.519 60.519 0 0018.445-8.986.75.75 0 000-1.218A60.517 60.517 0 003.478 2.405z' />
        </svg>`"
        subtitle="Total outgoing"
      />
      
      <StatCard 
        title="Received"
        :value="formatCurrencyCompact(totalReceived)"
        :icon="`<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='currentColor' class='w-5 h-5'>
          <path d='M9.25 13.25a.75.75 0 001.5 0V4.636l2.955 3.129a.75.75 0 001.09-1.03l-4.25-4.5a.75.75 0 00-1.09 0l-4.25 4.5a.75.75 0 101.09 1.03l2.955-3.129v8.614z' />
          <path d='M3.5 12.75a.75.75 0 00-1.5 0v2.5A2.75 2.75 0 004.75 18h10.5A2.75 2.75 0 0018 15.25v-2.5a.75.75 0 00-1.5 0v2.5c0 .69-.56 1.25-1.25 1.25H4.75c-.69 0-1.25-.56-1.25-1.25v-2.5z' />
        </svg>`"
        subtitle="Total incoming"
        :trend="receivedTrend ? `${receivedTrend.isUp ? '+' : '-'}${receivedTrend.percent}%` : undefined"
        :trendUp="receivedTrend?.isUp"
      />
    </div>

    <!-- Main Content Grid -->
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
      
      <!-- Left Column: Transfer Form + Frequent Contacts -->
      <div class="lg:col-span-1 space-y-6">
        <!-- Transfer Form -->
        <TransferForm 
          data-transfer-form
          :loading="transferLoading"
          :balance="balance"
          :preselectedContact="selectedQuickTransferContact || undefined"
          @transfer="handleTransfer"
        />

        <!-- Frequent Contacts -->
        <div v-if="frequentContacts.length > 0" class="bg-white dark:bg-slate-800 rounded-2xl border border-slate-200/60 dark:border-slate-700/60 p-6 shadow-sm">
          <div class="flex items-center gap-3 mb-4">
            <div class="w-10 h-10 rounded-xl bg-gradient-to-br from-indigo-500 to-indigo-600 flex items-center justify-center text-white shadow-lg shadow-indigo-500/30">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" class="w-5 h-5">
                <path d="M4.5 6.375a4.125 4.125 0 118.25 0 4.125 4.125 0 01-8.25 0zM14.25 8.625a3.375 3.375 0 116.75 0 3.375 3.375 0 01-6.75 0zM1.5 19.125a7.125 7.125 0 0114.25 0v.003l-.001.119a.75.75 0 01-.363.63 13.067 13.067 0 01-6.761 1.873c-2.472 0-4.786-.684-6.76-1.873a.75.75 0 01-.364-.63l-.001-.122zM17.25 19.128l-.001.144a2.25 2.25 0 01-.233.96 10.088 10.088 0 005.06-1.01.75.75 0 00.42-.643 4.875 4.875 0 00-6.957-4.611 8.586 8.586 0 011.71 5.157v.003z" />
              </svg>
            </div>
            <div>
              <h3 class="text-sm font-bold text-slate-900 dark:text-slate-50 uppercase tracking-wider">Frequent Contacts</h3>
              <p class="text-xs text-slate-500 dark:text-slate-400 font-medium mt-0.5">Quick access to regular recipients</p>
            </div>
          </div>
          <div class="space-y-2">
            <button 
              v-for="contact in frequentContacts" 
              :key="contact.user.id"
              @click="quickTransferToContact(contact)"
              class="w-full flex items-center gap-3 p-3 rounded-xl hover:bg-slate-50 dark:hover:bg-slate-700/50 transition-all group border border-transparent hover:border-slate-200 dark:hover:border-slate-600"
            >
              <div class="w-10 h-10 rounded-full bg-gradient-to-br from-violet-500 to-purple-600 flex items-center justify-center text-white font-bold text-sm shadow-md">
                {{ contact.user.fullName.charAt(0) }}
              </div>
              <div class="flex-1 text-left">
                <p class="text-sm font-bold text-slate-900 dark:text-white group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors">{{ contact.user.fullName }}</p>
                <p class="text-xs text-slate-500 dark:text-slate-400">{{ contact.transactionCount }} transaction{{ contact.transactionCount > 1 ? 's' : '' }}</p>
              </div>
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-5 h-5 text-slate-300 group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors">
                <path fill-rule="evenodd" d="M3 10a.75.75 0 01.75-.75h10.638L10.23 5.29a.75.75 0 111.04-1.08l5.5 5.5a.75.75 0 010 1.06l-5.5 5.5a.75.75 0 11-1.04-1.08l4.158-4.158H3.75A.75.75 0 013 10z" clip-rule="evenodd" />
              </svg>
            </button>
          </div>
        </div>

        <!-- Recent Activity Notifications -->
        <div v-if="recentActivity.length > 0" class="bg-white dark:bg-slate-800 rounded-2xl border border-slate-200/60 dark:border-slate-700/60 p-6 shadow-sm">
          <div class="flex items-center gap-3 mb-4">
            <div class="w-10 h-10 rounded-xl bg-gradient-to-br from-emerald-500 to-teal-600 flex items-center justify-center text-white shadow-lg shadow-emerald-500/30">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" class="w-5 h-5">
                <path fill-rule="evenodd" d="M5.25 9a6.75 6.75 0 0113.5 0v.75c0 2.123.8 4.057 2.118 5.52a.75.75 0 01-.297 1.206c-1.544.57-3.16.99-4.831 1.243a3.75 3.75 0 11-7.48 0 24.585 24.585 0 01-4.831-1.244.75.75 0 01-.298-1.205A8.217 8.217 0 005.25 9.75V9zm4.502 8.9a2.25 2.25 0 104.496 0 25.057 25.057 0 01-4.496 0z" clip-rule="evenodd" />
              </svg>
            </div>
            <div>
              <h3 class="text-sm font-bold text-slate-900 dark:text-slate-50 uppercase tracking-wider">Activity Feed</h3>
              <p class="text-xs text-slate-500 dark:text-slate-400 font-medium mt-0.5">Latest updates</p>
            </div>
          </div>
          <div class="space-y-3">
            <div 
              v-for="activity in recentActivity" 
              :key="activity.id"
              class="flex items-start gap-3 p-3 rounded-xl bg-slate-50 dark:bg-slate-700/30 border border-slate-100 dark:border-slate-700"
            >
              <div :class="[
                'w-8 h-8 rounded-lg flex items-center justify-center text-white shadow-md',
                activity.isIncoming ? 'bg-emerald-500' : 'bg-indigo-500'
              ]">
                <svg v-if="activity.isIncoming" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-4 h-4">
                  <path fill-rule="evenodd" d="M10 3a.75.75 0 01.75.75v10.638l3.96-4.158a.75.75 0 111.08 1.04l-5.25 5.5a.75.75 0 01-1.08 0l-5.25-5.5a.75.75 0 111.08-1.04l3.96 4.158V3.75A.75.75 0 0110 3z" clip-rule="evenodd" />
                </svg>
                <svg v-else xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-4 h-4">
                  <path fill-rule="evenodd" d="M10 17a.75.75 0 01-.75-.75V5.612L5.29 9.77a.75.75 0 01-1.08-1.04l5.25-5.5a.75.75 0 011.08 0l5.25 5.5a.75.75 0 11-1.08 1.04l-3.96-4.158V16.25A.75.75 0 0110 17z" clip-rule="evenodd" />
                </svg>
              </div>
              <div class="flex-1 min-w-0">
                <p class="text-xs font-bold text-slate-900 dark:text-slate-50">
                  {{ activity.isIncoming ? 'Received' : 'Sent' }} {{ formatCurrencyCompact(activity.amount) }}
                </p>
                <p class="text-xs text-slate-500 dark:text-slate-400 truncate">
                  {{ activity.isIncoming ? 'from' : 'to' }} {{ activity.otherUser?.fullName || 'Unknown' }}
                </p>
                <p class="text-[11px] text-slate-400 dark:text-slate-500 mt-1">{{ formatRelativeTime(activity.timestamp) }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Right Column: Recent Transactions -->
      <div class="lg:col-span-2 bg-white dark:bg-slate-800 rounded-2xl border border-slate-200/60 dark:border-slate-700/60 shadow-sm overflow-hidden">
        <div class="px-6 py-5 border-b border-slate-100 dark:border-slate-700 bg-gradient-to-r from-slate-50 dark:from-slate-700/30 to-white dark:to-slate-800 flex items-center justify-between">
          <div>
            <h3 class="text-lg font-black text-slate-900 dark:text-slate-50 tracking-tight">Recent Activity</h3>
            <p class="text-xs text-slate-500 dark:text-slate-400 font-medium mt-1">Latest {{ recentTransactions.length }} transactions</p>
          </div>
          <div class="px-3 py-1.5 bg-slate-900 dark:bg-slate-700 text-white dark:text-slate-200 rounded-lg text-xs font-bold">
            {{ transactionCount }} Total
          </div>
        </div>

        <div class="divide-y divide-slate-100 dark:divide-slate-700">
          <TransactionCard 
            v-for="tx in recentTransactions" 
            :key="tx.id"
            :id="tx.id"
            :type="tx.senderId === currentUserId ? 'outgoing' : 'incoming'"
            :amount="tx.amount"
            :timestamp="tx.timestamp"
            :status="tx.status"
            @click="openTransactionDetail(tx)"
          />

          <div v-if="transactions.length === 0" class="py-16 flex flex-col items-center justify-center">
            <div class="w-16 h-16 bg-slate-100 dark:bg-slate-700 rounded-2xl flex items-center justify-center mb-4">
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2" class="w-8 h-8 text-slate-400 dark:text-slate-500">
                <path stroke-linecap="round" stroke-linejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </div>
            <h4 class="text-sm font-bold text-slate-900 dark:text-slate-50 mb-1">No transactions yet</h4>
            <p class="text-xs text-slate-500 dark:text-slate-400">Your transaction history will appear here</p>
          </div>
        </div>

        <!-- Expanded view of all transactions (appears when showAllTransactions is true) -->
        <div v-if="showAllTransactions && transactions.length > 5" class="border-t border-slate-100 dark:border-slate-700">
          <div class="px-6 py-4 bg-slate-50 dark:bg-slate-700/30 border-b border-slate-100 dark:border-slate-700 flex items-center justify-between">
            <div>
              <h4 class="text-sm font-bold text-slate-900 dark:text-slate-50">All Transactions</h4>
              <p class="text-xs text-slate-500 dark:text-slate-400 font-medium mt-0.5">{{ transactions.length }} total</p>
            </div>
            <button 
              @click="toggleAllTransactions"
              class="p-1.5 hover:bg-slate-200 dark:hover:bg-slate-600 rounded-lg transition-all"
              title="Close"
            >
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-4 h-4 text-slate-500 dark:text-slate-400">
                <path d="M6.28 5.22a.75.75 0 00-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 101.06 1.06L10 11.06l3.72 3.72a.75.75 0 101.06-1.06L11.06 10l3.72-3.72a.75.75 0 00-1.06-1.06L10 8.94 6.28 5.22z" />
              </svg>
            </button>
          </div>
          <div class="divide-y divide-slate-100 dark:divide-slate-700 max-h-[500px] overflow-y-auto">
            <TransactionCard 
              v-for="tx in transactions" 
              :key="'all-' + tx.id"
              :id="tx.id"
              :type="tx.senderId === currentUserId ? 'outgoing' : 'incoming'"
              :amount="tx.amount"
              :timestamp="tx.timestamp"
              :status="tx.status"
              @click="openTransactionDetail(tx)"
            />
          </div>
        </div>

        <div v-if="transactions.length > 5" class="px-6 py-4 bg-slate-50 dark:bg-slate-700/30 border-t border-slate-100 dark:border-slate-700">
          <button 
            @click="toggleAllTransactions"
            class="w-full py-2.5 text-sm font-bold text-indigo-600 dark:text-indigo-400 hover:text-indigo-700 dark:hover:text-indigo-300 transition-colors"
          >
            {{ showAllTransactions ? '← Show Less' : 'View All Transactions →' }}
          </button>
        </div>
      </div>
    </div>

  </div>

  <!-- Transaction Detail Modal -->
  <Teleport to="body">
    <Transition
      enter-active-class="transition-opacity duration-200"
      leave-active-class="transition-opacity duration-200"
      enter-from-class="opacity-0"
      leave-to-class="opacity-0"
    >
      <div 
        v-if="showTransactionModal && selectedTransaction"
        class="fixed inset-0 bg-black/60 backdrop-blur-sm z-50 flex items-center justify-center p-4"
        @click.self="closeTransactionModal"
      >
        <Transition
          enter-active-class="transition-all duration-200"
          leave-active-class="transition-all duration-200"
          enter-from-class="opacity-0 scale-95"
          leave-to-class="opacity-0 scale-95"
        >
          <div 
            v-if="showTransactionModal"
            class="bg-white dark:bg-slate-800 rounded-3xl shadow-2xl max-w-lg w-full overflow-hidden"
          >
            <!-- Header -->
            <div class="relative px-6 py-8 bg-gradient-to-br from-slate-50 dark:from-slate-700/30 to-white dark:to-slate-800 border-b border-slate-200 dark:border-slate-700">
              <button 
                @click="closeTransactionModal"
                class="absolute top-4 right-4 p-2 hover:bg-slate-200 dark:hover:bg-slate-600 rounded-lg transition-all"
              >
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-5 h-5 text-slate-500 dark:text-slate-400">
                  <path d="M6.28 5.22a.75.75 0 00-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 101.06 1.06L10 11.06l3.72 3.72a.75.75 0 101.06-1.06L11.06 10l3.72-3.72a.75.75 0 00-1.06-1.06L10 8.94 6.28 5.22z" />
                </svg>
              </button>
              
              <div class="flex items-center gap-4">
                <div :class="[
                  'w-16 h-16 rounded-2xl flex items-center justify-center shadow-lg',
                  selectedTransaction.receiverId === currentUserId 
                    ? 'bg-gradient-to-br from-emerald-400 to-emerald-500 text-white' 
                    : 'bg-gradient-to-br from-slate-200 dark:from-slate-700 to-slate-300 dark:to-slate-600 text-slate-700 dark:text-slate-300'
                ]">
                  <svg v-if="selectedTransaction.senderId === currentUserId" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-7 h-7">
                    <path d="M10.75 2.75a.75.75 0 00-1.5 0v8.614L6.295 8.235a.75.75 0 10-1.09 1.03l4.25 4.5a.75.75 0 001.09 0l4.25-4.5a.75.75 0 00-1.09-1.03l-2.955 3.129V2.75z" />
                    <path d="M3.5 12.75a.75.75 0 00-1.5 0v2.5A2.75 2.75 0 004.75 18h10.5A2.75 2.75 0 0018 15.25v-2.5a.75.75 0 00-1.5 0v2.5c0 .69-.56 1.25-1.25 1.25H4.75c-.69 0-1.25-.56-1.25-1.25v-2.5z" />
                  </svg>
                  <svg v-else xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-7 h-7">
                    <path d="M9.25 13.25a.75.75 0 001.5 0V4.636l2.955 3.129a.75.75 0 001.09-1.03l-4.25-4.5a.75.75 0 00-1.09 0l-4.25 4.5a.75.75 0 101.09 1.03l2.955-3.129v8.614z" />
                    <path d="M3.5 12.75a.75.75 0 00-1.5 0v2.5A2.75 2.75 0 004.75 18h10.5A2.75 2.75 0 0018 15.25v-2.5a.75.75 0 00-1.5 0v2.5c0 .69-.56 1.25-1.25 1.25H4.75c-.69 0-1.25-.56-1.25-1.25v-2.5z" />
                  </svg>
                </div>
                <div>
                  <h3 class="text-2xl font-black text-slate-900 dark:text-slate-50">
                    {{ selectedTransaction.senderId === currentUserId ? 'Payment Sent' : 'Payment Received' }}
                  </h3>
                  <p class="text-sm text-slate-500 dark:text-slate-400 font-medium mt-1">
                    {{ new Date(selectedTransaction.timestamp).toUTCString().split(' ').slice(0, 4).join(' ') }}
                  </p>
                </div>
              </div>
            </div>

            <!-- Amount -->
            <div class="px-6 py-6 border-b border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-700/20">
              <p class="text-xs uppercase tracking-wider font-bold text-slate-500 dark:text-slate-400 mb-2">Amount</p>
              <p :class="[
                'text-4xl font-black tabular-nums',
                selectedTransaction.receiverId === currentUserId 
                  ? 'text-emerald-600 dark:text-emerald-400' 
                  : 'text-slate-900 dark:text-slate-50'
              ]">
                {{ selectedTransaction.receiverId === currentUserId ? '+' : '−' }}{{ formatCurrencyCompact(selectedTransaction.amount) }}
              </p>
            </div>

            <!-- Details -->
            <div class="px-6 py-6 space-y-4">
              <div>
                <p class="text-xs uppercase tracking-wider font-bold text-slate-500 dark:text-slate-400 mb-2">Transaction ID</p>
                <div class="flex items-center gap-2">
                  <p class="text-sm font-mono text-slate-900 dark:text-slate-50 bg-slate-100 dark:bg-slate-700 px-3 py-2 rounded-lg break-all flex-1">
                    {{ selectedTransaction.id }}
                  </p>
                  <button 
                    @click="copyTransactionId(selectedTransaction.id)"
                    :title="copiedTransactionId ? 'Copied!' : 'Copy ID'"
                    class="p-2 rounded-lg hover:bg-slate-200 dark:hover:bg-slate-600 text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-100 transition-colors flex-shrink-0"
                  >
                    <svg v-if="!copiedTransactionId" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-5 h-5">
                      <path d="M7 3.5A2.5 2.5 0 0 1 9.5 1h8A2.5 2.5 0 0 1 20 3.5v8A2.5 2.5 0 0 1 17.5 14h-8A2.5 2.5 0 0 1 7 11.5v-8z"/>
                      <path d="M5.5 9A2.5 2.5 0 0 0 3 11.5v5A2.5 2.5 0 0 0 5.5 19h5A2.5 2.5 0 0 0 13 16.5"/>
                    </svg>
                    <svg v-else xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-5 h-5 text-emerald-500">
                      <path fill-rule="evenodd" d="M16.704 4.153a.75.75 0 0 1 .044 1.06l-8.25 9.5a.75.75 0 0 1-1.072.082l-3.5-3.5a.75.75 0 0 1 1.061-1.06l2.895 2.893 7.48-8.598a.75.75 0 0 1 1.06-.044Z" clip-rule="evenodd"/>
                    </svg>
                  </button>
                </div>
              </div>

              <div class="grid grid-cols-2 gap-4">
                <div>
                  <p class="text-xs uppercase tracking-wider font-bold text-slate-500 dark:text-slate-400 mb-2">From</p>
                  <p class="text-sm font-bold text-slate-900 dark:text-slate-50">
                    {{ getUserById(selectedTransaction.senderId)?.fullName || 'Unknown' }}
                  </p>
                  <p class="text-xs text-slate-500 dark:text-slate-400 mt-1">
                    {{ getUserById(selectedTransaction.senderId)?.email || '' }}
                  </p>
                </div>
                <div>
                  <p class="text-xs uppercase tracking-wider font-bold text-slate-500 dark:text-slate-400 mb-2">To</p>
                  <p class="text-sm font-bold text-slate-900 dark:text-slate-50">
                    {{ getUserById(selectedTransaction.receiverId)?.fullName || 'Unknown' }}
                  </p>
                  <p class="text-xs text-slate-500 dark:text-slate-400 mt-1">
                    {{ getUserById(selectedTransaction.receiverId)?.email || '' }}
                  </p>
                </div>
              </div>

              <div>
                <p class="text-xs uppercase tracking-wider font-bold text-slate-500 dark:text-slate-400 mb-2">Time</p>
                <p class="text-sm font-bold text-slate-900 dark:text-slate-50">
                  {{ formatCentralTime(selectedTransaction.timestamp) }} CST
                </p>
                <p class="text-xs text-slate-500 dark:text-slate-400 mt-1">
                  {{ formatRelativeTime(selectedTransaction.timestamp) }}
                </p>
              </div>

              <div>
                <p class="text-xs uppercase tracking-wider font-bold text-slate-500 dark:text-slate-400 mb-2">Status</p>
                <span :class="[
                  'inline-block px-3 py-1.5 rounded-lg text-xs font-bold uppercase',
                  selectedTransaction.status === 'COMPLETED' 
                    ? 'bg-emerald-50 dark:bg-emerald-900/30 text-emerald-600 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800'
                    : 'bg-amber-50 dark:bg-amber-900/30 text-amber-600 dark:text-amber-400 border border-amber-200 dark:border-amber-800'
                ]">
                  {{ selectedTransaction.status }}
                </span>
              </div>

              <div class="pt-4 border-t border-slate-200 dark:border-slate-700">
                <p class="text-xs uppercase tracking-wider font-bold text-slate-500 dark:text-slate-400 mb-2">Cumulative Balance</p>
                <p class="text-2xl font-black text-slate-900 dark:text-slate-50 tabular-nums">
                  {{ formatCurrencyCompact(getBalanceAtTransaction(selectedTransaction)) }}
                </p>
                <p class="text-xs text-slate-500 dark:text-slate-400 mt-1">
                  Your total balance calculated from all transactions up to this point
                </p>
              </div>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.animate-slideIn {
  animation: slideIn 0.3s ease-out;
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
