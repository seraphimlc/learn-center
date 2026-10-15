
import { reactive } from 'vue'

export const toasts = reactive([])

export function toast(message, type = 'ok', ms = 2800) {
  const id = Date.now() + Math.random()
  toasts.push({ id, message, type })
  setTimeout(() => {
    const i = toasts.findIndex(t => t.id === id)
    if (i >= 0) toasts.splice(i, 1)
  }, ms)
}

export function formatDuration(sec = 0) {
  sec = Math.max(0, Math.floor(sec || 0))
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  const s = sec % 60
  if (h > 0) return `${h}:${String(m).padStart(2,'0')}:${String(s).padStart(2,'0')}`
  return `${m}:${String(s).padStart(2,'0')}`
}

export function mediaUrl(path) {
  if (!path) return ''
  if (path.startsWith('http') || path.startsWith('/api/')) return path
  return `/api/media/${path}`
}
