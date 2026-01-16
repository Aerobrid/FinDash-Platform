<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import axios from 'axios'
import { useRouter } from 'vue-router'
import { useAuth } from '../composables/useAuth'
import DeleteConfirmModal from '../components/DeleteConfirmModal.vue'

interface Contact {
  id: string
  fullName: string
  email: string
}

interface User {
  id: string
  fullName: string
  email: string
}

const { currentUser } = useAuth()
const contacts = ref<Contact[]>([])
const allUsers = ref<User[]>([])
const searchQuery = ref('')
const searchResults = ref<User[]>([])
const showSearchResults = ref(false)
const loading = ref(false)
const router = useRouter()

const currentUserId = computed(() => currentUser.value?.id || '')
const CONTACTS_STORAGE_KEY = computed(() => `contacts_${currentUserId.value}`)

// Delete confirmation modal
const showDeleteModal = ref(false)
const contactToDelete = ref<Contact | null>(null)

function loadContactsFromStorage() {
  try {
    const stored = localStorage.getItem(CONTACTS_STORAGE_KEY.value)
    if (stored) {
      contacts.value = JSON.parse(stored)
    }
  } catch (err) {
    console.error('Failed to load contacts from storage', err)
  }
}

function saveContactsToStorage() {
  try {
    localStorage.setItem(CONTACTS_STORAGE_KEY.value, JSON.stringify(contacts.value))
  } catch (err) {
    console.error('Failed to save contacts to storage', err)
  }
}

async function loadContacts() {
  try {
    // load all users from backend
    const res = await axios.get('/api/wallet/users')
    allUsers.value = res.data
    
    // Load contacts from localStorage
    loadContactsFromStorage()
  } catch (err) {
    console.error('Failed to load users', err)
  }
}

async function searchUsers() {
  if (!searchQuery.value.trim()) {
    searchResults.value = []
    showSearchResults.value = false
    return
  }

  loading.value = true
  try {
    // Filter users by name or email, excluding current user
    const query = searchQuery.value.toLowerCase()
    searchResults.value = allUsers.value.filter(user =>
      user.id !== currentUserId.value && // Exclude yourself
      (user.fullName.toLowerCase().includes(query) ||
      user.email.toLowerCase().includes(query))
    )
    showSearchResults.value = true
  } catch (err) {
    console.error('Search failed', err)
  } finally {
    loading.value = false
  }
}

function addContact(user: User) {
  // Check if trying to add yourself
  if (user.id === currentUserId.value) {
    alert('You cannot add yourself as a contact')
    return
  }
  
  // Check if already added
  if (contacts.value.find(c => c.id === user.id)) {
    alert('This contact is already added')
    return
  }

  contacts.value.push({
    id: user.id,
    fullName: user.fullName,
    email: user.email
  })
  
  saveContactsToStorage()
  searchQuery.value = ''
  searchResults.value = []
  showSearchResults.value = false
}

function deleteContact(id: string) {
  const contact = contacts.value.find(c => c.id === id)
  if (contact) {
    contactToDelete.value = contact
    showDeleteModal.value = true
  }
}

function confirmDelete() {
  if (contactToDelete.value) {
    contacts.value = contacts.value.filter(c => c.id !== contactToDelete.value!.id)
    saveContactsToStorage()
    showDeleteModal.value = false
    contactToDelete.value = null
  }
}

function cancelDelete() {
  showDeleteModal.value = false
  contactToDelete.value = null
}

onMounted(loadContacts)
</script>

