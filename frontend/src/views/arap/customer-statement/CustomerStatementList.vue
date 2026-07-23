<template>
  <div class="customer-statement">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">客户对账</span>
        <el-button type="primary" @click="openGenerateDialog">生成对账单</el-button>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="期间">
          <el-input v-model="query.period" placeholder="YYYYMM" style="width:120px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" v-loading="loading" border @expand-change="onExpandChange">
        <el-table-column type="expand" width="40">
          <template #default="{ row }">
            <div style="padding:12px 20px; font-size:13px; color:#666;">
              <div v-if="expandedRow !== row.id">点击展开详情</div>
              <div v-else>
                <p><strong>客户名称：</strong>{{ row.customerName }}</p>
                <p><strong>期间：</strong>{{ row.period }}</p>
                <p><strong>原始金额：</strong>{{ fmtAmount(row.originalAmount) }}</p>
                <p><strong>已核销：</strong>{{ fmtAmount(row.writtenOffAmount) }}</p>
                <p><strong>未核销：</strong>{{ fmtAmount(row.outstandingAmount) }}</p>
                <p><strong>状态：</strong>{{ STATUS_MAP[row.status] || row.status }}</p>
                <p v-if="row.remark"><strong>备注：</strong>{{ row.remark }}</p>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="customerName" label="客户名称" min-width="140" />
        <el-table-column prop="period" label="期间" width="100" align="center" />
        <el-table-column label="原始金额" width="140" align="right">
          <template #default="{ row }">{{ fmtAmount(row.originalAmount) }}</template>
        </el-table-column>
        <el-table-column label="已核销" width="140" align="right">
          <template #default="{ row }">{{ fmtAmount(row.writtenOffAmount) }}</template>
        </el-table-column>
        <el-table-column label="未核销" width="140" align="right">
          <template #default="{ row }">{{ fmtAmount(row.outstandingAmount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ STATUS_MAP[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" v-if="row.status === 'DRAFT'" @click="handleSend(row)">
              发送
            </el-button>
            <el-button text type="success" size="small" v-if="row.status === 'SENT'" @click="handleConfirm(row)">
              确认
            </el-button>
            <el-button text type="warning" size="small" v-if="row.status === 'SENT' || row.status === 'DRAFT'" @click="handleDispute(row)">
              争议
            </el-button>
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

    <!-- 生成对账单对话框 -->
    <el-dialog v-model="generateDialogVisible" title="生成对账单" width="480px">
      <el-form :model="generateForm" label-width="100px">
        <el-form-item label="期间">
          <el-input v-model="generateForm.period" placeholder="YYYYMM" />
        </el-form-item>
        <el-form-item label="客户选择">
          <el-radio-group v-model="generateForm.customerMode">
            <el-radio label="ALL">全部客户</el-radio>
            <el-radio label="SELECT">指定客户</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="generateForm.customerMode === 'SELECT'" label="客户ID">
          <el-select
            v-model="generateForm.customerIds"
            multiple
            filterable
            remote
            reserve-keyword
            placeholder="请输入客户名称搜索"
            :remote-method="searchCustomer"
            :loading="customerLoading"
            style="width:100%"
          >
            <el-option
              v-for="c in customerOptions"
              :key="c.id"
              :label="c.name"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="generateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="generating" @click="onGenerate">确定生成</el-button>
      </template>
    </el-dialog>

    <!-- 争议对话框 -->
    <el-dialog v-model="disputeDialogVisible" title="标记争议" width="420px">
      <el-form :model="disputeForm" label-width="80px">
        <el-form-item label="原因">
          <el-input
            v-model="disputeForm.reason"
            type="textarea"
            :rows="4"
            placeholder="请输入争议原因"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="disputeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onDispute">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'
import { pageStatements, sendStatement, confirmStatement, disputeStatement, generateStatements } from '@/api/modules/arap'

const STATUS_MAP: Record<string, string> = {
  DRAFT: '草稿',
  SENT: '已发送',
  CONFIRMED: '已确认',
  DISPUTED: '争议中',
}

