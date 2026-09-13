<script setup lang="ts">
/**
 * P67 批量操作统一结果弹窗（见 docs/development/frontend-batch-ops-convention.md）。
 * 展示归一化后的成功/失败统计与失败明细表。
 */
import type { NormalizedBatchResult } from '@/composables/useBatchOperation'

defineProps<{ modelValue: boolean; result: NormalizedBatchResult | null }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="批量操作结果"
    width="560"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template v-if="result">
      <el-result
        :icon="result.failures.length ? 'warning' : 'success'"
        :title="`成功 ${result.successCount} / 总 ${result.total} 条`"
        :sub-title="result.failures.length ? `失败 ${result.failures.length} 条，原因见下方列表` : '全部执行成功'"
      />
      <el-table
        v-if="result.failures.length"
        :data="result.failures"
        border
        size="small"
        max-height="240"
      >
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="no" label="单据号" width="160">
          <template #default="{ row }">{{ row.no || '-' }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="失败原因" min-width="200" show-overflow-tooltip />
      </el-table>
    </template>
    <template #footer>
      <el-button type="primary" @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>