<template>
  <div class="max-w-4xl mx-auto space-y-6">
    <!-- Header -->
    <div>
      <h2 class="text-3xl font-black text-slate-900 dark:text-slate-50">Contacts</h2>
      <p class="text-slate-500 dark:text-slate-400 mt-1">Search for users and add them to your contacts</p>
    </div>

    <!-- Search Box -->
    <div class="bg-white dark:bg-slate-800 rounded-2xl border border-slate-200 dark:border-slate-700 p-6">
      <label class="block text-sm font-bold text-slate-900 dark:text-slate-50 mb-3">Search Users</label>
      <div class="relative">
        <input 
          v-model="searchQuery"
          @input="searchUsers"
          type="text"
          placeholder="Search by name or email..."
          class="w-full px-4 py-3 border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-700 text-slate-900 dark:text-slate-50 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:border-transparent outline-none placeholder:text-slate-500 dark:placeholder:text-slate-400"
        />
        
        <!-- Search Results Dropdown -->
        <div v-if="showSearchResults" class="absolute top-full left-0 right-0 mt-2 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl shadow-lg z-10 max-h-64 overflow-y-auto">
          <div v-if="searchResults.length === 0" class="p-4 text-center text-slate-500 dark:text-slate-400">
            No users found
          </div>
          <button
            v-for="user in searchResults"
            :key="user.id"
            @click="addContact(user)"
            class="w-full text-left px-4 py-3 hover:bg-indigo-50 dark:hover:bg-slate-700/50 border-b border-slate-100 dark:border-slate-700 last:border-b-0 transition-all flex items-center justify-between group"
          >
            <div>
              <p class="font-bold text-slate-900 dark:text-white">{{ user.fullName }}</p>
              <p class="text-xs text-slate-500 dark:text-slate-400">{{ user.email }}</p>
            </div>
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-5 h-5 text-indigo-600 opacity-0 group-hover:opacity-100 transition-opacity">
              <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.857-9.809a.75.75 0 00-1.214-.882l-3.483 4.79-1.88-1.88a.75.75 0 10-1.06 1.061l2.5 2.5a.75.75 0 001.137-.089l4-5.5z" clip-rule="evenodd" />
            </svg>
          </button>
        </div>
      </div>
    </div>

    <!-- Contact List -->
    <div v-if="contacts.length > 0" class="bg-white dark:bg-slate-800 rounded-2xl border border-slate-200 dark:border-slate-700 p-6 shadow-sm">
      <h3 class="font-bold text-slate-900 dark:text-slate-50 mb-4">My Contacts ({{ contacts.length }})</h3>
      <div class="space-y-2">
        <div
          v-for="contact in contacts"
          :key="contact.id"
          class="flex items-center justify-between p-4 border border-slate-200 dark:border-slate-700 rounded-xl hover:bg-slate-50 dark:hover:bg-slate-700/50 transition-all"
        >
          <div class="flex-1">
            <p class="font-bold text-slate-900 dark:text-slate-50">{{ contact.fullName }}</p>
            <p class="text-sm text-slate-500 dark:text-slate-400">{{ contact.email }}</p>
          </div>
          <div class="flex items-center gap-2">
            <button
              @click="router.push({ name: 'contact-detail', params: { id: contact.id } })"
              class="p-2 hover:bg-indigo-100 dark:hover:bg-indigo-800 text-indigo-600 dark:text-indigo-400 dark:hover:text-indigo-300 rounded-lg transition-all"
              title="View Details"
            >
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-5 h-5">
                <path d="M10.5 1.5H5.75A2.75 2.75 0 003 4.25v11.5A2.75 2.75 0 005.75 18.5h8.5A2.75 2.75 0 0017 15.75V9.5M10 6.5v3m0 3v3M6.5 10h7" />
              </svg>
            </button>
            <button
              @click="deleteContact(contact.id)"
              class="p-2 hover:bg-rose-100 dark:hover:bg-rose-900/30 text-rose-600 dark:text-rose-400 rounded-lg transition-all"
              title="Delete"
            >
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-5 h-5">
                <path fill-rule="evenodd" d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z" clip-rule="evenodd" />
              </svg>
            </button>
          </div>
        </div>
      </div>
    </div>
    <div v-else class="bg-slate-50 dark:bg-slate-800 rounded-2xl border border-slate-200 dark:border-slate-700 p-12 text-center">
      <p class="text-slate-500 dark:text-slate-400">No contacts yet. Search for users to add them.</p>
    </div>
  </div>

  <!-- Delete Confirmation Modal -->
  <DeleteConfirmModal
    :is-open="showDeleteModal"
    title="Delete Contact"
    message="Are you sure you want to delete this contact?"
    :item-name="contactToDelete?.fullName"
    @confirm="confirmDelete"
    @cancel="cancelDelete"
  />
</template>
