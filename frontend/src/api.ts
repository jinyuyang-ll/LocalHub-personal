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
