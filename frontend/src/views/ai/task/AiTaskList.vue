<template>
  <div class="ai-task">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">AI 任务</span>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="任务类型">
          <el-select v-model="query.taskType" clearable placeholder="全部" style="width:160px">
            <el-option v-for="o in TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width:130px">
            <el-option v-for="o in STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" v-loading="loading" border>
        <el-table-column prop="taskNo" label="任务号" width="200" />
        <el-table-column label="类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="(TYPE_TAG_MAP[row.taskType] || 'info') as any">
              {{ TYPE_MAP[row.taskType] || row.taskType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="bizType" label="业务类型" width="120" align="center" />
        <el-table-column prop="bizId" label="业务ID" width="100" align="center" />
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="(STATUS_TAG_MAP[row.status] || 'info') as any" size="small">
              {{ STATUS_MAP[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="置信度" width="100" align="right">
          <template #default="{ row }">{{ row.confidence != null ? (Number(row.confidence) * 100).toFixed(1) + '%' : '-' }}</template>
        </el-table-column>
        <el-table-column label="已应用" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="(row.applyStatus === 'APPLIED' ? 'success' : row.applyStatus === 'REJECTED' ? 'danger' : 'info') as any" size="small">
              {{ APPLY_MAP[row.applyStatus] || row.applyStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" v-if="row.status === 'COMPLETED' && !row.reviewed" @click="onReview(row, true)">应用</el-button>
            <el-button text type="danger" v-if="row.status === 'COMPLETED' && !row.reviewed" @click="onReview(row, false)">拒绝</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="page-pagination">
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { pageAiTask, reviewAiTask } from '@/api/modules/ai'
import { useAuthStore } from '@/stores/auth.store'

const TYPE_OPTIONS = [
  { value: 'OCR', label: 'OCR识别' },
  { value: 'MATCH', label: '智能匹配' },
  { value: 'ANOMALY', label: '异常检测' },
  { value: 'EMBEDDING', label: '文本嵌入' },
]
const TYPE_MAP: Record<string, string> = Object.fromEntries(TYPE_OPTIONS.map((o) => [o.value, o.label]))
const TYPE_TAG_MAP: Record<string, string> = { OCR: 'primary', MATCH: 'success', ANOMALY: 'warning', EMBEDDING: 'info' }

const STATUS_OPTIONS = [
  { value: 'PENDING', label: '待处理' },
  { value: 'PROCESSING', label: '处理中' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'FAILED', label: '失败' },
]
const STATUS_MAP: Record<string, string> = Object.fromEntries(STATUS_OPTIONS.map((o) => [o.value, o.label]))
const STATUS_TAG_MAP: Record<string, string> = { PENDING: 'info', PROCESSING: 'warning', COMPLETED: 'success', FAILED: 'danger' }

const APPLY_MAP: Record<string, string> = { APPLIED: '已应用', REJECTED: '已拒绝', NOT_APPLIED: '未应用' }

const query = reactive({ taskType: '', status: '', current: 1, size: 20 })
const list = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const authStore = useAuthStore()

const fetchData = async () => {
  loading.value = true
  try {
    const res: any = await pageAiTask(query)
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

const onReview = async (row: any, approved: boolean) => {
  const reviewerId = (authStore.userInfo as any)?.id || 1
  await reviewAiTask(row.id, reviewerId, approved)
  ElMessage.success(approved ? '已应用' : '已拒绝')
  fetchData()
}

onMounted(fetchData)
</script>
