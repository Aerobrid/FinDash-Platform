<script setup lang="ts">
import { formatCurrencyCompact } from '../utils/formatNumber'

interface Props {
  id: string
  type: 'incoming' | 'outgoing'
  amount: number
  timestamp: string
  status?: string
}

const props = defineProps<Props>()

const formatTime = (timestamp: string) => {
  // Ensure timestamp is treated as UTC by adding Z if not present
  const utcTimestamp = timestamp.endsWith('Z') ? timestamp : timestamp + 'Z'
  const date = new Date(utcTimestamp)
  const time = date.toLocaleTimeString('en-US', { 
    timeZone: 'America/Chicago',
    hour: '2-digit', 
    minute: '2-digit' 
  })
  const dateStr = date.toLocaleDateString('en-US', { 
    timeZone: 'America/Chicago',
    month: 'short', 
    day: 'numeric' 
  })
  return { time, date: dateStr }
}

const { time, date } = formatTime(props.timestamp)
</script>

<template>
  <div :class="[
    'flex items-center justify-between p-4 rounded-xl transition-all group border border-transparent cursor-pointer',
    'hover:bg-slate-50/80 dark:hover:bg-slate-700/50 hover:border-slate-100 dark:hover:border-slate-600'
  ]">
    <div class="flex items-center gap-4">
      <div :class="[
        'w-12 h-12 rounded-xl flex items-center justify-center font-bold text-sm shadow-sm transition-transform group-hover:scale-105',
        type === 'outgoing' 
          ? 'bg-gradient-to-br from-slate-100 dark:from-slate-700 to-slate-200 dark:to-slate-600 text-slate-700 dark:text-slate-300' 
          : 'bg-gradient-to-br from-emerald-400 to-emerald-500 text-white'
      ]">
        <svg v-if="type === 'outgoing'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-5 h-5">
          <path d="M10.75 2.75a.75.75 0 00-1.5 0v8.614L6.295 8.235a.75.75 0 10-1.09 1.03l4.25 4.5a.75.75 0 001.09 0l4.25-4.5a.75.75 0 00-1.09-1.03l-2.955 3.129V2.75z" />
          <path d="M3.5 12.75a.75.75 0 00-1.5 0v2.5A2.75 2.75 0 004.75 18h10.5A2.75 2.75 0 0018 15.25v-2.5a.75.75 0 00-1.5 0v2.5c0 .69-.56 1.25-1.25 1.25H4.75c-.69 0-1.25-.56-1.25-1.25v-2.5z" />
        </svg>
        <svg v-else xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-5 h-5">
          <path d="M9.25 13.25a.75.75 0 001.5 0V4.636l2.955 3.129a.75.75 0 001.09-1.03l-4.25-4.5a.75.75 0 00-1.09 0l-4.25 4.5a.75.75 0 101.09 1.03l2.955-3.129v8.614z" />
          <path d="M3.5 12.75a.75.75 0 00-1.5 0v2.5A2.75 2.75 0 004.75 18h10.5A2.75 2.75 0 0018 15.25v-2.5a.75.75 0 00-1.5 0v2.5c0 .69-.56 1.25-1.25 1.25H4.75c-.69 0-1.25-.56-1.25-1.25v-2.5z" />
        </svg>
      </div>
      <div class="space-y-1">
        <p class="font-bold text-slate-900 dark:text-slate-50 text-sm">
          {{ type === 'outgoing' ? 'Payment Sent' : 'Payment Received' }}
        </p>
        <div class="flex items-center gap-2 text-xs">
          <span class="text-slate-500 dark:text-slate-400 font-medium">{{ time }}</span>
          <span class="w-1 h-1 rounded-full bg-slate-300 dark:bg-slate-600"></span>
          <span class="text-slate-400 dark:text-slate-500 font-medium">{{ date }}</span>
          <span class="w-1 h-1 rounded-full bg-slate-300 dark:bg-slate-600"></span>
          <span class="text-slate-400 dark:text-slate-500 font-mono">{{ id.substring(0, 8) }}</span>
        </div>
      </div>
    </div>
    <div class="text-right">
      <div :class="[
        'text-xl font-black tabular-nums tracking-tight',
        type === 'outgoing' ? 'text-slate-900 dark:text-slate-50' : 'text-emerald-600 dark:text-emerald-400'
      ]">
        {{ type === 'outgoing' ? '−' : '+' }}{{ formatCurrencyCompact(amount) }}
      </div>
      <span v-if="status" class="inline-block mt-1 px-2 py-0.5 rounded-md bg-emerald-50 dark:bg-emerald-900/20 text-emerald-600 dark:text-emerald-400 text-[10px] font-bold uppercase">
        {{ status }}
      </span>
    </div>
  </div>
</template>
