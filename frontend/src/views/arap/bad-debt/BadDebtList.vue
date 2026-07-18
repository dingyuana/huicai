<template>
  <div class="bad-debt">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">坏账准备</span>
        <el-button type="primary" @click="openDialog()">计提坏账</el-button>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width:130px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已确认" value="CONFIRMED" />
            <el-option label="已凭证" value="VOUCHERED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" v-loading="loading" border>
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="period" label="期间" width="100" align="center" />
        <el-table-column label="方法" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ METHOD_MAP[row.method] || row.method }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="provisionDate" label="计提日期" width="120" />
        <el-table-column label="金额" width="150" align="right">
          <template #default="{ row }">{{ fmtAmount(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="应有余额" width="120" align="right">
          <template #default="{ row }">{{ fmtAmount(row.expectedBalance) }}</template>
        </el-table-column>
        <el-table-column label="已有余额" width="120" align="right">
          <template #default="{ row }">{{ fmtAmount(row.existingBalance) }}</template>
        </el-table-column>
        <el-table-column label="调整金额" width="130" align="right">
          <template #default="{ row }">
            <span :style="{ color: (row.adjustmentAmount || 0) > 0 ? '#f56c6c' : '#67c23a', fontWeight: 'bold' }">
              {{ fmtAmount(row.adjustmentAmount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="调整类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.adjustmentType === 'PROVISION'" type="danger" size="small">补提</el-tag>
            <el-tag v-else-if="row.adjustmentType === 'REVERSAL'" type="success" size="small">冲回</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">
              {{ STATUS_MAP[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'DRAFT'">
              <el-button text type="primary" @click="onConfirm(row)">确认</el-button>
            </template>
            <span v-else-if="row.voucherNo" style="color: #409eff; font-size: 13px;">已生成凭证 {{ row.voucherNo }}</span>
            <template v-if="row.status === 'VOUCHERED'">
              <el-divider direction="vertical" />
              <el-button text type="warning" @click="openWriteOffDialog(row)">核销</el-button>
              <el-button text type="success" @click="openRecoveryDialog(row)">收回</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <div class="page-pagination">
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </el-card>

    <!-- 计提坏账弹窗 -->
    <el-dialog v-model="dialogVisible" title="计提坏账准备" width="640px">
      <el-form :model="form" label-width="120px">
        <el-form-item label="期间">
          <el-input v-model="form.period" placeholder="YYYYMM" />
        </el-form-item>
        <el-form-item label="方法">
          <el-radio-group v-model="form.method">
            <el-radio label="AGING_RATIO">账龄比例法</el-radio>
            <el-radio label="PERCENTAGE">余额百分比法</el-radio>
          </el-radio-group>
        </el-form-item>
        <template v-if="form.method === 'AGING_RATIO'">
          <el-form-item label="信用期内">
            <el-input-number v-model="form.ratios.current" :min="0" :max="1" :step="0.01" :precision="2" style="width:100%" />
          </el-form-item>
          <el-form-item label="1-30天">
            <el-input-number v-model="form.ratios.days_1_30" :min="0" :max="1" :step="0.01" :precision="2" style="width:100%" />
          </el-form-item>
          <el-form-item label="31-60天">
            <el-input-number v-model="form.ratios.days_31_60" :min="0" :max="1" :step="0.01" :precision="2" style="width:100%" />
          </el-form-item>
          <el-form-item label="61-90天">
            <el-input-number v-model="form.ratios.days_61_90" :min="0" :max="1" :step="0.01" :precision="2" style="width:100%" />
          </el-form-item>
          <el-form-item label="91-180天">
            <el-input-number v-model="form.ratios.days_91_180" :min="0" :max="1" :step="0.01" :precision="2" style="width:100%" />
          </el-form-item>
          <el-form-item label="181-365天">
            <el-input-number v-model="form.ratios.days_181_365" :min="0" :max="1" :step="0.01" :precision="2" style="width:100%" />
          </el-form-item>
          <el-form-item label="365天以上">
            <el-input-number v-model="form.ratios.days_over_365" :min="0" :max="1" :step="0.01" :precision="2" style="width:100%" />
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="计提比例">
            <el-input-number v-model="form.ratio" :min="0" :max="1" :step="0.01" :precision="2" style="width:100%" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 核销弹窗 -->
    <el-dialog v-model="writeOffVisible" title="坏账核销" width="480px">
      <el-form :model="writeOffForm" label-width="100px">
        <el-form-item label="核销金额">
          <el-input-number v-model="writeOffForm.writeOffAmount" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="核销原因">
          <el-input v-model="writeOffForm.reason" type="textarea" :rows="3" placeholder="请输入核销原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="writeOffVisible = false">取消</el-button>
        <el-button type="primary" @click="onWriteOff">确定核销</el-button>
      </template>
    </el-dialog>

    <!-- 收回弹窗 -->
    <el-dialog v-model="recoveryVisible" title="坏账收回" width="480px">
      <el-form :model="recoveryForm" label-width="100px">
        <el-form-item label="收回金额">
          <el-input-number v-model="recoveryForm.amount" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="recoveryVisible = false">取消</el-button>
        <el-button type="primary" @click="onRecovery">确定收回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import request from '@/api/request'
import { getBadDebtScheme, writeOffBadDebt, recoveryBadDebt } from '@/api/modules/arap'

const METHOD_MAP: Record<string, string> = {
  AGING_RATIO: '账龄比例法', PERCENTAGE: '余额百分比法', INDIVIDUAL: '个别认定法',
}
const STATUS_MAP: Record<string, string> = {
  DRAFT: '草稿', CONFIRMED: '已确认', VOUCHERED: '已凭证',
}
const statusType = (s: string) => {
  if (s === 'CONFIRMED') return 'success'
  if (s === 'VOUCHERED') return 'warning'
  return 'info'
}

const query = reactive({ status: '', current: 1, size: 20 })
const list = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const dialogVisible = ref(false)
const form = reactive<any>({
  period: dayjs().format('YYYYMM'),
  method: 'AGING_RATIO',
  ratios: { current: 0, days_1_30: 0.05, days_31_60: 0.1, days_61_90: 0.2, days_91_180: 0.3, days_181_365: 0.5, days_over_365: 1 },
  ratio: 0.01,
})

// 核销
const writeOffVisible = ref(false)
const writeOffForm = reactive({ writeOffAmount: 0, reason: '', sourceId: 0, sourceType: '' })

// 收回
const recoveryVisible = ref(false)
const recoveryForm = reactive({ amount: 0, sourceId: 0 })

const fmtAmount = (v: any) => Number(v || 0).toFixed(2)

const fetchData = async () => {
  loading.value = true
  try {
    const res: any = await request.get('/bad-debts/page', { params: query })
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

const openDialog = async () => {
  dialogVisible.value = true
  // 加载默认方案的区间比例
  try {
    const scheme: any = await getBadDebtScheme()
    if (scheme && scheme.ratios) {
      Object.assign(form.ratios, scheme.ratios)
    }
  } catch {
    // 加载失败则使用表单已有的默认值
  }
}

const onSubmit = async () => {
  if (form.method === 'AGING_RATIO') {
    await request.post('/bad-debts/provision/aging', form.ratios, { params: { period: form.period } })
  } else {
    await request.post('/bad-debts/provision/percentage', null, { params: { period: form.period, ratio: form.ratio } })
  }
  ElMessage.success('计提完成')
  dialogVisible.value = false
  fetchData()
}

const onConfirm = async (row: any) => {
  await request.post(`/bad-debts/${row.id}/confirm`)
  ElMessage.success('确认成功')
  fetchData()
}

const openWriteOffDialog = (row: any) => {
  writeOffForm.sourceId = row.id
  writeOffForm.sourceType = 'BAD_DEBT'
  writeOffForm.writeOffAmount = row.totalAmount || 0
  writeOffForm.reason = ''
  writeOffVisible.value = true
}

const onWriteOff = async () => {
  await writeOffBadDebt({
    sourceType: writeOffForm.sourceType,
    sourceId: writeOffForm.sourceId,
    writeOffAmount: writeOffForm.writeOffAmount,
    reason: writeOffForm.reason,
  })
  ElMessage.success('核销成功')
  writeOffVisible.value = false
  fetchData()
}

const openRecoveryDialog = (row: any) => {
  recoveryForm.sourceId = row.id
  recoveryForm.amount = row.totalAmount || 0
  recoveryVisible.value = true
}

const onRecovery = async () => {
  await recoveryBadDebt({
    sourceId: recoveryForm.sourceId,
    amount: recoveryForm.amount,
  })
  ElMessage.success('收回成功')
  recoveryVisible.value = false
  fetchData()
}

onMounted(fetchData)
</script>