<template>
  <div class="batch-operation-page">
    <h2>批量操作</h2>

    <el-tabs v-model="activeTab">
      <!-- 批量导入 -->
      <el-tab-pane label="批量导入" name="import">
        <el-card>
          <el-form label-width="100px">
            <el-form-item label="导入类型">
              <el-select v-model="importType" placeholder="选择类型">
                <el-option label="销项发票" value="output" />
                <el-option label="进项发票" value="input" />
              </el-select>
            </el-form-item>
            <el-form-item label="目标企业">
              <el-select v-model="importEnterpriseId" placeholder="选择企业">
                <el-option
                  v-for="ent in authStore.enterpriseList"
                  :key="ent.id"
                  :label="ent.enterpriseName"
                  :value="ent.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="上传文件">
              <el-upload
                v-model:file-list="fileList"
                drag
                multiple
                :auto-upload="false"
                accept=".xlsx,.xls,.csv,.pdf,.ofd,.xml"
              >
                <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                <div class="el-upload__text">拖拽文件到此处或 <em>点击上传</em></div>
              </el-upload>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleImport" :loading="importLoading">开始导入</el-button>
            </el-form-item>
          </el-form>

          <el-table v-if="importResult" :data="importResult.details" stripe style="margin-top: 16px">
            <el-table-column prop="id" label="序号" width="80" />
            <el-table-column prop="success" label="结果" width="100">
              <template #default="{ row }">
                <el-tag :type="row.success ? 'success' : 'danger'">
                  {{ row.success ? '成功' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="message" label="详情" />
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 批量审核 -->
      <el-tab-pane label="批量审核" name="audit">
        <el-card>
          <el-form label-width="100px">
            <el-form-item label="目标企业">
              <el-select v-model="auditEnterpriseId" placeholder="选择企业">
                <el-option
                  v-for="ent in authStore.enterpriseList"
                  :key="ent.id"
                  :label="ent.enterpriseName"
                  :value="ent.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="凭证ID列表">
              <el-input
                v-model="auditIds"
                type="textarea"
                :rows="3"
                placeholder="输入凭证ID，逗号分隔，如：1,2,3"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleAudit" :loading="auditLoading">批量审核</el-button>
            </el-form-item>
          </el-form>

          <el-table v-if="auditResult" :data="auditResult.details" stripe style="margin-top: 16px">
            <el-table-column prop="id" label="凭证ID" width="100" />
            <el-table-column prop="success" label="结果" width="100">
              <template #default="{ row }">
                <el-tag :type="row.success ? 'success' : 'danger'">
                  {{ row.success ? '成功' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="message" label="详情" />
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 批量结账 -->
      <el-tab-pane label="批量结账" name="close">
        <el-card>
          <el-form label-width="100px">
            <el-form-item label="目标企业">
              <el-select v-model="closeEnterpriseIds" multiple placeholder="选择企业">
                <el-option
                  v-for="ent in authStore.enterpriseList"
                  :key="ent.id"
                  :label="ent.enterpriseName"
                  :value="ent.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="期间">
              <el-input v-model="closePeriod" placeholder="如 2026-07" style="width: 200px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleClose" :loading="closeLoading">批量结账</el-button>
            </el-form-item>
          </el-form>

          <el-table v-if="closeResult" :data="closeResult.details" stripe style="margin-top: 16px">
            <el-table-column prop="id" label="企业ID" width="100" />
            <el-table-column prop="success" label="结果" width="100">
              <template #default="{ row }">
                <el-tag :type="row.success ? 'success' : 'danger'">
                  {{ row.success ? '成功' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="message" label="详情" />
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useAuthStore } from '@/stores/auth.store'
import { batchImport, batchAuditVouchers, batchClose, type BatchResult } from '@/api/modules/agency'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'

const authStore = useAuthStore()
const activeTab = ref('import')

// 批量导入
const importType = ref('output')
const importEnterpriseId = ref<number | null>(null)
const fileList = ref<any[]>([])
const importLoading = ref(false)
const importResult = ref<BatchResult | null>(null)

// 批量审核
const auditEnterpriseId = ref<number | null>(null)
const auditIds = ref('')
const auditLoading = ref(false)
const auditResult = ref<BatchResult | null>(null)

// 批量结账
const closeEnterpriseIds = ref<number[]>([])
const closePeriod = ref('')
const closeLoading = ref(false)
const closeResult = ref<BatchResult | null>(null)

async function handleImport() {
  if (!importEnterpriseId.value) { ElMessage.warning('请选择目标企业'); return }
  if (fileList.value.length === 0) { ElMessage.warning('请上传文件'); return }
  importLoading.value = true
  try {
    const formData = new FormData()
    fileList.value.forEach(f => formData.append('files', f.raw!))
    importResult.value = await batchImport(formData, importEnterpriseId.value)
    ElMessage.success(`导入完成：成功 ${importResult.value.success}，失败 ${importResult.value.failed}`)
  } catch {
    ElMessage.error('导入失败')
  } finally {
    importLoading.value = false
  }
}

async function handleAudit() {
  if (!auditEnterpriseId.value) { ElMessage.warning('请选择目标企业'); return }
  const ids = auditIds.value.split(',').map(s => Number(s.trim())).filter(Boolean)
  if (ids.length === 0) { ElMessage.warning('请输入凭证ID'); return }
  auditLoading.value = true
  try {
    auditResult.value = await batchAuditVouchers(ids, auditEnterpriseId.value)
    ElMessage.success(`审核完成：成功 ${auditResult.value.success}，失败 ${auditResult.value.failed}`)
  } catch {
    ElMessage.error('审核失败')
  } finally {
    auditLoading.value = false
  }
}

async function handleClose() {
  if (closeEnterpriseIds.value.length === 0) { ElMessage.warning('请选择目标企业'); return }
  if (!closePeriod.value) { ElMessage.warning('请输入期间'); return }
  closeLoading.value = true
  try {
    closeResult.value = await batchClose(closeEnterpriseIds.value, closePeriod.value)
    ElMessage.success(`结账完成：成功 ${closeResult.value.success}，失败 ${closeResult.value.failed}`)
  } catch {
    ElMessage.error('结账失败')
  } finally {
    closeLoading.value = false
  }
}
</script>

<style scoped lang="scss">
.batch-operation-page {
  padding: 16px;

  h2 { margin: 0 0 16px; font-size: 18px; }
}
</style>
