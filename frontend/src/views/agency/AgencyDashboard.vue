<template>
  <div class="agency-dashboard">
    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-value">{{ dashboard.totalEnterprises }}</div>
            <div class="stat-label">管理企业</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-value">{{ dashboard.activeEnterprises }}</div>
            <div class="stat-label">活跃企业</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-value">{{ dashboard.totalVouchersThisMonth }}</div>
            <div class="stat-label">本月凭证</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-value">{{ dashboard.pendingAuditVouchers }}</div>
            <div class="stat-label">待审核</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="section-card" header="企业列表">
      <el-table :data="dashboard.enterprises" stripe v-loading="loading">
        <el-table-column prop="enterpriseName" label="企业名称" min-width="150" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'warning'">
              {{ row.status === 'ACTIVE' ? '活跃' : (row.status || '未知') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="种子数据" width="100">
          <template #default="{ row }">
            <el-tag :type="row.seedDataDone ? 'success' : 'info'">
              {{ row.seedDataDone ? '已初始化' : '未初始化' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="section-card" header="会计工作量">
      <el-table :data="dashboard.accountants" stripe v-loading="loading">
        <el-table-column prop="userId" label="用户ID" width="100" />
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="row.agencyRole === 'ACCOUNTANT' ? 'success' : 'info'">
              {{ row.agencyRole === 'ACCOUNTANT' ? '会计' : '助理' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="enterpriseCount" label="负责企业" width="100" />
        <el-table-column prop="voucherCountThisMonth" label="本月凭证" width="100" />
        <el-table-column prop="pendingAuditCount" label="待审核" width="100" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" link
              @click="$router.push(`/agency/accountant-detail/${row.userId}`)">
              查看详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getDashboard } from '@/api/modules/agency'

const loading = ref(false)
const dashboard = ref({
  totalEnterprises: 0,
  activeEnterprises: 0,
  totalVouchersThisMonth: 0,
  pendingAuditVouchers: 0,
  enterprises: [] as any[],
  accountants: [] as any[]
})

onMounted(async () => {
  loading.value = true
  try {
    const res = await getDashboard()
    dashboard.value = res.data
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.agency-dashboard { padding: 16px; }
.stat-cards { margin-bottom: 16px; }
.stat-card { text-align: center; padding: 8px 0; }
.stat-value { font-size: 28px; font-weight: 700; color: #303133; }
.stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
.section-card { margin-bottom: 16px; }
</style>