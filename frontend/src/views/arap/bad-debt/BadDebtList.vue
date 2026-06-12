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
        <el-table-column label="金额" width="160" align="right">
          <template #default="{ row }">{{ fmtAmount(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="(row.status === 'CONFIRMED' ? 'success' : 'info') as any" size="small">
              {{ row.status === 'CONFIRMED' ? '已确认' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" v-if="row.status === 'DRAFT'">确认</el-button>
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

    <el-dialog v-model="dialogVisible" title="计提坏账准备" width="560px">
      <el-form :model="form" label-width="100px">
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
          <el-form-item label="1年内">
            <el-input-number v-model="form.ratios.current" :min="0" :max="1" :step="0.01" :precision="2" style="width:100%" />
          </el-form-item>
          <el-form-item label="1-2年">
            <el-input-number v-model="form.ratios.days_0_30" :min="0" :max="1" :step="0.01" :precision="2" style="width:100%" />
          </el-form-item>
          <el-form-item label="2-3年">
            <el-input-number v-model="form.ratios.days_31_60" :min="0" :max="1" :step="0.01" :precision="2" style="width:100%" />
          </el-form-item>
          <el-form-item label="3年以上">
            <el-input-number v-model="form.ratios.days_61_90" :min="0" :max="1" :step="0.01" :precision="2" style="width:100%" />
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import request from '@/api/request'

const METHOD_MAP: Record<string, string> = {
  AGING_RATIO: '账龄比例法', PERCENTAGE: '余额百分比法', INDIVIDUAL: '个别认定法',
}

const query = reactive({ status: '', current: 1, size: 20 })
const list = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const dialogVisible = ref(false)
const form = reactive<any>({
  period: dayjs().format('YYYYMM'),
  method: 'AGING_RATIO',
  ratios: { current: 0, days_0_30: 0.05, days_31_60: 0.2, days_61_90: 0.5 },
  ratio: 0.01,
})

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

const openDialog = () => {
  dialogVisible.value = true
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

onMounted(fetchData)
</script>
