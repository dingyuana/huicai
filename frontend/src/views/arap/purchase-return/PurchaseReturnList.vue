<template>
  <div class="purchase-return">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">采购退货（财务处理）</span>
        <el-button type="primary" @click="showDialog = true">新增退货</el-button>
      </div>

      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="returnNo" label="退货单号" width="180" />
        <el-table-column prop="vendorName" label="供应商" width="160" />
        <el-table-column prop="originalDocNo" label="原单号" width="160" />
        <el-table-column prop="returnAmount" label="退货金额" width="160" align="right">
          <template #default="{ row }">{{ fmtAmount(row.returnAmount) }}</template>
        </el-table-column>
        <el-table-column prop="taxAmount" label="进项税转出" width="140" align="right">
          <template #default="{ row }">{{ fmtAmount(row.taxAmount) }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" min-width="200" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="voucherNo" label="凭证号" width="160" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="onView(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && list.length === 0" description="暂无采购退货记录" />
    </el-card>

    <!-- 新增退货弹窗 -->
    <el-dialog v-model="showDialog" title="新增采购退货（财务处理）" width="500px">
      <el-form :model="form" label-width="120px">
        <el-form-item label="供应商ID" required>
          <el-input v-model.number="form.vendorId" placeholder="供应商ID" />
        </el-form-item>
        <el-form-item label="原应付单号">
          <el-input v-model="form.originalDocNo" placeholder="选填，关联原应付单" />
        </el-form-item>
        <el-form-item label="退货金额" required>
          <el-input-number v-model="form.returnAmount" :precision="2" :min="0.01" style="width:100%" />
        </el-form-item>
        <el-form-item label="进项税额转出">
          <el-input-number v-model="form.taxAmount" :precision="2" :min="0" style="width:100%" />
        </el-form-item>
        <el-form-item label="退货原因">
          <el-input v-model="form.reason" type="textarea" rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="onSubmit" :loading="submitting">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createPurchaseReturn, getPurchaseReturn } from '@/api/modules/arap'
import request from '@/api/request'

const loading = ref(false)
const list = ref<any[]>([])
const showDialog = ref(false)
const submitting = ref(false)

const form = reactive({
  vendorId: undefined as number | undefined,
  originalDocNo: '',
  returnAmount: 0,
  taxAmount: 0,
  reason: '',
})

const fetchList = async () => {
  loading.value = true
  try {
    const res: any = await request.get('/purchase-returns/list')
    list.value = res || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

const onSubmit = async () => {
  if (!form.vendorId || !form.returnAmount) {
    ElMessage.warning('请填写供应商ID和退货金额')
    return
  }
  submitting.value = true
  try {
    const res: any = await createPurchaseReturn({
      originalDocNo: form.originalDocNo,
      vendorId: form.vendorId,
      returnAmount: form.returnAmount,
      taxAmount: form.taxAmount || 0,
      reason: form.reason,
    })
    ElMessage.success('退货处理完成，凭证已生成')
    showDialog.value = false
    fetchList()
  } finally {
    submitting.value = false
  }
}

const onView = async (row: any) => {
  const res: any = await getPurchaseReturn(row.id)
  ElMessage.info(`退货单: ${res.returnNo}, 凭证号: ${res.voucherNo || '无'}`)
}

const fmtAmount = (v: any) => Number(v || 0).toFixed(2)

const statusType = (s: string) => {
  if (s === 'DRAFT') return 'info'
  if (s === 'CONFIRMED') return 'warning'
  if (s === 'VOUCHERED') return 'success'
  if (s === 'REVERSED') return 'danger'
  return 'info'
}

const statusLabel = (s: string) => {
  if (s === 'DRAFT') return '草稿'
  if (s === 'CONFIRMED') return '已确认'
  if (s === 'VOUCHERED') return '已制证'
  if (s === 'REVERSED') return '已冲销'
  return s || '-'
}

onMounted(fetchList)
</script>

<style scoped lang="scss">
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title {
  font-size: 16px;
  font-weight: 600;
}
</style>