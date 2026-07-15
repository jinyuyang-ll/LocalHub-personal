import axios from 'axios'

export const api = axios.create({
  baseURL: '/api'
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.authorization = token
  }
  return config
})

api.interceptors.response.use(response => response, error => {
  if (error?.response?.status === 401) {
    localStorage.removeItem('token')
    if (window.location.pathname !== '/login') window.location.assign('/login')
  }
  return Promise.reject(error)
})

export interface ApiResult<T = unknown> {
  success: boolean
  errorMsg?: string
  data?: T
  total?: number
}

export function unwrap<T>(res: { data: ApiResult<T> }): T {
  if (!res.data.success) {
    throw new Error(res.data.errorMsg || '请求失败')
  }
  return res.data.data as T
}

export async function streamSse(path: string, onMessage: (chunk: string) => void, signal?: AbortSignal): Promise<void> {
  const token = localStorage.getItem('token')
  const response = await fetch(`/api${path}`, {
    headers: token ? { authorization: token } : {}, signal
  })
  if (!response.ok || !response.body) throw new Error(`流式请求失败：${response.status}`)
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { value, done } = await reader.read()
    buffer += decoder.decode(value, { stream: !done })
    const events = buffer.split(/\r?\n\r?\n/)
    buffer = events.pop() || ''
    for (const event of events) {
      const type = event.match(/^event:\s*(.+)$/m)?.[1]
      const data = event.match(/^data:\s?(.*)$/m)?.[1] || ''
      if (type === 'message') onMessage(data)
      if (type === 'done') return
    }
    if (done) return
  }
}
