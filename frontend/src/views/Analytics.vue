<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import axios from 'axios'
import { formatCurrencyCompact } from '../utils/formatNumber'

interface Transaction {
  id: string
  senderId: string
  receiverId: string
  amount: number
  status: string
  timestamp: string
}

const transactions = ref<Transaction[]>([])
const selectedUserId = ref(sessionStorage.getItem('userId') || '')
const loading = ref(false)

const totalSpent = computed(() => {
  return transactions.value
    .filter(tx => tx.senderId === selectedUserId.value)
    .reduce((sum, tx) => sum + tx.amount, 0)
})

const totalReceived = computed(() => {
  return transactions.value
    .filter(tx => tx.receiverId === selectedUserId.value)
    .reduce((sum, tx) => sum + tx.amount, 0)
})

// Transaction detail modal
const selectedTransaction = ref<Transaction | null>(null)
const showTransactionModal = ref(false)
const copiedTransactionId = ref(false)

const averageTransaction = computed(() => {
  if (transactions.value.length === 0) return 0
  const total = transactions.value.reduce((sum, tx) => sum + tx.amount, 0)
  return total / transactions.value.length
})

const categoryStats = computed(() => {
  const categories: Record<string, number> = {
    'Payments Sent': 0,
    'Payments Received': 0
  }
  transactions.value.forEach(tx => {
    if (tx.senderId === selectedUserId.value) {
      categories['Payments Sent'] += tx.amount
    } else if (tx.receiverId === selectedUserId.value) {
      categories['Payments Received'] += tx.amount
    }
  })
  return categories
})

async function loadHistory() {
  loading.value = true
  try {
    const res = await axios.get(`/api/transaction/history/${selectedUserId.value}`)
    transactions.value = res.data.sort((a: Transaction, b: Transaction) => 
      new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
    )
  } catch (err) {
    console.error('Failed to load history', err)
  } finally {
    loading.value = false
  }
}

function openTransactionDetails(tx: Transaction) {
  selectedTransaction.value = tx
  showTransactionModal.value = true
}