const statusTagType = (status: string): 'success' | 'warning' | 'info' | 'danger' | 'primary' => {
  const map: Record<string, 'success' | 'warning' | 'info' | 'danger' | 'primary'> = {
    DRAFT: 'info',
    SENT: 'warning',
    CONFIRMED: 'success',
    DISPUTED: 'danger',
  }
  return map[status] || 'info'
}

const query = reactive({ period: '', current: 1, size: 20 })
const list = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const expandedRow = ref<number | null>(null)

const onExpandChange = (row: any, expandedRows: any[] | boolean) => {
  expandedRow.value = Array.isArray(expandedRows) && expandedRows.length > 0 ? row.id : null
}

const fmtAmount = (v: any) => Number(v || 0).toFixed(2)

const fetchData = async () => {
  loading.value = true
  try {
    const res: any = await request.get('/sme/arap/v1/customer-statements/page', { params: query })
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

const resetQuery = () => {
  query.period = ''
  query.current = 1
  fetchData()
}

// 生成对账单
const generateDialogVisible = ref(false)
const generating = ref(false)
const generateForm = reactive({
  period: '',
  customerMode: 'ALL',
  customerIds: [] as number[],
})

const customerOptions = ref<any[]>([])
const customerLoading = ref(false)

const openGenerateDialog = () => {
  generateForm.period = query.period || ''
  generateForm.customerMode = 'ALL'
  generateForm.customerIds = []
  generateDialogVisible.value = true
}

const searchCustomer = async (keyword: string) => {
  if (!keyword) return
  customerLoading.value = true
  try {
    const res: any = await request.get('/v1/customers/page', { params: { name: keyword, current: 1, size: 20 } })
    customerOptions.value = res.records || []
  } finally {
    customerLoading.value = false
  }
}

const onGenerate = async () => {
  if (!generateForm.period) {
    ElMessage.warning('请输入期间')
    return
  }
  generating.value = true
  try {
    const customerIds = generateForm.customerMode === 'ALL' ? [] : generateForm.customerIds
    await request.post('/sme/arap/v1/customer-statements/generate', { customerIds, period: generateForm.period })
    ElMessage.success('对账单生成成功')
    generateDialogVisible.value = false
    fetchData()
  } finally {
    generating.value = false
  }
}

// 发送
const handleSend = async (row: any) => {
  try {
    await ElMessageBox.confirm('确定发送该对账单给客户？', '确认', { type: 'info' })
    await request.post(`/customer-statements/${row.id}/send`)
    ElMessage.success('已发送')
    fetchData()
  } catch {
    // cancelled
  }
}

// 确认
const handleConfirm = async (row: any) => {
  try {
    await ElMessageBox.confirm('确定确认该对账单？', '确认', { type: 'info' })
    await request.post(`/customer-statements/${row.id}/confirm`)
    ElMessage.success('已确认')
    fetchData()
  } catch {
    // cancelled
  }
}

// 争议
const disputeDialogVisible = ref(false)
const disputeForm = reactive({ reason: '' })
let disputeTarget: any = null

const handleDispute = (row: any) => {
  disputeTarget = row
  disputeForm.reason = ''
  disputeDialogVisible.value = true
}

const onDispute = async () => {
  if (!disputeForm.reason) {
    ElMessage.warning('请输入争议原因')
    return
  }
  try {
    await request.post(`/customer-statements/${disputeTarget.id}/dispute`, { reason: disputeForm.reason })
    ElMessage.success('已标记争议')
    disputeDialogVisible.value = false
    fetchData()
  } catch {
    // error
  }
}

onMounted(fetchData)
</script>

<style scoped lang="scss">
.customer-statement {
  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;

    .page-title {
      font-size: 16px;
      font-weight: 600;
    }
  }

  .filter-form {
    margin-bottom: 12px;
  }

  .page-pagination {
    margin-top: 16px;
    display: flex;
    justify-content: flex-end;
  }
}
</style>