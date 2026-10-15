
import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({ baseURL: '/api', timeout: 120000 })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('vlp_admin_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
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
  if (status === 429) return '请求过于频繁，请稍后再试'
  if (status >= 500) return '服务暂时不可用，请稍后重试'
  if (!err.response) return '网络异常，请检查网络连接'
  return '请求失败，请稍后重试'
}

http.interceptors.response.use(
  (res) => {
    if (res.config.responseType === 'blob') return res
    const body = res.data
    if (body && typeof body.code === 'number' && body.code !== 0) {
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  },
  (err) => {
    const msg = friendlyMessage(err)
    if (err.response?.status === 401 || err.response?.status === 403) {
      localStorage.removeItem('vlp_admin_token')
      if (!location.pathname.includes('/login')) location.href = '/admin/login'
    }
    ElMessage.error(msg)
    return Promise.reject(new Error(msg))
  }
)

export default http
