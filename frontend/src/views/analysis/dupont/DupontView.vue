<template>
  <div class="dupont">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">杜邦分析</span>
        <span class="page-desc">ROE = 净利率 × 资产周转率 × 权益乘数</span>
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
        <el-col :span="6">
          <el-card shadow="never" class="metric-card">
            <div class="metric-title">净利率</div>
            <div class="metric-value primary">{{ fmtAmount(data.netMargin) }}%</div>
            <div class="metric-sub">净利润 / 营业收入</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="metric-card">
            <div class="metric-title">资产周转率</div>
            <div class="metric-value success">{{ fmtAmount(data.assetTurnover) }}</div>
            <div class="metric-sub">营业收入 / 总资产</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="metric-card">
            <div class="metric-title">权益乘数</div>
            <div class="metric-value warning">{{ fmtAmount(data.equityMultiplier) }}</div>
            <div class="metric-sub">总资产 / 净资产</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="metric-card highlight">
            <div class="metric-title">ROE (净资产收益率)</div>
            <div class="metric-value danger">{{ fmtAmount(data.roe) }}%</div>
            <div class="metric-sub">三因子乘积</div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { dupontAnalysis } from '@/api/modules/report'

const query = reactive({ period: dayjs().format('YYYYMM') })
const data = ref<any>(null)

const fmtAmount = (v: any) => Number(v || 0).toFixed(2)

const fetchData = async () => {
  if (!query.period) return
  data.value = await dupontAnalysis(query.period)
}

onMounted(fetchData)
</script>

<style scoped>
.metric-card {
  margin-bottom: 16px;
  text-align: center;
}
.metric-card.highlight {
  background: linear-gradient(135deg, #f56c6c 0%, #ff9a8b 100%);
  color: #fff;
}
.metric-card.highlight .metric-title,
.metric-card.highlight .metric-sub {
  color: rgba(255, 255, 255, 0.9);
}
.metric-title {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}
.metric-value {
  font-size: 32px;
  font-weight: 700;
  margin-bottom: 4px;
}
.metric-value.primary { color: #409eff; }
.metric-value.success { color: #67c23a; }
.metric-value.warning { color: #e6a23c; }
.metric-value.danger { color: #f56c6c; }
.metric-sub {
  font-size: 12px;
  color: #c0c4cc;
}
</style>
