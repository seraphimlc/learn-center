
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import http from '@/api/http'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('vlp_token') || '')
  const user = ref(JSON.parse(localStorage.getItem('vlp_user') || 'null'))

  const isLogin = computed(() => !!token.value)

  function persist() {
    if (token.value) localStorage.setItem('vlp_token', token.value)
    else localStorage.removeItem('vlp_token')
    if (user.value) localStorage.setItem('vlp_user', JSON.stringify(user.value))
    else localStorage.removeItem('vlp_user')
  }

  function applyAuth(data) {
    if (!data || !data.token) throw new Error('无效的登录凭证')
    token.value = data.token
    user.value = data
    persist()
    return data
  }

  async function login(form) {
    const res = await http.post('/auth/login', form)
    return applyAuth(res.data)
  }

  async function register(form) {
    const res = await http.post('/auth/register', form)
    return applyAuth(res.data)
  }

  async function fetchMe() {
    if (!token.value) return null
    const res = await http.get('/auth/me')
    user.value = { ...user.value, ...res.data }
    persist()
    return res.data
  }

  function logout() {
    token.value = ''
    user.value = null
    persist()
  }

  return { token, user, isLogin, login, register, fetchMe, logout, applyAuth }
})
