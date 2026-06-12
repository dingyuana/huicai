<template>
  <div class="key-metrics">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">关键指标</span>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="期间">
          <el-input v-model="query.period" placeholder="YYYYMM" style="width:120px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
        </el-form-item>
      </el-form>

      <el-row :gutter="20" v-if="data">
        <el-col :span="8">
          <el-card shadow="never" class="metric-card">
            <div class="metric-title">毛利率</div>
            <div class="metric-value primary">{{ fmtAmount(data.grossMargin) }}%</div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="never" class="metric-card">
            <div class="metric-title">净利率</div>
            <div class="metric-value success">{{ fmtAmount(data.netMargin) }}%</div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="never" class="metric-card">
            <div class="metric-title">资产回报率(ROA)</div>
            <div class="metric-value warning">{{ fmtAmount(data.roa) }}%</div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="never" class="metric-card">
            <div class="metric-title">净资产收益率(ROE)</div>
            <div class="metric-value danger">{{ fmtAmount(data.roe) }}%</div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="never" class="metric-card">
            <div class="metric-title">流动比率</div>
            <div class="metric-value primary">{{ fmtAmount(data.currentRatio) }}</div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="never" class="metric-card">
            <div class="metric-title">速动比率</div>
            <div class="metric-value success">{{ fmtAmount(data.quickRatio) }}</div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="never" class="metric-card">
            <div class="metric-title">资产负债率</div>
            <div class="metric-value danger">{{ fmtAmount(data.debtRatio) }}%</div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { keyMetrics } from '@/api/modules/report'

const query = reactive({ period: dayjs().format('YYYYMM') })
const data = ref<any>(null)

const fmtAmount = (v: any) => Number(v || 0).toFixed(2)

const fetchData = async () => {
  if (!query.period) return
  data.value = await keyMetrics(query.period)
}

onMounted(fetchData)
</script>

<style scoped>
.metric-card {
  margin-bottom: 16px;
  text-align: center;
}
.metric-title {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}
.metric-value {
  font-size: 28px;
  font-weight: 600;
}
.metric-value.primary { color: #409eff; }
.metric-value.success { color: #67c23a; }
.metric-value.warning { color: #e6a23c; }
.metric-value.danger { color: #f56c6c; }
</style>
