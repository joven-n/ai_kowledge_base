import request from './request'
import type { SystemHealth, ApiInfo } from '@/types'

// 获取系统健康状态
export function getHealth(): Promise<SystemHealth> {
  return request.get('/system/health')
}

// 获取 API 列表
export function getApis(): Promise<ApiInfo[]> {
  return request.get('/system/apis')
}
