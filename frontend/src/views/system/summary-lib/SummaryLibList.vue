<template>
  <div class="summary-lib-list">
    <el-card shadow="never">
      <div class="page-header">
        <el-form :model="query" inline>
          <el-form-item label="分类">
            <el-select v-model="query.category" placeholder="全部" clearable @change="fetchData">
              <el-option label="全部" value="" />
              <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
            </el-select>
          </el-form-item>
          <el-form-item label="关键词">
            <el-input v-model="query.keyword" placeholder="摘要内容" clearable @clear="fetchData" @keyup.enter="fetchData" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="fetchData">查询</el-button>
          </el-form-item>
        </el-form>
        <el-button type="primary" @click="openCreate">新增摘要</el-button>
      </div>

      <el-table :data="list" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="summaryCode" label="编码" width="100" />
        <el-table-column prop="summaryText" label="摘要内容" min-width="250" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" v-if="row.category">{{ row.category }}</el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="60" align="center" />
        <el-table-column prop="isActive" label="状态" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isActive ? 'success' : 'danger'" size="small">{{ row.isActive ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除？" @confirm="handleDelete(row)">
              <template #reference>
                <el-button text type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="page-pagination">
        <el-pagination
          v-model:current-page="query.page" v-model:page-size="query.size"
          :total="total" layout="total, prev, pager, next, jumper"
          @current-change="fetchData"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑摘要' : '新增摘要'" width="560" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100">
        <el-form-item label="摘要编码">
          <el-input v-model="form.summaryCode" placeholder="可选" />
        </el-form-item>
        <el-form-item label="摘要内容" prop="summaryText">
          <el-input v-model="form.summaryText" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" placeholder="选择分类" clearable style="width:100%">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="form.sortOrder" :min="1" style="width:100%" />
        </el-form-item>
        <el-form-item label="是否启用">
          <el-switch v-model="form.isActive" />
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
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { getSummaryLibPage, createSummaryLib, updateSummaryLib, deleteSummaryLib } from '@/api/modules/summaryLib'
import type { SummaryLibVO } from '@/api/modules/summaryLib'

const categories = ['费用', '收入', '往来', '转账', '其他']
const loading = ref(false)
const saving = ref(false)
const list = ref<SummaryLibVO[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const query = ref({ page: 1, size: 20, keyword: '', category: '' })
const form = ref({ summaryCode: '', summaryText: '', category: '', sortOrder: 1, isActive: true })
const formRules = {
  summaryText: [{ required: true, message: '请输入摘要内容', trigger: 'blur' }],
}

async function fetchData() {
  loading.value = true
  try {
    const params = { page: query.value.page, size: query.value.size }
    const p: any = { ...params }
    if (query.value.keyword) p.keyword = query.value.keyword
    if (query.value.category) p.category = query.value.category
    const res = await getSummaryLibPage(p)
    list.value = res.records
    total.value = res.total
  } catch { /* handled */ } finally { loading.value = false }
}

function openCreate() {
  isEdit.value = false
  editId.value = null
  form.value = { summaryCode: '', summaryText: '', category: '', sortOrder: 1, isActive: true }
  dialogVisible.value = true
}

function openEdit(row: SummaryLibVO) {
  isEdit.value = true
  editId.value = row.id
  form.value = { summaryCode: row.summaryCode, summaryText: row.summaryText, category: row.category, sortOrder: row.sortOrder, isActive: row.isActive }
  dialogVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (isEdit.value && editId.value) {
      await updateSummaryLib(editId.value, form.value)
      ElMessage.success('修改成功')
    } else {
      await createSummaryLib(form.value)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    await fetchData()
  } catch { /* handled */ } finally { saving.value = false }
}

async function handleDelete(row: SummaryLibVO) {
  await deleteSummaryLib(row.id)
  ElMessage.success('删除成功')
  await fetchData()
}

onMounted(fetchData)
</script>

<style scoped>
.summary-lib-list .page-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:16px; }
.page-title { font-size:16px; font-weight:600; }
.page-pagination { margin-top:16px; display:flex; justify-content:flex-end; }
</style>
