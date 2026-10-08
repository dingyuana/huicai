<template>
  <div class="dashboard">
    <h2>欢迎使用慧财智能财务平台</h2>
    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6">
        <el-card shadow="hover">
          <p class="stat-label">本月流水</p>
          <p class="stat-value">-- 笔</p>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <p class="stat-label">待审核凭证</p>
          <p class="stat-value warning">-- 张</p>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <p class="stat-label">本月净利润</p>
          <p class="stat-value positive">¥--</p>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <p class="stat-label">待处理流水</p>
          <p class="stat-value danger">-- 条</p>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="clickable-card" @click="goPendingSettlement">
          <p class="stat-label">待核销业务单据</p>
          <p class="stat-value danger">{{ pendingSettlementCount === null ? '--' : pendingSettlementCount }} 条</p>
        </el-card>
      </el-col>
    </el-row>
    <el-card class="quick-actions">
      <template #header><span>快速入口</span></template>
      <el-space wrap>
        <el-button type="primary">导入银行流水</el-button>
        <el-button>新增凭证</el-button>
        <el-button>结账体检</el-button>
      </el-space>
    </el-card>

    <el-card class="alert-card" v-if="alerts.length > 0">
      <template #header>
        <span>异常指标告警 <el-tag size="small" type="danger">{{ alerts.length }}</el-tag></span>
      </template>
      <el-alert
        v-for="a in alerts"
        :key="a.ruleId"
        :title="a.title"
        :description="a.detail"
        type="warning"
        show-icon
        :closable="false"
        style="margin-bottom:8px"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getPendingSettlementCount } from '@/api/modules/bankStatement'
import { reportDiagnostics } from '@/api/modules/report'

const router = useRouter()
const pendingSettlementCount = ref<number | null>(null)
const alerts = ref<any[]>([])

onMounted(async () => {
  try {
    const n = await getPendingSettlementCount()
    pendingSettlementCount.value = Number(n) || 0
  } catch {
    pendingSettlementCount.value = null
  }
  // REQ-037：加载异常指标告警（取当前月份作为期间）
  try {
    const now = new Date()
    const period = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}`
    alerts.value = await reportDiagnostics(period)
  } catch {
    alerts.value = []
  }
})

function goPendingSettlement() {
  router.push({ path: '/finance/bank-statement', query: { reviewStatus: 'payment_created' } })
}
</script>

<style scoped lang="scss">
.dashboard {
  padding: 24px;
  h2 { margin-bottom: 24px; }
  .stat-cards {
    margin-bottom: 24px;
  }
  .stat-label {
    font-size: 13px;
    color: #999;
    margin-bottom: 8px;
  }
  .stat-value {
    font-size: 24px;
    font-weight: 600;
    color: #333;
    &.warning { color: #faad14; }
    &.positive { color: #cf1322; }
    &.danger { color: #ff4d4f; }
  }
  .clickable-card { cursor: pointer; }
}
</style>