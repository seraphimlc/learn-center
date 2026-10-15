
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import http from '@/api/http'

const ADMIN_ROLES = ['ADMIN', 'CONTENT_ADMIN', 'DATA_VIEWER']

export const useAuthStore = defineStore('admin-auth', () => {
  const token = ref(localStorage.getItem('vlp_admin_token') || '')
  const user = ref(JSON.parse(localStorage.getItem('vlp_admin_user') || 'null'))
  const isLogin = computed(() => !!token.value)
  const role = computed(() => user.value?.role || '')
  const isSuperAdmin = computed(() => role.value === 'ADMIN')
  const canWriteContent = computed(() => role.value === 'ADMIN' || role.value === 'CONTENT_ADMIN')
  const canManageUsers = computed(() => role.value === 'ADMIN')
  const isReadOnly = computed(() => role.value === 'DATA_VIEWER')

  async function login(form) {
    const res = await http.post('/auth/login', form)
    if (!ADMIN_ROLES.includes(res.data.role)) throw new Error('该账号无后台权限')
    token.value = res.data.token
    user.value = res.data
    localStorage.setItem('vlp_admin_token', token.value)
    localStorage.setItem('vlp_admin_user', JSON.stringify(user.value))
  }
  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem('vlp_admin_token')
    localStorage.removeItem('vlp_admin_user')
  }
  return {
    token, user, isLogin, role,
    isSuperAdmin, canWriteContent, canManageUsers, isReadOnly,
    login, logout
  }
})
