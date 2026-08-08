<template>
  <div class="cash-flow">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">现金流量表</span>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="期间">
          <el-input v-model="query.period" placeholder="YYYYMM" style="width:120px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="onExport">导出</el-button>
        </el-form-item>
      </el-form>

      <el-table v-if="result" :data="rows" border>
        <el-table-column prop="label" label="项目" min-width="200" />
        <el-table-column label="金额" align="right" width="180">
          <template #default="{ row }">
            <span :class="{ 'amount-bold': row.bold }">{{ fmtAmount(row.amount) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, computed } from 'vue'
import { resolveDefaultPeriod } from '@/utils/period'
import { cashFlowStatement } from '@/api/modules/report'

const query = reactive({ period: '' })
const result = ref<any>(null)

const fmtAmount = (v: any) => Number(v || 0).toFixed(2)

const rows = computed(() => {
  if (!result.value) return []
  return [
    { label: '一、经营活动现金流量',   amount: '', bold: true },
    { label: '  现金流入',           amount: result.value.operatingIn, bold: false },
    { label: '  现金流出',           amount: result.value.operatingOut, bold: false },
    { label: '  经营活动净流量',     amount: result.value.operatingNet, bold: true },
    { label: '二、投资活动现金流量',   amount: '', bold: true },
    { label: '  现金流入',           amount: result.value.investingIn, bold: false },
    { label: '  现金流出',           amount: result.value.investingOut, bold: false },
    { label: '  投资活动净流量',     amount: result.value.investingNet, bold: true },
    { label: '三、筹资活动现金流量',   amount: '', bold: true },
    { label: '  现金流入',           amount: result.value.financingIn, bold: false },
    { label: '  现金流出',           amount: result.value.financingOut, bold: false },
    { label: '  筹资活动净流量',     amount: result.value.financingNet, bold: true },
    { label: '四、现金及现金等价物净增加额', amount: result.value.totalNet, bold: true },
  ]
})

const fetchData = async () => {
  if (!query.period) return
  result.value = await cashFlowStatement(query.period)
}

const onExport = () => {
  // 导出功能待实现
}

onMounted(async () => {
  query.period = await resolveDefaultPeriod()
  fetchData()
})
</script>

<style scoped>
.amount-bold {
  font-weight: 600;
  color: #409eff;
}
</style>
