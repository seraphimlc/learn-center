
import axios from 'axios'
import { useAuthStore } from '@/stores/auth'

const http = axios.create({ baseURL: '/api', timeout: 60000 })

http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) config.headers.Authorization = `Bearer ${auth.token}`
  return config
})

function friendlyMessage(err) {
  const status = err.response?.status
  const serverMsg = err.response?.data?.message
  if (serverMsg && typeof serverMsg === 'string' && !/Exception|at \w+\.|stack/i.test(serverMsg)) {
    return serverMsg
  }
  if (status === 401) return '登录已过期，请重新登录'
  if (status === 403) return '无权限访问'
  if (status === 404) return '资源不存在'
  if (status === 429) return '请求过于频繁，请稍后再试'
  if (status >= 500) return '服务暂时不可用，请稍后重试'
  if (err.code === 'ECONNABORTED') return '请求超时，请检查网络后重试'
  if (!err.response) return '网络异常，请检查网络连接'
  return '请求失败，请稍后重试'
}

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
    const msg = friendlyMessage(err)
    if (err.response?.status === 401) {
      const auth = useAuthStore()
      auth.logout()
      if (!location.pathname.startsWith('/login')) location.href = '/login'
    }
    return Promise.reject(new Error(msg))
  }
)

export default http
