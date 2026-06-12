<template>
  <div class="audit-log-list">
    <el-card shadow="never">
      <div class="page-header">
        <el-form :model="query" inline>
          <el-form-item label="操作模块">
            <el-select v-model="query.module" placeholder="全部" clearable @change="search" style="width: 140px">
              <el-option label="系统管理" value="system" />
              <el-option label="基础数据" value="basis" />
              <el-option label="财务核心" value="finance" />
              <el-option label="固定资产" value="asset" />
              <el-option label="往来管理" value="arap" />
              <el-option label="税务管理" value="tax" />
              <el-option label="预算管理" value="budget" />
              <el-option label="报表中心" value="report" />
              <el-option label="AI 中心" value="ai" />
            </el-select>
          </el-form-item>
          <el-form-item label="操作结果">
            <el-select v-model="query.status" placeholder="全部" clearable @change="search" style="width: 120px">
              <el-option label="成功" value="success" />
              <el-option label="失败" value="failure" />
            </el-select>
          </el-form-item>
          <el-form-item label="开始日期">
            <el-date-picker v-model="query.startDate" type="date" placeholder="开始日期" value-format="YYYY-MM-DD" @change="search" />
          </el-form-item>
          <el-form-item label="结束日期">
            <el-date-picker v-model="query.endDate" type="date" placeholder="结束日期" value-format="YYYY-MM-DD" @change="search" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="search">查询</el-button>
            <el-button @click="reset">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-table :data="list" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="操作人" width="120" />
        <el-table-column prop="module" label="模块" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.module }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="action" label="操作" min-width="160" show-overflow-tooltip />
        <el-table-column prop="resourceType" label="资源类型" width="100" />
        <el-table-column prop="resourceId" label="资源ID" width="80" />
        <el-table-column prop="ip" label="IP 地址" width="140" />
        <el-table-column prop="status" label="结果" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'success' ? 'success' : 'danger'" size="small">
              {{ row.status === 'success' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="操作时间" width="170" />
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="page-pagination">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="fetchData"
        />
      </div>
    </el-card>

    <el-dialog v-model="detailVisible" title="操作详情" width="640" destroy-on-close>
      <el-descriptions :column="2" border v-if="detail">
        <el-descriptions-item label="ID" width="100">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ detail.username }}</el-descriptions-item>
        <el-descriptions-item label="模块">{{ detail.module }}</el-descriptions-item>
        <el-descriptions-item label="操作结果">
          <el-tag :type="detail.status === 'success' ? 'success' : 'danger'" size="small">
            {{ detail.status === 'success' ? '成功' : '失败' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="操作" :span="2">{{ detail.action }}</el-descriptions-item>
        <el-descriptions-item label="资源类型">{{ detail.resourceType }}</el-descriptions-item>
        <el-descriptions-item label="资源ID">{{ detail.resourceId }}</el-descriptions-item>
        <el-descriptions-item label="IP 地址">{{ detail.ip }}</el-descriptions-item>
        <el-descriptions-item label="User-Agent" :span="2">
          <div class="ua-text">{{ detail.userAgent }}</div>
        </el-descriptions-item>
        <el-descriptions-item v-if="detail.errorMsg" label="错误信息" :span="2">
          <div class="error-text">{{ detail.errorMsg }}</div>
        </el-descriptions-item>
        <el-descriptions-item label="请求参数" :span="2" v-if="detail.requestParams">
          <pre class="json-block">{{ formatJson(detail.requestParams) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="操作时间">{{ detail.createdAt }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { getAuditLogPage, getAuditLog, type AuditLogVO } from '@/api/modules/system'

const loading = ref(false)
const list = ref<AuditLogVO[]>([])
const total = ref(0)

const query = reactive({ page: 1, size: 10, module: '', status: '', startDate: '', endDate: '' })

const detailVisible = ref(false)
const detail = ref<AuditLogVO | null>(null)

async function fetchData() {
  loading.value = true
  try {
    const params: Record<string, any> = { page: query.page, size: query.size }
    if (query.module) params.module = query.module
    if (query.status) params.status = query.status
    if (query.startDate) params.startDate = query.startDate
    if (query.endDate) params.endDate = query.endDate
    const res = await getAuditLogPage(params)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  fetchData()
}

function reset() {
  query.module = ''
  query.status = ''
  query.startDate = ''
  query.endDate = ''
  search()
}

async function openDetail(row: AuditLogVO) {
  try {
    detail.value = await getAuditLog(row.id)
    detailVisible.value = true
  } catch { /* ignore */ }
}

function formatJson(obj: any): string {
  try {
    const val = typeof obj === 'string' ? JSON.parse(obj) : obj
    return JSON.stringify(val, null, 2)
  } catch {
    return String(obj)
  }
}

onMounted(fetchData)
</script>

<style scoped lang="scss">
.page-header {
  margin-bottom: 16px;
}

.page-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.ua-text {
  word-break: break-all;
  font-size: 12px;
  color: #666;
}

.error-text {
  color: #f56c6c;
  font-size: 12px;
}

.json-block {
  background: #f5f7fa;
  padding: 8px 12px;
  border-radius: 4px;
  font-size: 12px;
  max-height: 200px;
  overflow: auto;
  margin: 0;
}
</style>
