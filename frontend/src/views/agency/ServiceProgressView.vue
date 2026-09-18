<template>
  <div class="agency-progress">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">服务进度与工作量</span>
      </div>

      <el-tabs v-model="activeTab">
        <!-- ── 服务进度跟踪 ── -->
        <el-tab-pane label="服务进度" name="progress">
          <el-form :model="pQuery" inline class="filter-form">
            <el-form-item label="期间">
              <el-date-picker
                v-model="pQuery.period"
                type="month"
                value-format="YYYYMM"
                :clearable="true"
                placeholder="全部期间"
                style="width:150px"
              />
            </el-form-item>
            <el-form-item label="节点">
              <el-select v-model="pQuery.stage" clearable placeholder="全部" style="width:130px">
                <el-option label="取票 INTAKE" value="INTAKE" />
                <el-option label="记账 BOOKING" value="BOOKING" />
                <el-option label="审核结账 REVIEW" value="REVIEW" />
                <el-option label="报税 FILING" value="FILING" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="pQuery.status" clearable placeholder="全部" style="width:130px">
                <el-option label="待处理" value="PENDING" />
                <el-option label="进行中" value="IN_PROGRESS" />
                <el-option label="已完成" value="DONE" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :disabled="pLoading" @click="fetchProgress">查询</el-button>
              <el-button :disabled="pLoading" @click="fetchOvertime">超期预警</el-button>
            </el-form-item>
          </el-form>

          <el-row :gutter="16" class="summary-cards">
            <el-col :span="4">
              <div class="summary-card">
                <div class="card-label">节点总数</div>
                <div class="card-value">{{ progress?.summary.total ?? 0 }}</div>
              </div>
            </el-col>
            <el-col :span="4">
              <div class="summary-card">
                <div class="card-label">已完成</div>
                <div class="card-value">{{ progress?.summary.done ?? 0 }}</div>
              </div>
            </el-col>
            <el-col :span="4">
              <div class="summary-card">
                <div class="card-label">进行中</div>
                <div class="card-value">{{ progress?.summary.inProgress ?? 0 }}</div>
              </div>
            </el-col>
            <el-col :span="4">
              <div class="summary-card">
                <div class="card-label">待处理</div>
                <div class="card-value">{{ progress?.summary.pending ?? 0 }}</div>
              </div>
            </el-col>
            <el-col :span="4">
              <div class="summary-card">
                <div class="card-label">超期未结</div>
                <div class="card-value" :class="{ danger: (progress?.summary.overtime ?? 0) > 0 }">
                  {{ progress?.summary.overtime ?? 0 }}
                </div>
              </div>
            </el-col>
          </el-row>

          <el-alert
            v-if="overtimeList.length > 0"
            :title="`超期预警 ${overtimeList.length} 条：${overtimeList.slice(0, 3).map(r => r.enterpriseName).join('、')}${overtimeList.length > 3 ? ' 等' : ''}`"
            type="warning" :closable="false" show-icon style="margin-bottom:12px"
          />

          <el-table :data="progress?.rows || []" v-loading="pLoading" border stripe>
            <el-table-column prop="enterpriseName" label="客户企业" min-width="150" />
            <el-table-column prop="period" label="期间" width="80" />
            <el-table-column prop="stage" label="节点" width="120">
              <template #default="{ row }">
                {{ stageLabel(row.stage) }}
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="assignedToName" label="经办人" width="110" />
            <el-table-column prop="dueDate" label="期限" width="110" />
            <el-table-column label="超期" width="70" align="center">
              <template #default="{ row }">
                <el-icon v-if="row.overtime" color="#f56c6c" :size="16"><Warning /></el-icon>
                <span v-else>-</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- ── 工作量统计 ── -->
        <el-tab-pane label="工作量统计" name="workload">
          <el-form :model="wQuery" inline class="filter-form">
            <el-form-item label="区间">
              <el-date-picker v-model="wQuery.periodFrom" type="month" value-format="YYYYMM" :clearable="false" placeholder="起" style="width:130px" />
              <span class="range-sep">~</span>
              <el-date-picker v-model="wQuery.periodTo" type="month" value-format="YYYYMM" :clearable="false" placeholder="止" style="width:130px" />
            </el-form-item>
            <el-form-item label="维度">
              <el-select v-model="wQuery.groupBy" style="width:140px">
                <el-option label="按人员" value="USER" />
                <el-option label="按客户" value="ENTERPRISE" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :disabled="wLoading" @click="fetchWorkload">查询</el-button>
            </el-form-item>
          </el-form>

          <el-table :data="workload?.rows || []" v-loading="wLoading" border stripe>
            <el-table-column :prop="wQuery.groupBy === 'USER' ? 'userName' : 'userName'" :label="wQuery.groupBy === 'USER' ? '人员' : '客户'" min-width="150" />
            <el-table-column prop="assignedCustomers" :label="wQuery.groupBy === 'USER' ? '分配客户数' : '经办人数'" width="120" align="right" />
            <el-table-column prop="completionRate" label="完成率" width="110" align="right">
              <template #default="{ row }">
                {{ row.completionRate == null ? '-' : (row.completionRate * 100).toFixed(1) + '%' }}
              </template>
            </el-table-column>
            <el-table-column prop="inProgress" label="在办节点" width="100" align="right" />
            <el-table-column prop="overtime" label="超期节点" width="100" align="right">
              <template #default="{ row }">
                <span :class="{ danger: row.overtime > 0 }">{{ row.overtime }}</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Warning } from '@element-plus/icons-vue'
