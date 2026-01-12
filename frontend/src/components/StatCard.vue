<script setup lang="ts">
interface Props {
  title: string
  value: string | number
  icon: string
  trend?: string
  trendUp?: boolean
  subtitle?: string
}

defineProps<Props>()
</script>

<template>
  <div class="bg-white dark:bg-slate-800 rounded-2xl border border-slate-200/60 dark:border-slate-700/60 p-6 shadow-sm hover:shadow-md transition-all hover:border-indigo-200/60 dark:hover:border-indigo-500/30 group">
    <div class="flex items-start justify-between">
      <div class="flex-1 min-w-0">
        <div class="flex items-center gap-2 mb-3">
          <div class="w-10 h-10 rounded-xl bg-gradient-to-br from-indigo-500 to-indigo-600 flex items-center justify-center text-white shadow-lg shadow-indigo-500/30 group-hover:scale-110 transition-transform flex-shrink-0" v-html="icon"></div>
          <span class="text-xs font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider truncate">{{ title }}</span>
        </div>
        <div class="space-y-1 min-w-0">
          <div class="font-black text-slate-900 dark:text-slate-50 tracking-tight line-clamp-2 break-words text-2xl sm:text-3xl" style="word-break: break-word;">{{ value }}</div>
          <p v-if="subtitle" class="text-xs text-slate-500 dark:text-slate-400 font-medium">{{ subtitle }}</p>
        </div>
      </div>
      <div v-if="trend" :class="[
        'px-3 py-1 rounded-full text-xs font-bold flex items-center gap-1 flex-shrink-0',
        trendUp ? 'bg-emerald-50 dark:bg-emerald-900/30 text-emerald-600 dark:text-emerald-400' : 'bg-rose-50 dark:bg-rose-900/30 text-rose-600 dark:text-rose-400'
      ]">
        <svg v-if="trendUp" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-3 h-3">
          <path fill-rule="evenodd" d="M10 17a.75.75 0 01-.75-.75V5.612L5.29 9.77a.75.75 0 01-1.08-1.04l5.25-5.5a.75.75 0 011.08 0l5.25 5.5a.75.75 0 11-1.08 1.04l-3.96-4.158V16.25A.75.75 0 0110 17z" clip-rule="evenodd" />
        </svg>
        <svg v-else xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-3 h-3">
          <path fill-rule="evenodd" d="M10 3a.75.75 0 01.75.75v10.638l3.96-4.158a.75.75 0 111.08 1.04l-5.25 5.5a.75.75 0 01-1.08 0l-5.25-5.5a.75.75 0 111.08-1.04l3.96 4.158V3.75A.75.75 0 0110 3z" clip-rule="evenodd" />
        </svg>
        {{ trend }}
      </div>
    </div>
  </div>
</template>
