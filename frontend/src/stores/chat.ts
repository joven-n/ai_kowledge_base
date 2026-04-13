import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { ChatMessage, ChatMode } from '@/types'
import { chat, agent, deleteSession } from '@/api/chat'
import { v4 as uuidv4 } from '@/utils/uuid'

// 前端轻量级跨境电商关键词检测（仅用于加载提示文字展示）
const CROSS_BORDER_KEYWORDS = [
  '亚马逊', 'amazon', 'lazada', 'shopee', '速卖通', 'aliexpress', 'ebay', 'wish',
  'temu', 'tiktok', '独立站', 'shopify', 'fba', 'fbm', '海外仓', '物流', '海运',
  '空运', 'dhl', 'ups', 'fedex', 'vat', '关税', '清关', 'listing', 'ppc', '广告',
  'acos', '关键词', 'seo', '选品', 'bsr', 'asin', 'sku', '库存', 'erp', '卖家精灵',
  'helium10', 'keepa', 'review', '测评', '品牌备案', '跨境', '出海', '外贸', '电商',
  '无货源', '一件代发', 'dropshipping', '铺货', '精品', '供应链', '收款',
  'pingpong', 'payoneer', 'prime', '促销', 'coupon', 'vine', '刷单', '封号', '申诉',
  'odr', '合规', '侵权', '认证', 'ce认证', 'rohs', '利润率', 'roi', '销量',
  '运营', '推广', '优化', '排名', '转化率', '点击率', 'cpc', 'cpm', 'roas',
  '头程', '尾程', '配送', '退货率', '妥投率', '柜型', '整柜', '拼柜',
  '旺季', '淡季', '黑五', '会员日', '秒杀', 'ld', 'bd', '变体', '合并',
  '商标', '专利', '知识产权', '包装法', '欧代', '英代', 'hs编码',
  '结汇', '汇率', '退税', '打款', 'moq', '交期', 'oem', 'odm',
]

function isCrossBorderQuestion(content: string): boolean {
  const lower = content.toLowerCase()
  return CROSS_BORDER_KEYWORDS.some(keyword => lower.includes(keyword.toLowerCase()))
}

export const useChatStore = defineStore('chat', () => {
  // 状态
  const messages = ref<ChatMessage[]>([])
  const currentMode = ref<ChatMode>('chat')
  const sessionId = ref<string>('')
  const loading = ref(false)
  const loadingText = ref('')

  // 计算属性
  const isChatMode = computed(() => currentMode.value === 'chat')
  const isAgentMode = computed(() => currentMode.value === 'agent')

  // 发送消息
  async function sendMessage(content: string) {
    if (!content.trim() || loading.value) return

    // 添加用户消息
    const userMessage: ChatMessage = {
      id: uuidv4(),
      role: 'user',
      content: content.trim(),
      timestamp: Date.now(),
    }
    messages.value.push(userMessage)

    loading.value = true

    // 根据问题内容设置加载提示文字
    if (isAgentMode.value) {
      loadingText.value = '正在调用工具...'
    } else if (isCrossBorderQuestion(content)) {
      loadingText.value = '正在检索知识库...'
    } else {
      loadingText.value = '思考中...'
    }

    try {
      let response

      if (isAgentMode.value) {
        // Agent 模式（保持原有工具决策逻辑）
        response = await agent({ question: content })
      } else {
        // 多轮对话模式（后端自动判断是否需要检索知识库）
        response = await chat({
          question: content,
          sessionId: sessionId.value || undefined,
        })
      }

      // 更新 sessionId
      if (response.sessionId) {
        sessionId.value = response.sessionId
      }

      // 添加 AI 消息
      const aiMessage: ChatMessage = {
        id: uuidv4(),
        role: 'assistant',
        content: response.answer,
        sources: response.sources,
        toolsCalled: response.toolsCalled,
        costMs: response.costMs,
        tokensUsed: response.tokensUsed,
        model: response.model,
        timestamp: Date.now(),
      }
      messages.value.push(aiMessage)
    } catch (error) {
      console.error('发送消息失败:', error)
    } finally {
      loading.value = false
      loadingText.value = ''
    }
  }

  // 新建对话
  async function newChat() {
    if (sessionId.value) {
      try {
        await deleteSession(sessionId.value)
      } catch (error) {
        console.error('删除会话失败:', error)
      }
    }
    sessionId.value = ''
    messages.value = []
  }

  // 清空消息（不删除会话）
  function clearMessages() {
    messages.value = []
  }

  // 切换模式
  function setMode(mode: ChatMode) {
    currentMode.value = mode
  }

  return {
    messages,
    currentMode,
    sessionId,
    loading,
    loadingText,
    isChatMode,
    isAgentMode,
    sendMessage,
    newChat,
    clearMessages,
    setMode,
  }
})
