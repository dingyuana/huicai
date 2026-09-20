<script setup lang="ts">
/**
 * PeriodNavigator —— 期间导航器（YYYYMM）。
 *
 * [◀] [120px input] [▶] 的内联布局：
 * - v-model:modelValue 双向绑定期间字符串
 * - ◀ / ▶ 按钮基于 dayjs 做 ±1 月跳转，无效期间不跳转
 * - 输入框手动输入始终 emit（即使不匹配 /^\d{6}$/），由父组件自行校验
 * - 跳转或输入均同时 emit `update:modelValue` 与 `change`
 */
import { computed } from 'vue'
import { usePeriodNavigation } from '@/composables/usePeriodNavigation'

const props = defineProps<{ modelValue: string | undefined }>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'change', value: string): void
}>()

const PERIOD_RE = /^\d{6}$/

/** 可写 computed：setter 统一负责 emit update:modelValue + change */
const periodRef = computed<string>({
  get: () => props.modelValue ?? "",
  set: (v: string) => {
    emit('update:modelValue', v)
    emit('change', v)
  },
})

const { prevPeriod, nextPeriod } = usePeriodNavigation(periodRef)

const isValid = (v: string) => PERIOD_RE.test(v)

function onPrev() {
  // 无效期间不跳转（req: if invalid, don't navigate）
  if (!props.modelValue || !isValid(props.modelValue)) return
  prevPeriod()
}

function onNext() {
  if (!props.modelValue || !isValid(props.modelValue)) return
  nextPeriod()
}
</script>

<template>
  <div class="period-navigator">
    <el-button
      size="small"
      :disabled="!props.modelValue"
      data-test="period-prev"
      @click="onPrev"
    >◀</el-button>
    <el-input
      v-model="periodRef"
      class="period-input"
      placeholder="YYYYMM"
      maxlength="6"
      data-test="period-input"
    />
    <el-button
      size="small"
      data-test="period-next"
      @click="onNext"
    >▶</el-button>
  </div>
</template>

<style scoped>
.period-navigator {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.period-input {
  width: 120px;
}
</style>
