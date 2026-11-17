<template>
  <div class="period-list">
    <el-card shadow="never">
      <div class="page-header">
        <span class="page-title">会计期间管理</span>
        <div>
          <el-button type="primary" @click="openCreate">新增期间</el-button>
          <el-button @click="fetchData">刷新</el-button>
        </div>
      </div>

      <el-table :data="list" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="year" label="年度" width="80" align="center" />
        <el-table-column prop="month" label="月份" width="70" align="center" />
        <el-table-column prop="periodCode" label="期间编码" width="120" />
        <el-table-column prop="startDate" label="开始日期" width="130" />
        <el-table-column prop="endDate" label="结束日期" width="130" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="340" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" @click="openEdit(row as PeriodVO)">编辑</el-button>
            <el-button text size="small" v-if="row.status === 'open'" type="warning" @click="handleClose(row as PeriodVO)">结账</el-button>
            <!--
              SPEC-P150：按钮<b>常驻渲染</b>，只有已结账期间可点。
              原来整颗按钮在非 closed 时消失 ⇒ 用户感知为「功能没了」，
              而真正的原因可能是「结账没成功」。禁用态必须能问出原因（tooltip）。
            -->
            <el-button
              v-if="isReopenable(row.status)"
              text
              size="small"
              type="warning"
              data-test="period-reopen"
              @click="handleUnClose(row as PeriodVO)"
            >反结账</el-button>
            <el-tooltip v-else :content="reopenBlockedHint(row.status)" placement="top">
              <span class="disabled-action-wrap">
                <el-button
                  text
                  size="small"
                  type="warning"
                  disabled
                  data-test="period-reopen-disabled"
                >反结账</el-button>
              </span>
            </el-tooltip>
            <el-popconfirm title="确认删除此期间？" @confirm="handleDelete(row as PeriodVO)">
              <template #reference>
                <el-button text type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="page-pagination">
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :total="total"
          layout="total, prev, pager, next, jumper"
          @current-change="fetchData"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑期间' : '新增期间'" width="520" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100">
        <el-form-item label="会计年度" prop="year">
          <el-input-number v-model="form.year" :min="2020" :max="2099" style="width:100%" />
        </el-form-item>
        <el-form-item label="会计月份" prop="month">
          <el-select v-model="form.month" style="width:100%">
            <el-option v-for="m in 12" :key="m" :label="m + '月'" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始日期" prop="startDate">
          <el-date-picker v-model="form.startDate" type="date" placeholder="选择日期" style="width:100%" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="结束日期" prop="endDate">
          <el-date-picker v-model="form.endDate" type="date" placeholder="选择日期" style="width:100%" value-format="YYYY-MM-DD" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { getPeriodPage, createPeriod, updatePeriod, deletePeriod, closePeriod, unlockPeriod } from '@/api/modules/period'
import type { PeriodVO, PeriodCreateParam } from '@/api/modules/period'
import { isPeriodAlreadyClosed, periodStatusLabel, reopenBlockedHint } from '@/utils/periodClose'

const loading = ref(false)
const saving = ref(false)
const list = ref<PeriodVO[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const query = ref({ current: 1, size: 20 })
const form = ref({ year: new Date().getFullYear(), month: 1, startDate: '', endDate: '' })
const formRules = {
  year: [{ required: true, message: '请选择会计年度', trigger: 'blur' }],
  month: [{ required: true, message: '请选择会计月份', trigger: 'change' }],
  startDate: [{ required: true, message: '请选择开始日期', trigger: 'change' }],
  endDate: [{ required: true, message: '请选择结束日期', trigger: 'change' }],
}

function statusType(s: string) {
  return s === 'open' ? 'success' : s === 'closed' ? 'info' : 'warning'
}
function statusLabel(s: string) {
  // SPEC-P150：文案统一收口到 utils/periodClose，避免与「为何不可反结账」提示口径漂移
  return periodStatusLabel(s)
}
/** 仅「已结账」期间可反结账（与后端 reopenPeriod 的前置校验同口径） */
function isReopenable(status: string | null | undefined) {
  return isPeriodAlreadyClosed(status)
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getPeriodPage(query.value)
    // 🔴 原实现只赋 total、**从未给 list 赋值**，而表格绑定的是 :data="list"
    //    ⇒ 分页器显示条数但表格永远空白（2026-10-19 实测：接口 total=2，页面无数据）。
    //    同时静默吞掉 catch，使该缺陷在界面上表现为「本来就没数据」而非报错，极难定位。
    list.value = res.records ?? []
    total.value = res.total ?? 0
  } catch (e) {
    list.value = []
    total.value = 0
    ElMessage.error('会计期间加载失败：' + ((e as Error)?.message ?? e))
  } finally {
    loading.value = false
  }
}

function openCreate() {
  isEdit.value = false
  editId.value = null
  form.value = { year: new Date().getFullYear(), month: 1, startDate: '', endDate: '' }
  dialogVisible.value = true
}

function openEdit(row: PeriodVO) {
  isEdit.value = true
  editId.value = row.id
  form.value = { year: row.year, month: row.month, startDate: row.startDate, endDate: row.endDate }
  dialogVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (isEdit.value && editId.value) {
      await updatePeriod(editId.value, { startDate: form.value.startDate, endDate: form.value.endDate })
      ElMessage.success('修改成功')
    } else {
      await createPeriod(form.value as PeriodCreateParam)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    await fetchData()
  } catch {
    // handled
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: PeriodVO) {
  try {
    await deletePeriod(row.id)
    ElMessage.success('删除成功')
    await fetchData()
  } catch {
    // handled
  }
}

async function handleClose(row: PeriodVO) {
  try {
    await closePeriod(row.id)
    ElMessage.success('期间已结账')
    await fetchData()
  } catch {
    // handled
  }
}

async function handleUnClose(row: PeriodVO) {
  try {
    await ElMessageBox.confirm(`确认对期间 ${row.periodCode} 执行反结账？反结账后期间将恢复开启状态，可重新操作凭证。`, '反结账确认', { type: 'warning' })
  } catch { return }
  await unlockPeriod(row.id)
  ElMessage.success('反结账成功')
  await fetchData()
}

onMounted(fetchData)
</script>

<style scoped>
.period-list .page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title {
  font-size: 16px;
  font-weight: 600;
}
.page-pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
/* 禁用态按钮需要包一层元素才能触发 tooltip（Element Plus 的 disabled 按钮不派发事件） */
.disabled-action-wrap {
  display: inline-block;
}
</style>
