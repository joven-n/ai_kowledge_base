import request from './request'
import type { AskRequest, AskResponse } from '@/types'

// 多轮对话（集成意图检测 + RAG）
export function chat(data: AskRequest): Promise<AskResponse> {
  return request.post('/ai/chat', data)
}

// Agent 工具调用
export function agent(data: AskRequest): Promise<AskResponse> {
  return request.post('/ai/agent', data)
}

// 删除会话
export function deleteSession(sessionId: string): Promise<void> {
  return request.delete(`/ai/session/${sessionId}`)
}
