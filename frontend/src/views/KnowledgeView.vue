<template>
  <div class="knowledge-view">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2 class="page-title">知识库管理</h2>
      <p class="page-subtitle">管理您的知识文档，支持文本和文件上传</p>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-section">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-card class="stat-card" shadow="hover">
            <div class="stat-content">
              <div class="stat-icon document">
                <FileText :size="32" />
              </div>
              <div class="stat-info">
                <div class="stat-value">{{ stats.documentCount }}</div>
                <div class="stat-label">文档总数</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card class="stat-card" shadow="hover">
            <div class="stat-content">
              <div class="stat-icon chunk">
                <Layers :size="32" />
              </div>
              <div class="stat-info">
                <div class="stat-value">{{ stats.chunkCount }}</div>
                <div class="stat-label">切片总数</div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 操作工具栏 -->
    <div class="toolbar">
      <el-button type="primary" :icon="Upload" @click="showUploadDialog = true">
        上传文档
      </el-button>
      <el-button type="success" :icon="Plus" @click="showTextDialog = true">
        添加文本
      </el-button>
      <el-button :icon="RefreshCw" @click="loadData">
        刷新
      </el-button>
    </div>

    <!-- 文档列表 -->
    <div class="document-list">
      <el-card shadow="never">
        <el-table
          v-loading="loading"
          :data="documents"
          stripe
          style="width: 100%"
        >
          <el-table-column prop="name" label="文档名称" min-width="200">
            <template #default="{ row }">
              <div class="doc-name">
                <FileText :size="16" class="doc-icon" />
                <span>{{ row.name }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="source" label="来源" min-width="150" />
          <el-table-column prop="createdAt" label="上传时间" width="180">
            <template #default="{ row }">
              {{ formatDate(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button
                type="danger"
                size="small"
                :icon="Trash2"
                @click="handleDelete(row)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-if="!loading && documents.length === 0" description="暂无文档，请上传" />
      </el-card>
    </div>

    <!-- 上传文档对话框 -->
    <el-dialog
      v-model="showUploadDialog"
      title="上传文档"
      width="500px"
      destroy-on-close
    >
      <el-upload
        drag
        action=""
        :auto-upload="false"
        :on-change="handleFileChange"
        :on-remove="handleFileRemove"
        :limit="1"
        accept=".txt,.pdf,.md"
        class="upload-area"
      >
        <el-icon class="el-icon--upload"><Upload :size="48" /></el-icon>
        <div class="el-upload__text">
          将文件拖到此处，或 <em>点击上传</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">
            支持 .txt、.pdf 和 .md 格式文件，大小不超过 50MB
          </div>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="showUploadDialog = false">取消</el-button>
        <el-button
          type="primary"
          :loading="uploadLoading"
          :disabled="!selectedFile"
          @click="handleUpload"
        >
          确认上传
        </el-button>
      </template>
    </el-dialog>

    <!-- 添加文本对话框 -->
    <el-dialog
      v-model="showTextDialog"
      title="添加文本"
      width="600px"
      destroy-on-close
    >
      <el-form
        ref="textFormRef"
        :model="textForm"
        :rules="textRules"
        label-width="80px"
      >
        <el-form-item label="来源" prop="source">
          <el-input
            v-model="textForm.source"
            placeholder="请输入来源标识，如：manual-input"
          />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input
            v-model="textForm.content"
            type="textarea"
            :rows="8"
            placeholder="请输入要添加的文本内容..."
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showTextDialog = false">取消</el-button>
        <el-button
          type="primary"
          :loading="textLoading"
          @click="handleAddText"
        >
          确认添加
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, UploadFile } from 'element-plus'
import {
  FileText,
  Layers,
  Upload,
  Plus,
  RefreshCw,
  Trash2,
} from 'lucide-vue-next'
import { listDocuments, getStats, uploadFile, uploadText, deleteDocument } from '@/api/knowledge'
import type { KnowledgeDocument, KnowledgeStats } from '@/types'

// 数据
const documents = ref<KnowledgeDocument[]>([])
const stats = ref<KnowledgeStats>({ documentCount: 0, chunkCount: 0 })
const loading = ref(false)

// 上传对话框
const showUploadDialog = ref(false)
const selectedFile = ref<File | null>(null)
const uploadLoading = ref(false)

// 文本对话框
const showTextDialog = ref(false)
const textFormRef = ref<FormInstance>()
const textLoading = ref(false)
const textForm = ref({
  source: '',
  content: '',
})
const textRules = {
  source: [{ required: true, message: '请输入来源', trigger: 'blur' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }],
}

// 加载数据
async function loadData() {
  loading.value = true
  try {
    const [docs, stat] = await Promise.all([
      listDocuments(),
      getStats(),
    ])
    documents.value = docs
    stats.value = stat
  } catch (error) {
    console.error('加载数据失败:', error)
  } finally {
    loading.value = false
  }
}

// 格式化日期
function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

// 文件选择
function handleFileChange(file: UploadFile) {
  if (file.raw) {
    selectedFile.value = file.raw
  }
}

// 文件移除
function handleFileRemove() {
  selectedFile.value = null
}

// 上传文件
async function handleUpload() {
  if (!selectedFile.value) return

  uploadLoading.value = true
  try {
    await uploadFile(selectedFile.value)
    ElMessage.success('上传成功，已完成向量化处理')
    showUploadDialog.value = false
    selectedFile.value = null
    loadData()
  } catch (error: any) {
    console.error('上传失败:', error)
    ElMessage.error(error.message || '上传失败，请检查网络或API配置')
  } finally {
    uploadLoading.value = false
  }
}

// 添加文本
async function handleAddText() {
  if (!textFormRef.value) return

  await textFormRef.value.validate(async (valid) => {
    if (!valid) return

    textLoading.value = true
    try {
      await uploadText(textForm.value.content, textForm.value.source)
      ElMessage.success('添加成功，已完成向量化处理')
      showTextDialog.value = false
      textForm.value = { source: '', content: '' }
      loadData()
    } catch (error: any) {
      console.error('添加失败:', error)
      ElMessage.error(error.message || '添加失败，请检查网络或API配置')
    } finally {
      textLoading.value = false
    }
  })
}

// 删除文档
async function handleDelete(doc: KnowledgeDocument) {
  try {
    await ElMessageBox.confirm(
      `确定要删除文档 "${doc.name}" 吗？`,
      '确认删除',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )

    await deleteDocument(doc.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
    }
  }
}

onMounted(loadData)
</script>

<style scoped>
.knowledge-view {
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

.stats-section {
  margin-bottom: 24px;
}

.stat-card {
  border-radius: 12px;
}

.stat-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  width: 64px;
  height: 64px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.stat-icon.document {
  background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
}

.stat-icon.chunk {
  background: linear-gradient(135deg, #67c23a 0%, #85ce61 100%);
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: #303133;
  line-height: 1;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 4px;
}

.toolbar {
  margin-bottom: 24px;
  display: flex;
  gap: 12px;
}

.document-list {
  .doc-name {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .doc-icon {
    color: #409eff;
  }
}

.upload-area {
  :deep(.el-upload-dragger) {
    width: 100%;
    height: 200px;
  }
}
</style>
