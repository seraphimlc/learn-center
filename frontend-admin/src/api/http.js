
import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({ baseURL: '/api', timeout: 120000 })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('vlp_admin_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

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
    const msg = err.response?.data?.message || err.message || '网络异常'
    if (err.response?.status === 401 || err.response?.status === 403) {
      localStorage.removeItem('vlp_admin_token')
      if (!location.pathname.includes('/login')) location.href = '/admin/login'
    }
    ElMessage.error(msg)
    return Promise.reject(new Error(msg))
  }
)

export default http
