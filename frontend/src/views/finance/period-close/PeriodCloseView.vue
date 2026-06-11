<template>
  <div class="period-close">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">期末结账</span>
        <el-button @click="refresh">刷新</el-button>
      </div>

      <el-form inline>
        <el-form-item label="会计期间">
          <el-input v-model="period" placeholder="YYYYMM" style="width:160px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onCheck">结账前检查</el-button>
          <el-button type="warning" @click="onCarryover">生成损益结转</el-button>
          <el-button type="success" :disabled="!canClose" @click="onClose">执行结账</el-button>
          <el-button type="danger" @click="onReopen">反结账</el-button>
        </el-form-item>
      </el-form>

      <el-divider />

      <div v-if="checkResult">
        <h3>检查结果</h3>
        <el-alert
          :type="checkResult.passed ? 'success' : 'error'"
          :title="checkResult.passed ? '结账检查通过, 可执行结账' : '结账检查未通过, 请处理以下问题'"
          :closable="false"
        />
        <ul v-if="!checkResult.passed" class="issue-list">
          <li v-for="(issue, idx) in checkResult.issues" :key="idx">{{ issue }}</li>
        </ul>
        <p v-else class="ok-text">未发现阻碍结账的问题。</p>

        <h3>试算平衡</h3>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="期初">借 {{ fmt(checkResult.trialBalance.totalBeginDebit) }} / 贷 {{ fmt(checkResult.trialBalance.totalBeginCredit) }}</el-descriptions-item>
          <el-descriptions-item label="期初平衡">
            <el-tag :type="checkResult.trialBalance.beginBalanced ? 'success' : 'danger'" size="small">
              {{ checkResult.trialBalance.beginBalanced ? '是' : '否' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="发生">借 {{ fmt(checkResult.trialBalance.totalDebitTotal) }} / 贷 {{ fmt(checkResult.trialBalance.totalCreditTotal) }}</el-descriptions-item>
          <el-descriptions-item label="发生平衡">
            <el-tag :type="checkResult.trialBalance.movementBalanced ? 'success' : 'danger'" size="small">
              {{ checkResult.trialBalance.movementBalanced ? '是' : '否' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="期末">借 {{ fmt(checkResult.trialBalance.totalEndDebit) }} / 贷 {{ fmt(checkResult.trialBalance.totalEndCredit) }}</el-descriptions-item>
          <el-descriptions-item label="期末平衡">
            <el-tag :type="checkResult.trialBalance.endBalanced ? 'success' : 'danger'" size="small">
              {{ checkResult.trialBalance.endBalanced ? '是' : '否' }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <el-empty v-else description="请点击「结账前检查」开始" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { checkClose, profitCarryover, closePeriod, reopenPeriod, type CloseCheckResult } from '@/api/modules/periodClose'

const period = ref(new Date().toISOString().slice(0, 7).replace('-', ''))
const checkResult = ref<CloseCheckResult | null>(null)

const canClose = computed(() => checkResult.value?.passed === true)

function fmt(v: number) {
  return v == null ? '' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function refresh() {
  checkResult.value = null
}

async function onCheck() {
  if (!period.value) {
    ElMessage.warning('请输入会计期间')
    return
  }
  checkResult.value = await checkClose(period.value)
}

async function onCarryover() {
  if (!period.value) return
  await ElMessageBox.confirm(`将基于期间 ${period.value} 的所有已记账分录生成结转凭证, 是否继续?`, '提示', { type: 'warning' })
  try {
    const id = await profitCarryover(period.value)
    ElMessage.success(`结转凭证已生成, ID: ${id}, 请前往凭证管理编辑后提交记账`)
  } catch {
    // handled
  }
}

async function onClose() {
  if (!canClose.value) {
    ElMessage.warning('请先完成结账检查且通过')
    return
  }
  await ElMessageBox.confirm(`确认对期间 ${period.value} 执行结账? 结账后期间将锁定, 不可操作凭证.`, '高危操作', { type: 'warning' })
  try {
    await closePeriod(period.value)
    ElMessage.success('结账成功')
    checkResult.value = null
  } catch {
    // handled
  }
}

async function onReopen() {
  await ElMessageBox.confirm(`确认对期间 ${period.value} 执行反结账? 需最高权限, 操作将记入日志.`, '高危操作', { type: 'error' })
  try {
    await reopenPeriod(period.value)
    ElMessage.success('反结账成功')
    checkResult.value = null
  } catch {
    // handled
  }
}
</script>

<style scoped>
.period-close .page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title {
  font-size: 16px;
  font-weight: 600;
}
.issue-list {
  margin: 12px 0 0;
  padding-left: 24px;
  color: #f56c6c;
}
.ok-text {
  color: #67c23a;
  margin: 12px 0;
}
h3 {
  margin: 16px 0 12px;
  font-size: 14px;
  font-weight: 600;
}
</style>
