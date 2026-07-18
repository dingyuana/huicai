<template>
  <div class="payment-plan">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">付款计划</span>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="期间">
          <el-input v-model="query.period" placeholder="YYYYMM" style="width:130px" />
        </el-form-item>
        <el-form-item label="供应商">
          <el-input v-model="query.vendorId" placeholder="供应商ID" style="width:130px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchPlan">生成计划</el-button>
        </el-form-item>
      </el-form>

      <div v-if="loading" v-loading="loading" style="min-height:200px"></div>

      <template v-for="group in planList" :key="group.vendorId">
        <el-descriptions :title="group.vendorName" :column="3" border style="margin-top:16px">
          <el-descriptions-item label="待付总额" align="right">
            <span style="color:#f56c6c;font-weight:600">{{ fmtAmount(group.totalDue) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="待付笔数" align="center">{{ group.itemCount }}</el-descriptions-item>
        </el-descriptions>

        <el-table :data="group.items" border stripe style="margin-top:8px">
          <el-table-column prop="docNo" label="单号" width="180" />
          <el-table-column prop="docType" label="类型" width="120" />
          <el-table-column prop="dueDate" label="到期日" width="120" align="center" />
          <el-table-column prop="unsettledAmount" label="未清金额" width="160" align="right">
            <template #default="{ row }">{{ fmtAmount(row.unsettledAmount) }}</template>
          </el-table-column>
          <el-table-column prop="overdueDays" label="逾期天数" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.overdueDays > 0 ? 'danger' : 'success'" size="small">
                {{ row.overdueDays }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="suggestedPayDate" label="建议付款日" width="130" align="center" />
          <el-table-column prop="priority" label="优先级" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="priorityType(row.priority)" size="small">{{ priorityLabel(row.priority) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <el-empty v-if="!loading && planList.length === 0" description="暂无待付款项" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { generatePaymentPlan } from '@/api/modules/arap'

const query = reactive({
  period: dayjs().format('YYYYMM'),
  vendorId: '',
})

const loading = ref(false)
const planList = ref<any[]>([])

const fetchPlan = async () => {
  loading.value = true
  try {
    const params: any = { period: query.period }
    if (query.vendorId) params.vendorId = Number(query.vendorId)
    const res: any = await generatePaymentPlan(params)
    planList.value = res || []
  } finally {
    loading.value = false
  }
}

const fmtAmount = (v: any) => Number(v || 0).toFixed(2)

const priorityType = (p: string) => {
  if (p === 'CRITICAL') return 'danger'
  if (p === 'HIGH') return 'warning'
  if (p === 'MEDIUM') return 'warning'
  if (p === 'NORMAL') return 'info'
  return 'info'
}

const priorityLabel = (p: string) => {
  if (p === 'CRITICAL') return '紧急'
  if (p === 'HIGH') return '高'
  if (p === 'MEDIUM') return '中'
  if (p === 'NORMAL') return '普通'
  return '低'
}
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