<template>
  <div class="carryover-guide">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">期末结转向导</span>
        <el-select v-model="period" placeholder="选择期间" style="width:160px" @change="onPeriodChange">
          <el-option v-for="p in periods" :key="p" :label="p" :value="p" />
        </el-select>
      </div>

      <!-- 结账前必须看清：本此结转的会计期间与对公司（醒目横幅） -->
      <el-alert v-if="period" class="close-banner" type="warning" :closable="false" show-icon>
        <template #title>
          <span class="banner-item">本次结账期间</span>
          <span class="banner-period">{{ period }}</span>
          <el-divider direction="vertical" style="height:22px;margin:0 12px" />
          <span class="banner-item">结账公司</span>
          <span class="banner-company">{{ enterpriseName || '…' }}</span>
        </template>
      </el-alert>
      <el-alert
        v-else
        class="close-banner"
        type="error"
        :closable="false"
        show-icon
        title="尚未选择结账期间——未选定期间前不能发起任何结转/结账操作"
      />

      <el-steps :active="activeStep" align-center finish-status="success" style="margin:24px 0">
        <el-step title="折旧计提" />
        <el-step title="税金计算" />
        <el-step title="成本结转" />
        <el-step title="损益结转" />
        <el-step title="结账检查" />
        <el-step title="执行结账" />
      </el-steps>
      <div class="step-content">
        <div v-if="activeStep===4">
          <el-button type="primary" :loading="checking" @click="doCheck">检查结账条件（{{ period || '未选期间' }}）</el-button>
          <div v-if="checkResult" style="margin-top:16px">
            <el-alert :title="checkResult.passed?'检查通过':'检查未通过'" :type="checkResult.passed?'success':'error'" show-icon />
            <ul v-if="checkResult.issues?.length"><li v-for="issue in checkResult.issues" :key="issue">{{ issue }}</li></ul>
          </div>
        </div>
        <div v-else-if="activeStep===3">
          <p>点击生成损益结转凭证，将损益类科目余额结转至本年利润。</p>
          <el-button type="primary" :loading="carrying" @click="doProfitCarryover">
            生成损益结转凭证（{{ period || '未选期间' }}）
          </el-button>
        </div>
        <div v-else-if="activeStep===5">
          <p>确认所有条件满足后执行结账。</p>
          <el-button type="danger" :loading="closing" @click="doClose">
            为【{{ enterpriseName }}】执行 {{ period || '' }} 期间结账
          </el-button>
        </div>
        <div v-else>
          <p>步骤 {{ activeStep+1 }} 处理中...</p>
          <el-button type="primary" @click="activeStep++">标记完成，下一步</el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>
<script setup lang="ts">
import request from '@/api/request'
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth.store'

const authStore = useAuthStore()
const period = ref('')
const periods = ref<string[]>([])
const activeStep = ref(0)
const checking = ref(false)
const carrying = ref(false)
const closing = ref(false)
const checkResult = ref<any>(null)
const enterpriseName = ref('')

async function loadPeriods() {
  const d: any[] = await request.get('/v1/periods/all')
  periods.value = d.map((p: any) => p.periodCode).filter(Boolean)
  if (periods.value.length) period.value = periods.value[periods.value.length - 1]
}

/** 公司名优先取会话里的 currentEnterpriseId 对照 enterpriseList；列表未装载时拉一次用户信息补齐 */
async function loadEnterpriseName() {
  const match = authStore.enterpriseList?.find((e: any) => e.id === authStore.currentEnterpriseId)
  if (match?.enterpriseName) {
    enterpriseName.value = match.enterpriseName
    return
  }
  if (!authStore.userInfo) {
    try { await authStore.fetchUserInfo() } catch { /* 名字取不到就用占位 */ }
  }
  const retry = authStore.enterpriseList?.find((e: any) => e.id === authStore.currentEnterpriseId)
  enterpriseName.value = retry?.enterpriseName || (authStore.userInfo as any)?.homeEnterpriseName || ''
}

function onPeriodChange() {
  // 换期间后旧检查结果不再可信，重置回第一步并提示
  checkResult.value = null
  activeStep.value = 0
  ElMessage.info(`已切换结账期间：${period.value}`)
}

async function doCheck() {
  checking.value = true
  try { checkResult.value = await request.get('/base/voucher/v1/period-close/check', { params: { period: period.value } }) }
  finally { checking.value = false }
}

async function doProfitCarryover() {
  if (!period.value) return ElMessage.warning('请先选择会计期间')
  carrying.value = true
  try {
    await request.post('/base/voucher/v1/period-close/profit-carryover', null, { params: { period: period.value } })
    ElMessage.success(`【${enterpriseName.value || '当前公司'}】 ${period.value} 期间损益结转凭证已生成`)
    activeStep.value++
  } finally { carrying.value = false }
}

async function doClose() {
  if (!period.value) return ElMessage.warning('请先选择会计期间')
  closing.value = true
  try {
    await request.post('/base/voucher/v1/period-close/close', null, { params: { period: period.value } })
    ElMessage.success(`【${enterpriseName.value || '当前公司'}】期间 ${period.value} 已结账`)
    activeStep.value++
  } finally { closing.value = false }
}

onMounted(async () => {
  await Promise.all([loadPeriods(), loadEnterpriseName()])
})
</script>
<style scoped>
.page-header { display:flex; justify-content:space-between; align-items:center; }
.page-title { font-size:16px; font-weight:600; }
.step-content { text-align:center; padding:40px 0; }
/* 结账前醒目横幅：期间橙金大字 + 公司名蓝色大字，一眼可确认在对谁做哪一期 */
.close-banner { margin: 12px 0 4px; }
.banner-item { font-size: 14px; color: var(--el-text-color-regular); vertical-align: middle; }
.banner-period {
  font-size: 22px;
  font-weight: 700;
  color: var(--el-color-warning-dark-2, #b88230);
  margin: 0 10px;
  vertical-align: middle;
  letter-spacing: 1px;
}
.banner-company {
  font-size: 22px;
  font-weight: 700;
  color: var(--el-color-primary);
  margin-left: 10px;
  vertical-align: middle;
}
</style>