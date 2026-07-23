<template>
  <div class="enterprise-list-page">
    <h2>客户企业列表</h2>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="总客户数" :value="stats.total" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="活跃客户" :value="stats.active" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="本月到期" :value="stats.expiring" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="待审核凭证" :value="stats.pendingVouchers" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input v-model="searchName" placeholder="企业名称" clearable style="width: 200px" />
      <el-input v-model="searchTaxId" placeholder="纳税人识别号" clearable style="width: 200px; margin-left: 12px" />
      <el-button type="primary" @click="fetchList" style="margin-left: 12px">搜索</el-button>
      <el-button type="success" @click="router.push('/agency/batch-operation')" style="margin-left: auto">
        批量操作
      </el-button>
    </div>

    <!-- 客户表格 -->
    <el-table :data="tableData" v-loading="loading" stripe>
      <el-table-column prop="enterpriseName" label="企业名称" min-width="160" />
      <el-table-column prop="taxId" label="纳税人识别号" width="160" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="(row as EnterpriseVO).status === 'ACTIVE' ? 'success' : (row as EnterpriseVO).status === 'PENDING' ? 'warning' : 'info'">
            {{ statusLabel((row as EnterpriseVO).status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="初始化" width="80">
        <template #default="{ row }">
          <el-tag :type="(row as EnterpriseVO).seedDataDone ? 'success' : 'warning'" size="small">
            {{ (row as EnterpriseVO).seedDataDone ? '已完成' : '未完成' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="160" />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="enterEnterprise(row as EnterpriseVO)">
            进入
          </el-button>
          <el-button
            v-if="(row as EnterpriseVO).status === 'PENDING'"
            size="small"
            type="success"
            @click="handleActivate(row as EnterpriseVO)"
          >
            激活
          </el-button>
          <el-button
            v-if="(row as EnterpriseVO).status === 'ACTIVE'"
            size="small"
            type="warning"
            @click="handleSuspend(row as EnterpriseVO)"
          >
            暂停
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      :page-size="size"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="fetchList"
      style="margin-top: 16px; justify-content: flex-end"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth.store'
import { getEnterpriseList, activateEnterprise, suspendEnterprise, type EnterpriseVO } from '@/api/modules/agency'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)
const searchName = ref('')
const searchTaxId = ref('')
const tableData = ref<EnterpriseVO[]>([])

const stats = reactive({
  total: 0,
  active: 0,
  expiring: 0,
  pendingVouchers: 0,
})

function statusLabel(status: string) {
  const map: Record<string, string> = { PENDING: '待激活', ACTIVE: '已激活', SUSPENDED: '已暂停', TERMINATED: '已终止' }
  return map[status] || status
}

async function fetchList() {
  loading.value = true
  try {
    const agencyId = authStore.agencyId
    if (!agencyId) return
    const res = await getEnterpriseList(agencyId, page.value, size.value)
    tableData.value = res.records
    total.value = res.total
    stats.total = res.total
    stats.active = res.records.filter(r => r.status === 'ACTIVE').length
  } finally {
    loading.value = false
  }
}

async function enterEnterprise(enterprise: EnterpriseVO) {
  try {
    await authStore.switchEnterprise(enterprise.id)
    ElMessage.success(`已进入「${enterprise.enterpriseName}」`)
    router.push('/dashboard')
  } catch {
    ElMessage.error('进入企业失败')
  }
}

async function handleActivate(enterprise: EnterpriseVO) {
  await ElMessageBox.confirm(`确认激活企业「${enterprise.enterpriseName}」？`)
  try {
    await activateEnterprise(enterprise.id)
    ElMessage.success('激活成功')
    fetchList()
  } catch {
    ElMessage.error('激活失败')
  }
}

async function handleSuspend(enterprise: EnterpriseVO) {
  await ElMessageBox.confirm(`确认暂停企业「${enterprise.enterpriseName}」？`)
  try {
    await suspendEnterprise(enterprise.id)
    ElMessage.success('已暂停')
    fetchList()
  } catch {
    ElMessage.error('暂停失败')
  }
}

onMounted(fetchList)
</script>

<style scoped lang="scss">
.enterprise-list-page {
  padding: 16px;

  h2 { margin: 0 0 16px; font-size: 18px; }

  .stats-row { margin-bottom: 16px; }

  .search-bar {
    display: flex;
    align-items: center;
    margin-bottom: 16px;
  }
}
</style>
