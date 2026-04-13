<template>
  <div class="chat-view">
    <!-- 顶栏控制区 -->
    <div class="chat-header">
      <h2 class="header-title">跨境电商 AI 助手</h2>
      <div class="header-controls">
        <!-- 模式切换 -->
        <el-radio-group v-model="currentMode" size="small" @change="handleModeChange">
          <el-radio-button label="chat">多轮对话</el-radio-button>
          <el-radio-button label="agent">Agent模式</el-radio-button>
        </el-radio-group>

        <!-- 新建对话 -->
        <el-button
          type="primary"
          size="small"
          :icon="Plus"
          @click="handleNewChat"
        >
          新建对话
        </el-button>
      </div>
    </div>

    <!-- 消息列表区 -->
    <div ref="messageContainer" class="message-list">
      <div v-if="messages.length === 0" class="empty-state">
        <div class="empty-icon">
          <MessageSquare :size="48" />
        </div>
        <p class="empty-text">开始与 AI 助手对话</p>
        <p class="empty-hint">输入跨境电商相关问题，获取智能回答</p>
      </div>

      <div
        v-for="message in messages"
        :key="message.id"
        :class="['message-item', message.role]"
      >
        <!-- 头像 -->
        <div class="message-avatar">
          <el-avatar
            :size="36"
            :icon="message.role === 'user' ? User : Bot"
            :class="message.role"
          />
        </div>

        <!-- 内容区 -->
        <div class="message-content">
          <div class="message-bubble">
            <div class="message-text">{{ message.content }}</div>

            <!-- 来源引用（RAG/知识库模式） -->
            <div v-if="message.sources && message.sources.length > 0" class="sources-section">
              <el-divider content-position="left">
                <span class="sources-title">
                  <FileText :size="12" />
                  参考来源
                </span>
              </el-divider>
              <el-collapse>
                <el-collapse-item
                  v-for="(source, index) in message.sources"
                  :key="source.documentId"
                  :title="`${index + 1}. ${source.documentName}`"
                >
                  <p class="source-content">{{ source.content }}</p>
                  <p class="source-score">相关度: {{ (source.score * 100).toFixed(1) }}%</p>
                </el-collapse-item>
              </el-collapse>
            </div>

            <!-- 工具调用（Agent模式） -->
            <div v-if="message.toolsCalled && message.toolsCalled.length > 0" class="tools-section">
              <div class="tools-label">
                <Wrench :size="12" />
                调用工具
              </div>
              <div class="tools-list">
                <el-tag
                  v-for="tool in message.toolsCalled"
                  :key="tool"
                  size="small"
                  type="warning"
                  effect="plain"
                >
                  {{ tool }}
                </el-tag>
              </div>
            </div>
          </div>

          <!-- 元信息 -->
          <div v-if="message.role === 'assistant'" class="message-meta">
            <span v-if="message.costMs" class="meta-item">
              <Clock :size="12" />
              {{ message.costMs }}ms
            </span>
            <span v-if="message.tokensUsed" class="meta-item">
              <Zap :size="12" />
              {{ message.tokensUsed }} tokens
            </span>
            <span v-if="message.model" class="meta-item model">
              {{ message.model }}
            </span>
          </div>
        </div>
      </div>

      <!-- 加载动画 + 动态提示文字 -->
      <div v-if="loading" class="message-item assistant loading">
        <div class="message-avatar">
          <el-avatar :size="36" :icon="Bot" class="assistant" />
        </div>
        <div class="message-content">
          <div class="loading-status">
            <div class="typing-indicator">
              <span></span>
              <span></span>
              <span></span>
            </div>
            <div v-if="loadingText" class="loading-text">{{ loadingText }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部输入区 -->
    <div class="input-area">
      <div class="input-wrapper">
        <el-input
          v-model="inputMessage"
          type="textarea"
          :rows="1"
          :autosize="{ minRows: 1, maxRows: 5 }"
          placeholder="输入问题，按 Enter 发送，Shift+Enter 换行..."
          @keydown.enter.prevent="handleSend"
        />
        <el-button
          type="primary"
          :icon="Send"
          :loading="loading"
          :disabled="!inputMessage.trim()"
          class="send-btn"
          @click="handleSend"
        />
      </div>
      <div v-if="sessionId" class="session-info">
        会话 ID: {{ sessionId }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useChatStore } from '@/stores/chat'
import type { ChatMode } from '@/types'
import {
  Plus,
  User,
  Bot,
  MessageSquare,
  Send,
  FileText,
  Wrench,
  Clock,
  Zap,
} from 'lucide-vue-next'

const chatStore = useChatStore()
const { messages, loading, sessionId, loadingText, isChatMode } = storeToRefs(chatStore)

const inputMessage = ref('')
const messageContainer = ref<HTMLElement>()
const currentMode = computed({
  get: () => chatStore.currentMode,
  set: (val: ChatMode) => chatStore.setMode(val),
})

// 发送消息
async function handleSend() {
  if (!inputMessage.value.trim() || loading.value) return
  const content = inputMessage.value
  inputMessage.value = ''
  await chatStore.sendMessage(content)
  scrollToBottom()
}

// 新建对话
async function handleNewChat() {
  await chatStore.newChat()
}

// 模式切换
function handleModeChange(mode: ChatMode) {
  chatStore.setMode(mode)
}

// 滚动到底部
function scrollToBottom() {
  nextTick(() => {
    if (messageContainer.value) {
      messageContainer.value.scrollTop = messageContainer.value.scrollHeight
    }
  })
}

// 监听消息变化，自动滚动
watch(messages, scrollToBottom, { deep: true })
</script>

<style scoped>
.chat-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f5f7fa;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}

