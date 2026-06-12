<template>
  <div class="carryover-guide">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">期末结转向导</span>
        <el-select v-model="period" placeholder="选择期间" style="width:160px">
          <el-option v-for="p in periods" :key="p" :label="p" :value="p" />
        </el-select>
      </div>
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
          <el-button type="primary" :loading="checking" @click="doCheck">检查结账条件</el-button>
          <div v-if="checkResult" style="margin-top:16px">
            <el-alert :title="checkResult.passed?'检查通过':'检查未通过'" :type="checkResult.passed?'success':'error'" show-icon />
            <ul v-if="checkResult.issues?.length"><li v-for="issue in checkResult.issues" :key="issue">{{ issue }}</li></ul>
          </div>
        </div>
        <div v-else-if="activeStep===3">
          <p>点击生成损益结转凭证，将损益类科目余额结转至本年利润。</p>
          <el-button type="primary" :loading="carrying" @click="doProfitCarryover">生成损益结转凭证</el-button>
        </div>
        <div v-else-if="activeStep===5">
          <p>确认所有条件满足后执行结账。</p>
          <el-button type="danger" :loading="closing" @click="doClose">执行结账</el-button>
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
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
const { default: request } = await import('@/api/request')

const period = ref('')
const periods = ref<string[]>([])
const activeStep = ref(0)
const checking = ref(false)
const carrying = ref(false)
const closing = ref(false)
const checkResult = ref<any>(null)

async function loadPeriods() {
  const d: any[] = await request.get('/periods/list')
  periods.value = d.map((p: any) => p.period)
  if (periods.value.length) period.value = periods.value[periods.value.length - 1]
}

async function doCheck() {
  checking.value = true
  try { checkResult.value = await request.get('/period-close/check', { params: { period: period.value } }) }
  finally { checking.value = false }
}

async function doProfitCarryover() {
  carrying.value = true
  try {
    await request.post('/period-close/profit-carryover', null, { params: { period: period.value } })
    ElMessage.success('损益结转凭证已生成')
    activeStep.value++
  } finally { carrying.value = false }
}

async function doClose() {
  closing.value = true
  try {
    await request.post('/period-close/close', null, { params: { period: period.value } })
    ElMessage.success(`期间 ${period.value} 已结账`)
    activeStep.value++
  } finally { closing.value = false }
}

onMounted(loadPeriods)
</script>
<style scoped>
.page-header { display:flex; justify-content:space-between; align-items:center; }
.page-title { font-size:16px; font-weight:600; }
.step-content { text-align:center; padding:40px 0; }
</style>