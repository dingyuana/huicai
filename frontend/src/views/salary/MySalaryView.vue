<template>
  <div class="my-salary">
    <el-card shadow="never">
      <template #header>
        <div class="toolbar">
          <span>我的工资单（已发放记录，只读）</span>
          <el-button circle @click="load">
            <el-icon><Refresh /></el-icon>
          </el-button>
        </div>
      </template>

      <el-alert
        v-if="!loading && bills.length === 0"
        title="暂无已发放的工资单。若你应有记录，请联系管理员确认账号已在员工管理中绑定。"
        type="info"
        show-icon
        :closable="false"
      />

      <el-table :data="bills" v-loading="loading" border>
        <el-table-column prop="period" label="期间" width="100" />
        <el-table-column prop="totalGross" label="应发" align="right" :formatter="money" />
        <el-table-column prop="totalSocial" label="社保(个人)" align="right" :formatter="money" />
        <el-table-column prop="totalSpecialAdd" label="专项附加" align="right" :formatter="money" />
        <el-table-column prop="totalOtherDeduct" label="其他扣款" align="right" :formatter="money" />
        <el-table-column prop="totalTax" label="个税" align="right" :formatter="money" />
        <el-table-column prop="totalNet" label="实发" align="right">
          <template #default="{ row }">
            <span style="font-weight:600;color:#67c23a">{{ Number(row.totalNet || 0).toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row as any)">明细</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="detailVisible" title="工资明细" width="560px">
      <el-table :data="detailRows" border size="small">
        <el-table-column prop="itemName" label="薪资项目" min-width="140" />
        <el-table-column prop="amount" label="金额" align="right" :formatter="money" />
        <el-table-column prop="remark" label="说明" min-width="120" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getMySalaryBills, getMySalaryItems, type SalaryBill } from '@/api/modules/salary'

const bills = ref<SalaryBill[]>([])
const loading = ref(false)
const detailVisible = ref(false)
const detailRows = ref<any[]>([])

const money = (row: any, _col: any, val: any) => Number(val || 0).toFixed(2)

async function load(): Promise<void> {
  loading.value = true
  try {
    bills.value = await getMySalaryBills()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '加载失败')
  } finally {
    loading.value = false
  }
}

async function openDetail(row: SalaryBill): Promise<void> {
  try {
    detailRows.value = await getMySalaryItems(row.id!)
    detailVisible.value = true
  } catch (e: any) {
    ElMessage.error(e?.message ?? '明细加载失败')
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
