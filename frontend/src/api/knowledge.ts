import request from './request'
import type { KnowledgeDocument, KnowledgeStats } from '@/types'

// 上传文档
export function uploadFile(file: File): Promise<string> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/kb/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
}

// 添加文本
export function uploadText(content: string, source: string): Promise<string> {
  return request.post('/kb/text', { content, source })
}

// 获取文档列表
export function listDocuments(): Promise<KnowledgeDocument[]> {
  return request.get('/kb/documents')
}

// 获取统计信息
export function getStats(): Promise<KnowledgeStats> {
  return request.get('/kb/stats')
}

// 删除文档
export function deleteDocument(id: string): Promise<void> {
  return request.delete(`/kb/documents/${id}`)
}