.header-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}

.header-controls {
  display: flex;
  align-items: center;
  gap: 16px;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  scroll-behavior: smooth;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #909399;
}

.empty-icon {
  margin-bottom: 16px;
  color: #c0c4cc;
}

.empty-text {
  font-size: 16px;
  font-weight: 500;
  color: #606266;
  margin-bottom: 8px;
}

.empty-hint {
  font-size: 14px;
  color: #909399;
}

.message-item {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
}

.message-item.user {
  flex-direction: row-reverse;
}

.message-avatar .el-avatar {
  background: #409eff;
}

.message-avatar .el-avatar.assistant {
  background: #67c23a;
}

.message-content {
  max-width: 70%;
}

.message-item.user .message-content {
  align-items: flex-end;
  display: flex;
  flex-direction: column;
}

.message-bubble {
  padding: 12px 16px;
  border-radius: 12px;
  word-break: break-word;
}

.message-item.user .message-bubble {
  background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
  color: #fff;
}

.message-item.assistant .message-bubble {
  background: #fff;
  color: #303133;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.message-text {
  line-height: 1.6;
  font-size: 14px;
}

.sources-section {
  margin-top: 12px;
  padding-top: 8px;
  border-top: 1px dashed #e4e7ed;
}

.sources-title {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #909399;
}

.source-content {
  font-size: 13px;
  color: #606266;
  line-height: 1.5;
  margin: 8px 0;
}

.source-score {
  font-size: 12px;
  color: #409eff;
}

.tools-section {
  margin-top: 12px;
  padding-top: 8px;
  border-top: 1px dashed #e4e7ed;
}

.tools-label {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}

.tools-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.message-meta {
  display: flex;
  gap: 12px;
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.meta-item.model {
  background: #f0f2f5;
  padding: 2px 8px;
  border-radius: 4px;
}

.loading-status {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 16px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.typing-indicator span {
  width: 8px;
  height: 8px;
  background: #c0c4cc;
  border-radius: 50%;
  animation: typing 1.4s infinite ease-in-out both;
}

.typing-indicator span:nth-child(1) {
  animation-delay: -0.32s;
}

.typing-indicator span:nth-child(2) {
  animation-delay: -0.16s;
}

.loading-text {
  font-size: 12px;
  color: #909399;
  padding-left: 8px;
  animation: pulse 1.5s infinite ease-in-out;
}

@keyframes typing {
  0%, 80%, 100% {
    transform: scale(0);
  }
  40% {
    transform: scale(1);
  }
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}

.input-area {
  padding: 16px 24px;
  background: #fff;
  border-top: 1px solid #e4e7ed;
}

.input-wrapper {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.input-wrapper .el-textarea {
  flex: 1;
}

.send-btn {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  padding: 0;
}

.session-info {
  margin-top: 8px;
  font-size: 12px;
  color: #c0c4cc;
  text-align: center;
}
</style>
