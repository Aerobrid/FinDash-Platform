<script setup lang="ts">
import { ref } from 'vue'

interface Props {
  title?: string
  message?: string
  itemName?: string
  isOpen: boolean
}

const props = withDefaults(defineProps<Props>(), {
  title: 'Delete Item',
  message: 'This action cannot be undone.',
  itemName: ''
})

const emit = defineEmits<{
  confirm: []
  cancel: []
}>()

const isConfirming = ref(false)

function handleConfirm() {
  isConfirming.value = true
  setTimeout(() => {
    emit('confirm')
    isConfirming.value = false
  }, 100)
}

function handleCancel() {
  emit('cancel')
}
</script>

<template>
  <Teleport to="body" v-if="isOpen">
    <Transition name="fade">
      <div v-if="isOpen" class="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
        <Transition name="scale">
          <div v-if="isOpen" class="bg-white dark:bg-slate-800 rounded-2xl border border-slate-200 dark:border-slate-700 shadow-xl max-w-sm w-full mx-4">
            <!-- Header -->
            <div class="px-6 py-5 border-b border-slate-200 dark:border-slate-700">
              <h3 class="text-lg font-bold text-slate-900 dark:text-slate-50">{{ title }}</h3>
            </div>

            <!-- Content -->
            <div class="px-6 py-4">
              <p class="text-slate-600 dark:text-slate-300 mb-2">{{ message }}</p>
              <p v-if="itemName" class="text-sm font-semibold text-slate-900 dark:text-slate-50 bg-slate-100 dark:bg-slate-700/50 px-3 py-2 rounded-lg break-words">
                {{ itemName }}
              </p>
            </div>

            <!-- Actions -->
            <div class="px-6 py-4 border-t border-slate-200 dark:border-slate-700 flex gap-3 justify-end">
              <button
                @click="handleCancel"
                class="px-4 py-2 rounded-lg font-medium text-slate-700 dark:text-slate-300 bg-slate-100 dark:bg-slate-700/50 hover:bg-slate-200 dark:hover:bg-slate-700 transition-colors"
              >
                Cancel
              </button>
              <button
                @click="handleConfirm"
                :disabled="isConfirming"
                class="px-4 py-2 rounded-lg font-medium text-white bg-rose-600 hover:bg-rose-700 dark:bg-rose-700 dark:hover:bg-rose-600 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              >
                {{ isConfirming ? 'Deleting...' : 'Delete' }}
              </button>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>
  </Teleport>
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
