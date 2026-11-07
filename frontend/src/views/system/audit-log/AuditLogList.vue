<template>
  <div class="audit-log-list">
    <el-card shadow="never">
      <div class="page-header">
        <el-form :model="query" inline>
          <el-form-item label="操作模块">
            <!-- SPEC-P152：取值对齐后端真实 module（@Log 的 system、
                 登录审计 AUTH、服务端异常 SYSTEM、前端上报 FRONTEND、
                 @Auditable/实体派生的模块名）-->
            <el-select v-model="query.module" placeholder="全部" clearable @change="search" style="width: 160px">
              <el-option label="系统管理" value="system" />
              <el-option label="认证登录" value="AUTH" />
              <el-option label="服务端异常" value="SYSTEM" />
              <el-option label="前端错误" value="FRONTEND" />
              <el-option label="期初建账" value="SUBJECT_BALANCE" />
              <el-option label="凭证" value="voucher" />
              <el-option label="单据" value="business" />
            </el-select>
          </el-form-item>
          <el-form-item label="操作结果">
            <el-select v-model="query.status" placeholder="全部" clearable @change="search" style="width: 120px">
              <el-option label="成功" value="success" />
              <!-- value=failure：服务端对 fail/failure 两种口径都认（pageLog） -->
              <el-option label="失败" value="failure" />
              <el-option label="警告" value="warn" />
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

      <el-table :data="list" v-loading="loading" border stripe style="width: 100%" data-testid="audit-log-table">
        <template #empty>
          <el-empty v-if="!query.startDate && !query.endDate" description="请先选择日期范围（开始/结束日期）查询审计日志" />
        </template>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="操作人" width="120" />
        <el-table-column prop="module" label="模块" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ row.module }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operation" label="操作" min-width="150" show-overflow-tooltip />
        <el-table-column label="对象" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.entityType">{{ row.entityType }}<template v-if="row.entityNo"> · {{ row.entityNo }}</template><template v-else-if="row.entityId"> · #{{ row.entityId }}</template></span>
            <span v-else style="color:#c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="ipAddress" label="IP 地址" width="140" />
        <el-table-column prop="executionTimeMs" label="耗时" width="80" align="right">
          <template #default="{ row }">{{ row.executionTimeMs != null ? row.executionTimeMs + 'ms' : '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="结果" width="80">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="操作时间" width="170" />
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" @click="openDetail(row as AuditLogVO)">详情</el-button>
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

    <el-dialog v-model="detailVisible" title="操作详情" width="720" destroy-on-close data-testid="audit-detail-dialog">
      <el-descriptions :column="2" border v-if="detail">
        <el-descriptions-item label="ID" width="100">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ detail.username }}</el-descriptions-item>
        <el-descriptions-item label="模块">{{ detail.module }}</el-descriptions-item>
        <el-descriptions-item label="操作结果">
          <el-tag :type="statusTagType(detail.status)" size="small">{{ statusLabel(detail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="操作" :span="2">{{ detail.operation }}</el-descriptions-item>
        <el-descriptions-item label="目标方法" :span="2">{{ detail.method || '-' }}</el-descriptions-item>
        <el-descriptions-item label="对象类型">{{ detail.entityType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="对象">{{ detail.entityNo || detail.entityId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="IP 地址">{{ detail.ipAddress }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ detail.executionTimeMs != null ? detail.executionTimeMs + 'ms' : '-' }}</el-descriptions-item>
        <el-descriptions-item label="User-Agent" :span="2">
          <div class="ua-text">{{ detail.userAgent }}</div>
        </el-descriptions-item>
        <el-descriptions-item v-if="detail.responseResult" label="响应/错误" :span="2">
          <div class="error-text">{{ detail.responseResult }}</div>
        </el-descriptions-item>
        <el-descriptions-item label="请求参数" :span="2" v-if="detail.requestParams">
          <pre class="json-block">{{ formatJson(detail.requestParams) }}</pre>
        </el-descriptions-item>
        <!-- 错误类审计的核心：SYSTEM 记堆栈、FRONTEND 记前端堆栈、业务变更记变更后快照 -->
        <el-descriptions-item v-if="detail.afterData" :label="afterDataLabel" :span="2">
          <pre class="json-block stack-block">{{ formatJson(detail.afterData) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item v-if="detail.beforeData" label="变更前快照" :span="2">
          <pre class="json-block">{{ formatJson(detail.beforeData) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="操作时间">{{ detail.createdAt }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { getAuditLogPage, getAuditLog, type AuditLogVO } from '@/api/modules/system'

const loading = ref(false)
const list = ref<AuditLogVO[]>([])
const total = ref(0)

const query = reactive({ page: 1, size: 10, module: '', status: '', startDate: '', endDate: '' })

const detailVisible = ref(false)
const detail = ref<AuditLogVO | null>(null)

// SPEC-P152：status 真实值域来自切面/监听器（success / fail / warn），
// failure 为历史页端取值 —— 展示层三种都认，不出现空白标签
function statusLabel(status?: string): string {
  if (status === 'success') return '成功'
  if (status === 'warn') return '警告'
  if (status === 'fail' || status === 'failure') return '失败'
  return status || '-'
}
function statusTagType(status?: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'success') return 'success'
  if (status === 'warn') return 'warning'
  if (status === 'fail' || status === 'failure') return 'danger'
  return 'info'
}
// after_data 的语义随模块变化：SYSTEM 存服务端堆栈、FRONTEND 存前端堆栈、
// 业务模块存变更后快照 —— 标签必须跟着变，否则「变更后快照」里显示堆栈会误导排查
const afterDataLabel = computed(() => {
  const m = detail.value?.module
  if (m === 'SYSTEM') return '异常堆栈'
  if (m === 'FRONTEND') return '前端堆栈'
  return '变更后快照'
})

async function fetchData() {
  // R1：审计日志为历史归档视图，必须带日期条件才允许查询
  if (!query.startDate && !query.endDate) {
    list.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const params: Record<string, any> = { page: query.page, size: query.size }
    if (query.module) params.module = query.module
    if (query.status) params.status = query.status
    if (query.startDate) params.startDate = query.startDate
    if (query.endDate) params.endDate = query.endDate
    const res = await getAuditLogPage(params as any)
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

onMounted(() => {
  // 不自动拉全量，带日期条件后由查询按钮触发
})
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

/* 堆栈展示：保留换行、可横向滚动（SPEC-P152 错误留痕的主视图） */
.stack-block {
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 320px;
  color: #c7254e;
}
</style>
