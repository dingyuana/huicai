<template>
  <div class="assignment-page">
    <h2>客户分配</h2>

    <!-- 选择会计 -->
    <div class="select-bar">
      <span class="label">选择会计：</span>
      <el-select
        v-model="selectedAgencyUserId"
        placeholder="请选择会计"
        filterable
        style="width: 280px"
        @change="onSelect"
      >
        <el-option
          v-for="u in agencyUsers"
          :key="u.id"
          :label="`${u.realName} (${u.username})`"
          :value="u.id"
        />
      </el-select>
    </div>

    <el-row v-if="selectedAgencyUserId" :gutter="16">
      <!-- 已分配企业 -->
      <el-col :span="12">
        <el-card header="已分配企业" shadow="hover">
          <el-table :data="assignedList" v-loading="loading" stripe size="small">
            <el-table-column prop="enterpriseName" label="企业名称" />
            <el-table-column prop="assignedAt" label="分配时间" width="160" />
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button type="danger" size="small" @click="handleUnassign(row as AssignmentVO)">
                  取消分配
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!loading && assignedList.length === 0" description="暂无分配" />
        </el-card>
      </el-col>

      <!-- 可分配企业 -->
      <el-col :span="12">
        <el-card header="可分配企业" shadow="hover">
          <el-table :data="availableList" v-loading="loading" stripe size="small">
            <el-table-column prop="enterpriseName" label="企业名称" />
            <el-table-column prop="taxId" label="税号" width="160" />
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button type="primary" size="small" @click="handleAssign(row as EnterpriseVO)">
                  分配
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!loading && availableList.length === 0" description="无可分配企业" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getAgencyUsers, getAssignments, assignEnterprise, unassignEnterprise,
  getEnterpriseList,
  type AgencyUserVO, type AssignmentVO, type EnterpriseVO,
} from '@/api/modules/agency'
import { useAuthStore } from '@/stores/auth.store'

const route = useRoute()
const authStore = useAuthStore()

const agencyUsers = ref<AgencyUserVO[]>([])
const selectedAgencyUserId = ref<number | null>(null)
const assignedList = ref<AssignmentVO[]>([])
const availableList = ref<EnterpriseVO[]>([])
const loading = ref(false)

// 加载代理用户列表
async function loadAgencyUsers() {
  try {
    agencyUsers.value = await getAgencyUsers()
    // 如果 URL 中指定了 agencyUserId，自动选中
    const qId = route.query.agencyUserId
    if (qId) {
      selectedAgencyUserId.value = Number(qId)
      onSelect(Number(qId))
    }
  } catch {
    ElMessage.error('加载用户列表失败')
  }
}

// 选择会计后加载数据
async function onSelect(agencyUserId: number) {
  loading.value = true
  try {
    const [assignments, allEnterprises] = await Promise.all([
      getAssignments(agencyUserId),
      getEnterpriseList(authStore.agencyId!, 1, 999),
    ])
    assignedList.value = assignments
    const assignedIds = new Set(assignments.map(a => a.enterpriseId))
    availableList.value = allEnterprises.records.filter(e => !assignedIds.has(e.id))
  } catch {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

// 分配企业
async function handleAssign(enterprise: EnterpriseVO) {
  try {
    await ElMessageBox.confirm(`确定将「${enterprise.enterpriseName}」分配给该会计吗？`, '确认分配', { type: 'info' })
  } catch {
    return
  }
  try {
    await assignEnterprise({ agencyUserId: selectedAgencyUserId.value!, enterpriseId: enterprise.id })
    ElMessage.success('分配成功')
    onSelect(selectedAgencyUserId.value!)
  } catch {
    ElMessage.error('分配失败')
  }
}

// 取消分配
async function handleUnassign(assignment: AssignmentVO) {
  try {
    await ElMessageBox.confirm(`确定取消「${assignment.enterpriseName}」的分配吗？`, '确认取消', { type: 'warning' })
  } catch {
    return
  }
  try {
    await unassignEnterprise(assignment.id)
    ElMessage.success('已取消分配')
    onSelect(selectedAgencyUserId.value!)
  } catch {
    ElMessage.error('操作失败')
  }
}

onMounted(loadAgencyUsers)
</script>

<style scoped lang="scss">
.assignment-page {
  padding: 16px;
  h2 { margin-bottom: 16px; }
  .select-bar {
    display: flex;
    align-items: center;
    margin-bottom: 20px;
    .label { margin-right: 8px; font-weight: 500; }
  }
}
</style>