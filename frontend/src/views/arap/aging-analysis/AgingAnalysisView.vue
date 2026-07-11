<template>
  <div class="aging-analysis">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">账龄分析</span>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="期间">
          <el-input v-model="query.period" placeholder="YYYYMM" style="width:130px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchAll">查询</el-button>
        </el-form-item>
        <el-form-item>
          <el-button @click="onGenerateAlerts">生成预警</el-button>
        </el-form-item>
      </el-form>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="账龄分布" name="distribution">
          <el-table :data="agingList" v-loading="loading.aging" border stripe>
            <el-table-column prop="rangeName" label="账龄区间" width="160" />
            <el-table-column prop="amount" label="金额" width="180" align="right">
              <template #default="{ row }">{{ fmtAmount(row.amount) }}</template>
            </el-table-column>
            <el-table-column prop="count" label="笔数" width="100" align="center" />
            <el-table-column label="占比" width="140" align="right">
              <template #default="{ row }">{{ fmtPercent(row.ratio) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="到期债权" name="due">
          <el-table :data="dueList" v-loading="loading.due" border stripe>
            <el-table-column prop="customerName" label="客户" width="160" />
            <el-table-column prop="docNo" label="单号" width="160" />
            <el-table-column prop="dueDate" label="到期日" width="120" align="center" />
            <el-table-column prop="unsettledAmount" label="未清金额" width="160" align="right">
              <template #default="{ row }">{{ fmtAmount(row.unsettledAmount) }}</template>
            </el-table-column>
            <el-table-column prop="overdueDays" label="逾期天数" width="100" align="center" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="逾期预警" name="alerts">
          <el-form :model="alertQuery" inline class="filter-form" style="margin-bottom:12px">
            <el-form-item label="预警等级">
              <el-select v-model="alertQuery.alertLevel" clearable placeholder="全部" style="width:130px">
                <el-option label="低" value="LOW" />
                <el-option label="中" value="MEDIUM" />
                <el-option label="高" value="HIGH" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="alertQuery.status" clearable placeholder="全部" style="width:130px">
                <el-option label="未处理" value="ACTIVE" />
                <el-option label="已忽略" value="DISMISSED" />
                <el-option label="已解决" value="RESOLVED" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="fetchAlerts">查询</el-button>
            </el-form-item>
          </el-form>

          <el-table :data="alertList" v-loading="loading.alerts" border stripe>
            <el-table-column prop="customerName" label="客户" width="160" />
            <el-table-column prop="alertLevel" label="等级" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="alertLevelType(row.alertLevel)" size="small">
                  {{ row.alertLevel === 'HIGH' ? '高' : row.alertLevel === 'MEDIUM' ? '中' : '低' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="amount" label="金额" width="160" align="right">
              <template #default="{ row }">{{ fmtAmount(row.amount) }}</template>
            </el-table-column>
            <el-table-column prop="overdueDays" label="逾期天数" width="100" align="center" />
            <el-table-column prop="description" label="预警说明" min-width="200" />
            <el-table-column prop="status" label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="alertStatusType(row.status)" size="small">
                  {{ row.status === 'ACTIVE' ? '未处理' : row.status === 'DISMISSED' ? '已忽略' : '已解决' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button text type="primary" v-if="row.status === 'ACTIVE'" @click="onDismissAlert(row.id)">忽略</el-button>
                <el-button text type="success" v-if="row.status === 'ACTIVE'" @click="onResolveAlert(row.id)">已解决</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import request from '@/api/request'

const activeTab = ref('distribution')

const query = reactive({
  period: dayjs().format('YYYYMM'),
})

const loading = reactive({
  aging: false,
  due: false,
  alerts: false,
})

// ===== 账龄分布 =====
const agingList = ref<any[]>([])

const fetchAging = async () => {
  loading.aging = true
  try {
    const res: any = await request.get('/aging-analysis/summary', { params: { period: query.period } })
    agingList.value = res || []
  } finally {
    loading.aging = false
  }
}

// ===== 到期债权 =====
const dueList = ref<any[]>([])

const fetchDue = async () => {
  loading.due = true
  try {
    const res: any = await request.get('/aging-analysis/due-receivables', { params: { date: query.period } })
    dueList.value = res || []
  } finally {
    loading.due = false
  }
}

// ===== 逾期预警 =====
const alertQuery = reactive({
  alertLevel: '',
  status: 'ACTIVE',
})
const alertList = ref<any[]>([])

const fetchAlerts = async () => {
  loading.alerts = true
  try {
    const params: any = {}
    if (alertQuery.alertLevel) params.alertLevel = alertQuery.alertLevel
    if (alertQuery.status) params.status = alertQuery.status
    const res: any = await request.get('/aging-analysis/alerts', { params })
    alertList.value = res || []
  } finally {
    loading.alerts = false
  }
}

const onDismissAlert = async (id: number) => {
  await request.post(`/aging-analysis/alerts/${id}/dismiss`)
  ElMessage.success('已忽略')
  fetchAlerts()
}

const onResolveAlert = async (id: number) => {
  await request.post(`/aging-analysis/alerts/${id}/resolve`)
  ElMessage.success('已解决')
  fetchAlerts()
}

const onGenerateAlerts = async () => {
  await request.post('/aging-analysis/alerts/generate', null, { params: { period: query.period } })
  ElMessage.success('预警生成完成')
  fetchAlerts()
}

// ===== 工具函数 =====
const fmtAmount = (v: any) => Number(v || 0).toFixed(2)

const fmtPercent = (v: any) => {
  const n = Number(v || 0)
  return (n * 100).toFixed(2) + '%'
}

const alertLevelType = (level: string) => {
  if (level === 'HIGH') return 'danger'
  if (level === 'MEDIUM') return 'warning'
  return 'info'
}

const alertStatusType = (status: string) => {
  if (status === 'ACTIVE') return 'danger'
  if (status === 'DISMISSED') return 'info'
  return 'success'
}

const fetchAll = () => {
  fetchAging()
  fetchDue()
  fetchAlerts()
}

onMounted(fetchAll)
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
</style>