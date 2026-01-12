import axios from 'axios'

// Set base URL
axios.defaults.baseURL = ''

// Add token to requests if available
axios.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// Handle 401 responses (expired tokens)
axios.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // Token expired or invalid
      localStorage.removeItem('token')
      localStorage.removeItem('userId')
      localStorage.removeItem('currentUser')
      delete axios.defaults.headers.common['Authorization']
      
      // Redirect to login if not already there
      if (window.location.pathname !== '/') {
        window.location.href = '/'
      }
    }
    return Promise.reject(error)
  }
)

export default axios
