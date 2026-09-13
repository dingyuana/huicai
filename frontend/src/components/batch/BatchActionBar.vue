<script setup lang="ts">
/**
 * P67 批量操作统一按钮条（见 docs/development/frontend-batch-ops-convention.md）。
 * 启用语义：every() —— 全部选中行都支持该操作才启用，杜绝静默子集执行。
 */
import { computed } from 'vue'
import type { BatchActionDef } from '@/composables/useBatchOperation'

const props = withDefaults(
  defineProps<{
    rows: any[]
    actions: BatchActionDef[]
    /** 状态 → 允许的 action key 列表 */
    statusMatrix: Record<string, string[]>
    /** 行状态取值函数，默认读 row.status */
    statusOf?: (row: any) => string
    maxCount?: number
  }>(),
  { statusOf: undefined, maxCount: 100 },
)

const emit = defineEmits<{ (e: 'action', key: string): void; (e: 'clear'): void }>()

const selectedCount = computed(() => props.rows.length)

const enabledOf = (action: BatchActionDef) => {
  if (selectedCount.value === 0 || selectedCount.value > props.maxCount) return false
  const statusOf = props.statusOf ?? ((row: any) => row.status)
  return props.rows.every((r) => (props.statusMatrix[statusOf(r)] || []).includes(action.key))
}

const tooltipOf = (action: BatchActionDef) => {
  if (selectedCount.value === 0) return '请先勾选记录'
  if (selectedCount.value > props.maxCount) return `单次最多批量操作 ${props.maxCount} 条`
  return `当前选中记录不支持「${action.label}」`
}
</script>

<template>
  <div class="batch-action-bar">
    <el-tag v-if="selectedCount > 0" type="info" effect="plain" data-test="batch-selected-count">
      已选 {{ selectedCount }} 条
    </el-tag>
    <el-tooltip
      v-for="ba in actions"
      :key="ba.key"
      :disabled="enabledOf(ba)"
      :content="tooltipOf(ba)"
      placement="top"
    >
      <span>
        <el-button
          :type="ba.type || 'primary'"
          plain
          size="small"
          :disabled="!enabledOf(ba)"
          :data-test="`batch-action-${ba.key}`"
          @click="emit('action', ba.key)"
        >{{ ba.label }}</el-button>
      </span>
    </el-tooltip>
    <el-button v-if="selectedCount > 0" text size="small" @click="emit('clear')">清空</el-button>
  </div>
</template>

<style scoped>
.batch-action-bar {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
