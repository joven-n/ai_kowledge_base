<template>
  <div class="main-layout">
    <!-- 侧边栏 -->
    <aside class="sidebar">
      <div class="logo">
        <div class="logo-title">AI KB</div>
        <div class="logo-subtitle">Knowledge Base</div>
      </div>
      <el-menu
        :default-active="activeMenu"
        class="sidebar-menu"
        router
        background-color="transparent"
        text-color="#b0b3c7"
        active-text-color="#409EFF"
      >
        <el-menu-item index="/chat">
          <el-icon><MessageCircle /></el-icon>
          <span>AI对话</span>
        </el-menu-item>
        <el-menu-item index="/knowledge">
          <el-icon><Folder /></el-icon>
          <span>知识库管理</span>
        </el-menu-item>
        <el-menu-item index="/system">
          <el-icon><Activity /></el-icon>
          <span>系统监控</span>
        </el-menu-item>
      </el-menu>
    </aside>

    <!-- 主内容区 -->
    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { MessageCircle, Folder, Activity } from 'lucide-vue-next'

const route = useRoute()
const activeMenu = computed(() => route.path)
</script>

<style scoped>
.main-layout {
  display: flex;
  width: 100%;
  height: 100vh;
}

.sidebar {
  width: 220px;
  flex-shrink: 0;
  background: linear-gradient(180deg, #1D1E2C 0%, #2A2D3E 100%);
  display: flex;
  flex-direction: column;
}

.logo {
  padding: 24px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.logo-title {
  font-size: 24px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 1px;
}

.logo-subtitle {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.sidebar-menu {
  flex: 1;
  border-right: none;
  padding-top: 16px;
}

.sidebar-menu :deep(.el-menu-item) {
  height: 50px;
  line-height: 50px;
  margin: 4px 12px;
  border-radius: 8px;
  transition: all 0.3s;
}

.sidebar-menu :deep(.el-menu-item:hover) {
  background: rgba(64, 158, 255, 0.1);
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background: rgba(64, 158, 255, 0.15);
}

.sidebar-menu :deep(.el-icon) {
  margin-right: 12px;
  font-size: 18px;
}

.main-content {
  flex: 1;
  overflow: hidden;
  background: #fff;
}
</style>
