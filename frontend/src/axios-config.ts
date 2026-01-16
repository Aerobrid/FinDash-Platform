import axios from 'axios'

axios.defaults.baseURL = ''
axios.defaults.withCredentials = true // browser auto-sends cookies

axios.interceptors.request.use(
  (config) => config,
  (error) => Promise.reject(error)
)

// 401 -> redirect to login
axios.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && window.location.pathname !== '/') {
      window.location.href = '/'
    }
    return Promise.reject(error)
  }
)

export default axios
