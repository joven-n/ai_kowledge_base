<template>
  <div class="system-view">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2 class="page-title">系统监控</h2>
      <p class="page-subtitle">查看系统运行状态和 API 接口信息</p>
    </div>

    <!-- 健康状态卡片 -->
    <div class="health-section">
      <h3 class="section-title">
        <Activity :size="18" />
        系统健康状态
      </h3>
      <el-row :gutter="16">
        <el-col :span="8" :xs="24" :sm="12" :md="8">
          <el-card class="health-card" shadow="hover">
            <div class="health-item">
              <div class="health-icon" :class="health.status">
                <CheckCircle v-if="health.status === 'UP'" :size="24" />
                <XCircle v-else :size="24" />
              </div>
              <div class="health-info">
                <div class="health-label">服务状态</div>
                <div class="health-value" :class="health.status">
                  {{ health.status === 'UP' ? '运行中' : '异常' }}
                </div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="8" :xs="24" :sm="12" :md="8">
          <el-card class="health-card" shadow="hover">
            <div class="health-item">
              <div class="health-icon info">
                <Info :size="24" />
              </div>
              <div class="health-info">
                <div class="health-label">系统版本</div>
                <div class="health-value">{{ health.version || '-' }}</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="8" :xs="24" :sm="12" :md="8">
          <el-card class="health-card" shadow="hover">
            <div class="health-item">
              <div class="health-icon info">
                <Clock :size="24" />
              </div>
              <div class="health-info">
                <div class="health-label">运行时间</div>
                <div class="health-value">{{ health.uptime || '-' }}</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="8" :xs="24" :sm="12" :md="8">
          <el-card class="health-card" shadow="hover">
            <div class="health-item">
              <div class="health-icon warning">
                <Database :size="24" />
              </div>
              <div class="health-info">
                <div class="health-label">内存使用</div>
                <div class="health-value">
                  <template v-if="health.memory">
                    {{ formatBytes(health.memory.used) }} / {{ formatBytes(health.memory.total) }}
                    <el-progress
                      :percentage="memoryPercentage"
                      :color="memoryColor"
                      :stroke-width="6"
                      class="memory-progress"
                    />
                  </template>
                  <span v-else>-</span>
                </div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="8" :xs="24" :sm="12" :md="8">
          <el-card class="health-card" shadow="hover">
            <div class="health-item">
              <div class="health-icon info">
                <Cpu :size="24" />
              </div>
              <div class="health-info">
                <div class="health-label">活跃线程</div>
                <div class="health-value">{{ health.threads || '-' }}</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="8" :xs="24" :sm="12" :md="8">
          <el-card class="health-card" shadow="hover">
            <div class="health-item">
              <div class="health-icon info">
                <Server :size="24" />
              </div>
              <div class="health-info">
                <div class="health-label">处理器数</div>
                <div class="health-value">{{ health.processors || '-' }}</div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- API 接口列表 -->
    <div class="api-section">
      <h3 class="section-title">
        <List :size="18" />
        API 接口清单
      </h3>
      <el-card shadow="never">
        <el-table :data="apis" stripe style="width: 100%">
          <el-table-column label="请求方法" width="100">
            <template #default="{ row }">
              <el-tag
                :type="getMethodType(row.method)"
                size="small"
                effect="plain"
              >
                {{ row.method }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="path" label="接口路径" min-width="250">
            <template #default="{ row }">
              <code class="api-path">{{ row.path }}</code>
            </template>
          </el-table-column>
          <el-table-column prop="description" label="接口说明" min-width="300" />
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  Activity,
  CheckCircle,
  XCircle,
  Info,
  Clock,
  Database,
  Cpu,
  Server,
  List,
} from 'lucide-vue-next'
import { getHealth, getApis } from '@/api/system'
import type { SystemHealth, ApiInfo } from '@/types'

// 数据
const health = ref<Partial<SystemHealth>>({})
const apis = ref<ApiInfo[]>([])

// 计算内存百分比
const memoryPercentage = computed(() => {
  if (!health.value.memory?.used || !health.value.memory?.total || health.value.memory.total === 0) return 0
  return Math.round((health.value.memory.used / health.value.memory.total) * 100)
})

// 内存进度条颜色
const memoryColor = computed(() => {
  const pct = memoryPercentage.value
  if (pct < 60) return '#67c23a'
  if (pct < 80) return '#e6a23c'
  return '#f56c6c'
})

// 格式化字节
function formatBytes(bytes: number | undefined | null): string {
  if (!bytes || bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

// 获取请求方法对应的标签类型
function getMethodType(method: string): string {
  const map: Record<string, string> = {
    GET: 'success',
    POST: 'primary',
    PUT: 'warning',
    DELETE: 'danger',
    PATCH: 'info',
  }
  return map[method] || 'info'
}

// 加载数据
async function loadData() {
  try {
    const [healthData, apiData] = await Promise.all([
      getHealth(),
      getApis(),
    ])
    health.value = healthData
    apis.value = apiData
  } catch (error) {
    console.error('加载系统数据失败:', error)
  }
}

onMounted(loadData)
</script>

<style scoped>
.system-view {
  padding: 24px;
  height: 100%;
  overflow-y: auto;
}

.page-header {
  margin-bottom: 24px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 8px;
}

.page-subtitle {
  font-size: 14px;
  color: #909399;
  margin: 0;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 16px;
}

.health-section {
  margin-bottom: 32px;
}

.health-card {
  margin-bottom: 16px;
  border-radius: 12px;
}

.health-item {
  display: flex;
  align-items: center;
  gap: 16px;
}

.health-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.health-icon.UP {
  background: rgba(103, 194, 58, 0.1);
  color: #67c23a;
}

.health-icon.DOWN {
  background: rgba(245, 108, 108, 0.1);
  color: #f56c6c;
}

.health-icon.info {
  background: rgba(64, 158, 255, 0.1);
  color: #409eff;
}

.health-icon.warning {
  background: rgba(230, 162, 60, 0.1);
  color: #e6a23c;
}

.health-info {
  flex: 1;
}

.health-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.health-value {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.health-value.UP {
  color: #67c23a;
}

.health-value.DOWN {
  color: #f56c6c;
}

.memory-progress {
  margin-top: 8px;
}

.api-section {
  .api-path {
    background: #f5f7fa;
    padding: 4px 8px;
    border-radius: 4px;
    font-family: 'Courier New', monospace;
    font-size: 13px;
    color: #606266;
  }
}
</style>