function closeTransactionModal() {
  showTransactionModal.value = false
  selectedTransaction.value = null
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

onMounted(loadHistory)
</script>

<template>
  <div class="max-w-6xl mx-auto space-y-6">
    <!-- Header -->
    <div>
      <h2 class="text-3xl font-black text-slate-900 dark:text-slate-50">Analytics & Insights</h2>
      <p class="text-slate-500 dark:text-slate-400 mt-1">Track your spending and transaction history</p>
    </div>

    <!-- Stats Cards -->
    <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
      <div class="bg-white dark:bg-slate-800 rounded-2xl border border-slate-200 dark:border-slate-700/60 p-6 shadow-sm">
        <div class="flex items-start justify-between gap-4">
          <div class="min-w-0 flex-1">
            <p class="text-slate-500 dark:text-slate-400 text-sm font-medium uppercase tracking-wider truncate">Total Sent</p>
            <p class="font-black text-slate-900 dark:text-slate-50 mt-2 text-2xl sm:text-3xl line-clamp-2 break-words">{{ formatCurrencyCompact(totalSpent) }}</p>
          </div>
          <div class="w-12 h-12 rounded-xl bg-rose-100 dark:bg-rose-900/40 flex items-center justify-center flex-shrink-0">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="#ef4444" class="w-6 h-6">
              <path fill-rule="evenodd" d="M3 10a.75.75 0 01.75-.75h10.638l-1.873-1.873a.75.75 0 111.06-1.06l3.5 3.5a.75.75 0 010 1.06l-3.5 3.5a.75.75 0 11-1.06-1.06l1.873-1.873H3.75A.75.75 0 013 10z" clip-rule="evenodd" />
            </svg>
          </div>
        </div>
      </div>

      <div class="bg-white dark:bg-slate-800 rounded-2xl border border-slate-200 dark:border-slate-700/60 p-6 shadow-sm">
        <div class="flex items-start justify-between gap-4">
          <div class="min-w-0 flex-1">
            <p class="text-slate-500 dark:text-slate-400 text-sm font-medium uppercase tracking-wider truncate">Total Received</p>
            <p class="font-black text-emerald-600 dark:text-emerald-300 mt-2 text-2xl sm:text-3xl line-clamp-2 break-words">{{ formatCurrencyCompact(totalReceived) }}</p>
          </div>
          <div class="w-12 h-12 rounded-xl bg-emerald-100 dark:bg-emerald-900/40 flex items-center justify-center flex-shrink-0">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="#10b981" class="w-6 h-6">
              <path d="M9.25 13.25a.75.75 0 001.5 0V4.636l2.955 3.129a.75.75 0 001.09-1.03l-4.25-4.5a.75.75 0 00-1.09 0l-4.25 4.5a.75.75 0 101.09 1.03l2.955-3.129v8.614z" />
            </svg>
          </div>
        </div>
      </div>

      <div class="bg-white dark:bg-slate-800 rounded-2xl border border-slate-200 dark:border-slate-700/60 p-6 shadow-sm">
        <div class="flex items-start justify-between gap-4">
          <div class="min-w-0 flex-1">
            <p class="text-slate-500 dark:text-slate-400 text-sm font-medium uppercase tracking-wider truncate">Transactions</p>
            <p class="font-black text-slate-900 dark:text-slate-50 mt-2 text-2xl sm:text-3xl">{{ transactions.length }}</p>
          </div>
          <div class="w-12 h-12 rounded-xl bg-blue-100 dark:bg-blue-900/40 flex items-center justify-center flex-shrink-0">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="#3b82f6" class="w-5 h-5">
              <path fill-rule="evenodd" d="M17 6a.75.75 0 01-.75.75H4.257l2.471 2.471a.75.75 0 11-1.06 1.06l-3.75-3.75a.75.75 0 010-1.06l3.75-3.75a.75.75 0 111.06 1.06L4.257 5.25H16.25A.75.75 0 0117 6z" clip-rule="evenodd" />
              <path fill-rule="evenodd" d="M3 15a.75.75 0 01.75-.75h12.993l-2.471-2.471a.75.75 0 111.06-1.06l3.75 3.75a.75.75 0 010 1.06l-3.75 3.75a.75.75 0 11-1.06-1.06l2.471-2.471H3.75A.75.75 0 013 15z" clip-rule="evenodd" />
            </svg>
          </div>
        </div>
      </div>

      <div class="bg-white dark:bg-slate-800 rounded-2xl border border-slate-200 dark:border-slate-700/60 p-6 shadow-sm">
        <div class="flex items-start justify-between gap-4">
          <div class="min-w-0 flex-1">
            <p class="text-slate-500 dark:text-slate-400 text-sm font-medium uppercase tracking-wider truncate">Avg Transaction</p>
            <p class="font-black text-indigo-600 dark:text-indigo-300 mt-2 text-2xl sm:text-3xl line-clamp-2 break-words">{{ formatCurrencyCompact(averageTransaction) }}</p>
          </div>
          <div class="w-12 h-12 rounded-xl bg-indigo-100 dark:bg-indigo-900/40 flex items-center justify-center flex-shrink-0">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="#4f46e5" class="w-6 h-6">
              <path d="M10.5 1.5H5.75A2.75 2.75 0 003 4.25v11.5A2.75 2.75 0 005.75 18.5h8.5A2.75 2.75 0 0017 15.75V9.5m-15-4h6m0 6h6" />
            </svg>
          </div>
        </div>
      </div>
    </div>

    <!-- Category Breakdown -->
    <div class="bg-white dark:bg-slate-800 rounded-2xl border border-slate-200 dark:border-slate-700/60 p-6 shadow-sm">
      <h3 class="font-bold text-slate-900 dark:text-slate-50 mb-6">Spending by Category</h3>
      <div class="space-y-4">
        <div v-for="(amount, category) in categoryStats" :key="category">
          <div class="flex justify-between items-center mb-2">
            <p class="font-semibold text-slate-900 dark:text-slate-50">{{ category }}</p>
            <p class="font-bold text-slate-900 dark:text-slate-50">{{ formatCurrencyCompact(amount) }}</p>
          </div>
          <div class="w-full bg-slate-100 dark:bg-slate-700/60 rounded-full h-2 overflow-hidden">
            <div 
              class="h-full bg-indigo-600 rounded-full transition-all"
              :style="{ width: totalSpent > 0 ? `${(amount / (totalSpent + totalReceived)) * 100}%` : '0%' }"
            ></div>
          </div>
        </div>
      </div>
    </div>

    <!-- Recent Transactions -->
    <div class="bg-white dark:bg-slate-800 rounded-2xl border border-slate-200 dark:border-slate-700/60 overflow-hidden shadow-sm">
      <div class="px-6 py-5 border-b border-slate-100 dark:border-slate-700 bg-slate-50 dark:bg-slate-700/30">
        <h3 class="font-bold text-slate-900 dark:text-slate-50">Recent Transactions</h3>
      </div>
      <div class="divide-y divide-slate-100 dark:divide-slate-700">
        <button
          v-for="tx in transactions.slice(0, 10)"
          :key="tx.id"
          @click="openTransactionDetails(tx)"
          class="w-full text-left px-6 py-4 flex items-center justify-between hover:bg-slate-50 dark:hover:bg-slate-700/40 transition-all cursor-pointer group"
        >
          <div class="flex items-center gap-4">
            <div :class="[
              'w-10 h-10 rounded-full flex items-center justify-center text-white font-bold group-hover:scale-110 transition-transform',
              tx.senderId === selectedUserId ? 'bg-rose-500' : 'bg-emerald-500'
            ]">
              {{ tx.senderId === selectedUserId ? '↑' : '↓' }}
            </div>
            <div>
              <p class="font-semibold text-slate-900 dark:text-slate-50">{{ tx.senderId === selectedUserId ? 'Sent' : 'Received' }}</p>
              <p class="text-xs text-slate-500 dark:text-slate-400">{{ new Date(tx.timestamp).toLocaleDateString() }}</p>
            </div>
          </div>
          <p :class="[
            'font-bold text-lg tabular-nums',
            tx.senderId === selectedUserId ? 'text-rose-600 dark:text-rose-300' : 'text-emerald-600 dark:text-emerald-300'
          ]">
            {{ tx.senderId === selectedUserId ? '−' : '+' }}{{ formatCurrencyCompact(tx.amount) }}
          </p>
        </button>
      </div>
    </div>

    <!-- Transaction Detail Modal -->
    <Teleport to="body" v-if="showTransactionModal && selectedTransaction">
      <Transition name="fade">
        <div v-if="showTransactionModal" class="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <Transition name="scale">
            <div v-if="showTransactionModal" class="bg-white dark:bg-slate-800 rounded-2xl border border-slate-200 dark:border-slate-700 shadow-xl max-w-md w-full">
              <!-- Header -->
              <div class="px-6 py-5 border-b border-slate-200 dark:border-slate-700 flex items-center justify-between">
                <div class="flex items-center gap-3">
                  <div :class="[
                    'w-10 h-10 rounded-full flex items-center justify-center text-white font-bold',
                    selectedTransaction.senderId === selectedUserId ? 'bg-rose-500' : 'bg-emerald-500'
                  ]">
                    <svg v-if="selectedTransaction.senderId === selectedUserId" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-6 h-6">
                      <path fill-rule="evenodd" d="M3 10a.75.75 0 01.75-.75h10.638l-1.873-1.873a.75.75 0 111.06-1.06l3.5 3.5a.75.75 0 010 1.06l-3.5 3.5a.75.75 0 11-1.06-1.06l1.873-1.873H3.75A.75.75 0 013 10z" clip-rule="evenodd" />
                    </svg>
                    <svg v-else xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-6 h-6">
                      <path d="M9.25 13.25a.75.75 0 001.5 0V4.636l2.955 3.129a.75.75 0 001.09-1.03l-4.25-4.5a.75.75 0 00-1.09 0l-4.25 4.5a.75.75 0 101.09 1.03l2.955-3.129v8.614z" />
                    </svg>
                  </div>
                  <div>
                    <h3 class="font-bold text-slate-900 dark:text-slate-50">{{ selectedTransaction.senderId === selectedUserId ? 'Payment Sent' : 'Payment Received' }}</h3>
                    <p class="text-xs text-slate-500 dark:text-slate-400">{{ new Date(selectedTransaction.timestamp).toUTCString().split(' ').slice(0, 4).join(' ') }}</p>
                  </div>
                </div>
                <button
                  @click="closeTransactionModal"
                  class="p-1 hover:bg-slate-100 dark:hover:bg-slate-700 rounded-lg transition-colors"
                >
                  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-5 h-5 text-slate-500 dark:text-slate-400">
                    <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd" />
                  </svg>
                </button>
              </div>
              <!-- Amount -->
              <div class="px-6 py-6 border-b border-slate-200 dark:border-slate-700 text-center">
                <p :class="[
                  'text-4xl font-black',
                  selectedTransaction.senderId === selectedUserId ? 'text-rose-600 dark:text-rose-300' : 'text-emerald-600 dark:text-emerald-300'
                ]">
                  {{ selectedTransaction.senderId === selectedUserId ? '−' : '+' }}{{ formatCurrencyCompact(selectedTransaction.amount) }}
                </p>
              </div>

              <!-- Details -->
              <div class="px-6 py-6 space-y-4">
                <!-- Transaction ID -->
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

                <!-- Status -->
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

                <!-- Time -->
                <div>
                  <p class="text-xs font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider mb-2">Time</p>
                  <p class="text-sm text-slate-900 dark:text-slate-50">{{ formatCentralTime(selectedTransaction.timestamp) }} CST</p>
                </div>
              </div>

              <!-- Close Button -->
              <div class="px-6 py-4 border-t border-slate-200 dark:border-slate-700">
                <button
                  @click="closeTransactionModal"
                  class="w-full px-4 py-2 rounded-lg font-semibold bg-slate-100 dark:bg-slate-700 text-slate-900 dark:text-slate-50 hover:bg-slate-200 dark:hover:bg-slate-600 transition-colors"
                >
                  Close
                </button>
              </div>
            </div>
          </Transition>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.scale-enter-active,
.scale-leave-active {
  transition: all 0.2s ease;
}

.scale-enter-from,
.scale-leave-to {
  opacity: 0;
  transform: scale(0.95);
}
</style>
