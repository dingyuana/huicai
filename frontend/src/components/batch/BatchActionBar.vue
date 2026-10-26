<script setup lang="ts">
/**
 * P67 批量操作统一按钮条（见 docs/development/frontend-batch-ops-convention.md）。
 * 启用语义：every() —— 全部选中行都支持该操作才启用，杜绝静默子集执行。
 *
 * SPEC-P136：在状态矩阵之外增加**权限门禁**。
 *
 * <p>无权限时**不用原生 disabled**：原生 disabled 的按钮不派发 click，
 * 用户点不出任何解释（只能靠 hover tooltip）。改为「视觉禁用 + 仍可点击」，
 * 由 useBatchOperation.run 弹窗说明缺少哪个权限码。
 */
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth.store'
import type { BatchActionDef } from '@/composables/useBatchOperation'
import {
  actionTooltip,
  isNativelyDisabled,
  isPermissionLocked,
  type ActionGate,
} from './batchActionPermission'

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

const authStore = useAuthStore()

const selectedCount = computed(() => props.rows.length)

const gateOf = (action: BatchActionDef): ActionGate => {
  const statusOf = props.statusOf ?? ((row: any) => row.status)
  return {
    action,
    selectedCount: selectedCount.value,
    maxCount: props.maxCount,
    statusOk: props.rows.every((r) =>
      (props.statusMatrix[statusOf(r)] || []).includes(action.key),
    ),
    hasPermission: (perm: string) => authStore.hasPermission(perm),
  }
}

const lockedOf = (action: BatchActionDef) =>
  isPermissionLocked(action, (perm: string) => authStore.hasPermission(perm))

const disabledOf = (action: BatchActionDef) => isNativelyDisabled(gateOf(action))

const tooltipOf = (action: BatchActionDef) => actionTooltip(gateOf(action))
</script>

<template>
  <div class="batch-action-bar">
    <el-tag v-if="selectedCount > 0" type="info" effect="plain" data-test="batch-selected-count">
      已选 {{ selectedCount }} 条
    </el-tag>
    <el-tooltip
      v-for="ba in actions"
      :key="ba.key"
      :disabled="!tooltipOf(ba)"
      :content="tooltipOf(ba)"
      placement="top"
    >
      <span>
        <el-button
          :type="ba.type || 'primary'"
          plain
          size="small"
          :disabled="disabledOf(ba)"
          :aria-disabled="lockedOf(ba)"
          :class="{ 'is-permission-locked': lockedOf(ba) }"
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

/* SPEC-P136：无权限的按钮视觉上禁用，但保留可点击以给出解释 */
.batch-action-bar :deep(.is-permission-locked) {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
