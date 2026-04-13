// 通用响应包装
export interface Result<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

// AI 问答请求
export interface AskRequest {
  question?: string
  message?: string
  sessionId?: string
  useRag?: boolean
}

// AI 问答响应
export interface AskResponse {
  answer: string
  sessionId: string
  sources?: Source[]
  toolsCalled?: string[]
  costMs: number
  tokensUsed: number
  model: string
}

// RAG 来源
export interface Source {
  documentId: string
  documentName: string
  content: string
  score: number
}

// 聊天消息
export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  sources?: Source[]
  toolsCalled?: string[]
  costMs?: number
  tokensUsed?: number
  model?: string
  timestamp: number
}

// 知识库文档
export interface KnowledgeDocument {
  id: string
  name: string
  content: string
  source: string
  createdAt: string
}

// 知识库统计
export interface KnowledgeStats {
  documentCount: number
  chunkCount: number
}

// 系统健康状态
export interface SystemHealth {
  status: string
  version: string
  uptime: string
  memory: {
    total: number
    used: number
    free: number
  }
  threads: number
  processors: number
}

// API 接口信息
export interface ApiInfo {
  path: string
  method: string
  description: string
}

// 聊天模式
export type ChatMode = 'chat' | 'agent'
