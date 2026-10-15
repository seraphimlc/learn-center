
import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import { toast } from '@/utils/toast'

const http = axios.create({ baseURL: '/api', timeout: 60000 })

http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) config.headers.Authorization = `Bearer ${auth.token}`
  return config
})

http.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && typeof body.code === 'number' && body.code !== 0) {
      const err = new Error(body.message || '请求失败')
      err.code = body.code
      return Promise.reject(err)
    }
    return body
  },
  (err) => {
    const msg = err.response?.data?.message || err.message || '网络异常'
    if (err.response?.status === 401) {
      const auth = useAuthStore()
      auth.logout()
      if (!location.pathname.startsWith('/login')) location.href = '/login'
    }
    return Promise.reject(new Error(msg))
  }
)

export default http
