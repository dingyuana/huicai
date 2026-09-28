<template>
  <el-alert v-if="text" type="warning" show-icon :closable="false"
            title="报表诊断提示" :description="text" style="margin-bottom: 16px" />
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { reportDiagnostics } from '@/api/modules/report'
import { toDiagnosticAlertText, type DiagnosticItem } from '@/utils/report/diagnostics'

/**
 * P97/REQ-100 + REQ-102 报表诊断黄条（三张报表共用）。
 *
 * 诊断结果只是**建议**（铁律 #2）：本组件只展示，不触发任何写操作、不自动调数。
 * 取数失败静默降级为不显示，避免诊断接口异常把整张报表卡住。
 */
const props = defineProps<{ period: string }>()

const items = ref<DiagnosticItem[]>([])

const load = async (period: string) => {
  if (!period) {
    items.value = []
    return
  }
  try {
    const rows = await reportDiagnostics(period)
    items.value = Array.isArray(rows) ? rows : []
  } catch {
    items.value = []
  }
}

const text = computed(() => toDiagnosticAlertText(items.value))

watch(() => props.period, p => load(p), { immediate: true })

defineExpose({ reload: load })
</script>