import { resolveDefaultPeriod } from '@/utils/period'
import {
  getServiceProgress, getServiceOvertime, getWorkload,
  type ServiceProgressVO, type ServiceProgressRowVO,
  type WorkloadVO, type WorkloadGroupBy,
  type ProgressStage, type ProgressStatus,
} from '@/api/modules/agency'

type Tab = 'progress' | 'workload'
const activeTab = ref<Tab>('progress')

const pLoading = ref(false)
const progress = ref<ServiceProgressVO | null>(null)
const overtimeList = ref<ServiceProgressRowVO[]>([])
const pQuery = ref({ period: '', stage: '' as ProgressStage | '', status: '' as ProgressStatus | '' })

const wLoading = ref(false)
const workload = ref<WorkloadVO | null>(null)
const wQuery = ref({ periodFrom: '', periodTo: '', groupBy: 'USER' as WorkloadGroupBy })

const stageLabel = (s: string) =>
  ({ INTAKE: '取票', BOOKING: '记账', REVIEW: '审核结账', FILING: '报税', DONE: '全部完成' }[s] ?? s)
const statusLabel = (s: string) =>
  ({ PENDING: '待处理', IN_PROGRESS: '进行中', DONE: '已完成' }[s] ?? s)
const statusType = (s: string): 'success' | 'warning' | 'info' =>
  s === 'DONE' ? 'success' : s === 'IN_PROGRESS' ? 'warning' : 'info'

const fetchProgress = async () => {
  pLoading.value = true
  try {
    progress.value = await getServiceProgress({
      period: pQuery.value.period || undefined,
      stage: pQuery.value.stage || undefined,
      status: pQuery.value.status || undefined,
    })
  } catch {
    progress.value = null
  } finally {
    pLoading.value = false
  }
}

const fetchOvertime = async () => {
  try {
    overtimeList.value = await getServiceOvertime()
  } catch {
    overtimeList.value = []
  }
}

const fetchWorkload = async () => {
  if (!wQuery.value.periodFrom || !wQuery.value.periodTo || wQuery.value.periodFrom > wQuery.value.periodTo) {
    ElMessage.warning('区间必填且起期不晚于止期')
    return
  }
  wLoading.value = true
  try {
    workload.value = await getWorkload({
      periodFrom: wQuery.value.periodFrom,
      periodTo: wQuery.value.periodTo,
      groupBy: wQuery.value.groupBy,
    })
  } catch {
    workload.value = null
  } finally {
    wLoading.value = false
  }
}

onMounted(async () => {
  const p = await resolveDefaultPeriod()
  pQuery.value.period = p
  wQuery.value.periodFrom = p.slice(0, 4) + '01'
  wQuery.value.periodTo = p
  fetchProgress()
  fetchOvertime()
  fetchWorkload()
})
</script>

<style scoped lang="scss">
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title {
  font-size: 16px;
  font-weight: 600;
}
.filter-form {
  margin-bottom: 12px;
}
.range-sep {
  margin: 0 8px;
  color: #909399;
}
.summary-cards {
  margin-bottom: 16px;
}
.summary-card {
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.card-label {
  font-size: 13px;
  color: #909399;
}
.card-value {
  font-size: 20px;
  font-weight: 600;
}
.danger {
  color: #f56c6c;
}
</style>
