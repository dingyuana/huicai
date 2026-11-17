import { ref } from 'vue'
import { getActivePinia } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth.store'

/**
 * P67 批量操作统一 composable。
 * 交互契约见 docs/development/frontend-batch-ops-convention.md：
 * 空选校验 → 数量上限 → 原因必填(needReason) → 二次确认(needConfirm) →
 * 执行 → 结果归一化 → 统一结果弹窗 → 清空选择 + refresh。
 */
export interface BatchFailure {
  id: number
  no?: string
  reason: string
}

/** 三种后端契约归一化后的统一结果模型 */
export interface NormalizedBatchResult {
  total: number
  successCount: number
  failures: BatchFailure[]
}

export interface BatchActionDef {
  key: string
  label: string
  type?: 'primary' | 'warning' | 'danger'
  /** 需要必填原因（驳回/作废/红冲/删除类） */
  needReason?: boolean
  /** 需要二次确认 */
  needConfirm?: boolean
  /** 自定义确认文案，默认含数量与不可撤销提示 */
  confirmText?: string
  /** 单次上限，默认 100 */
  maxCount?: number
  /**
   * SPEC-P136：执行该动作所需权限码（与 permission-manifest.yml 一致）。
   * 缺省 = 不做前端权限判断（向后兼容），此时仍由后端 403 兜底。
   */
  permission?: string
}

interface UseBatchOptions {
  /** 批量成功后的刷新回调 */
  refresh?: () => any
  /** 清空表格勾选的回调（tableRef.clearSelection()） */
  clearer?: () => void
  /** SPEC-P136：权限判定，默认读 auth store（测试可注入，避免必须建 pinia） */
  hasPermission?: (perm: string) => boolean
}

/**
 * SPEC-P136：默认权限判定。
 *
 * <p>⚠️ **无 pinia 时放行**：`useBatchOperation` 只在组件内使用，生产环境 pinia
 * 在 `main.ts` 中先于挂载安装 ⇒ 该分支只会在「纯单元测试 / 组件外调用」命中。
 * 此时放行是刻意的 —— 真正的边界是后端 403，前端只是体验层（P133 D-4）。
 * 有测试锁住这一行为，避免它变成无人知晓的静默放行。
 */
function defaultHasPermission(perm: string): boolean {
  if (!getActivePinia()) return true
  return useAuthStore().hasPermission(perm)
}

/**
 * 兼容三种现存后端契约：
 * - 销项发票(P56): { success: number[], failure: [{id, reason}] }
 * - 银行流水(P55): { total, success: number, failed: [{id, reason}] }
 * - 凭证等全有全无契约: 无返回值，视为全部成功
 */
export function normalizeBatchResult(raw: any, total: number): NormalizedBatchResult {
  if (!raw) return { total, successCount: total, failures: [] }
  if (Array.isArray(raw.success)) {
    const failures: BatchFailure[] = (raw.failure || []).map((f: any) => ({
      id: f.id,
      no: f.no ?? f.invoiceNo ?? f.docNo ?? f.voucherNo,
      reason: f.reason ?? f.message ?? '未知原因',
    }))
    return { total: total || raw.success.length + failures.length, successCount: raw.success.length, failures }
  }
  if (typeof raw.success === 'number') {
    const failures: BatchFailure[] = (raw.failed || []).map((f: any) => ({
      id: f.id,
      no: f.no,
      reason: f.reason ?? f.message ?? '未知原因',
    }))
    return { total: raw.total ?? total, successCount: raw.success, failures }
  }
  return { total, successCount: total, failures: [] }
}

export function useBatchOperation(options: UseBatchOptions = {}) {
  const selectedRows = ref<any[]>([])
  const result = ref<NormalizedBatchResult | null>(null)
  const resultVisible = ref(false)
  const running = ref(false)

  function onSelectionChange(rows: any[]) {
    selectedRows.value = rows
  }

  function clearSelection() {
    selectedRows.value = []
    options.clearer?.()
  }

  async function run(def: BatchActionDef, executor: (ids: number[], reason: string) => Promise<any>) {
    // SPEC-P136：权限前置校验必须**先于**任何执行动作。
    //
    // ⚠️ 这里刻意弹 alert 而**不抛异常**：run() 是 UI 事件处理器，抛异常只会进
    //    下方 catch 再弹一条红字 —— 正是本次要消除的「一次点击两条报错」。
    if (def.permission) {
      const has = options.hasPermission ?? defaultHasPermission
      if (!has(def.permission)) {
        await ElMessageBox.alert(
          `当前账号缺少「${def.label}」所需的权限（${def.permission}），请联系管理员开通。`,
          '权限不足',
          { type: 'warning' },
        ).catch(() => undefined)
        return
      }
    }

    const ids = selectedRows.value.map((r) => r.id).filter(Boolean)
    if (ids.length === 0) {
      ElMessage.warning('请先勾选记录')
      return
    }
    const max = def.maxCount ?? 100
    if (ids.length > max) {
      ElMessage.warning(`单次最多批量操作 ${max} 条`)
      return
    }

    let reason = ''
    if (def.needReason) {
      const { value } = await ElMessageBox.prompt(
        `请输入${def.label}原因（将应用于所有选中记录）`,
        def.label,
        { inputType: 'textarea', inputValidator: (v: string) => !!v?.trim(), inputErrorMessage: '原因不能为空' }
      ).catch(() => ({ value: null }))
      if (!value) return
      reason = value
    }

    if (def.needConfirm) {
      const text = def.confirmText || `确认对 ${ids.length} 条选中记录执行【${def.label}】？此操作不可撤销。`
      try {
        await ElMessageBox.confirm(text, def.label, { type: 'warning' })
      } catch {
        return
      }
    }

    running.value = true
    try {
      const raw = await executor(ids, reason)
      result.value = normalizeBatchResult(raw, ids.length)
      resultVisible.value = true
      const failCount = result.value.failures.length
      if (failCount === 0) {
        ElMessage.success(`${def.label}完成：成功 ${result.value.successCount} 条`)
      } else {
        ElMessage.warning(`${def.label}完成：成功 ${result.value.successCount} 条，失败 ${failCount} 条（详见弹窗）`)
      }
      selectedRows.value = []
      options.clearer?.()
      await options.refresh?.()
    } catch (e: any) {
      // 全有全无契约（如凭证批量接口）整批报错时走这里
      //
      // SPEC-P136：403 已由 request.ts 弹过对话框（权限是预期内的业务结果，
      // 不是故障），此处**不得**再弹一次 —— 否则一次点击会出现「弹窗 + 红字」。
      // 判据用状态码，不比中文文案（文案会随 i18n/措辞调整而失效）。
      if (e?.response?.status === 403) return
      const msg = e?.message || '批量操作失败'
      if (msg.includes('期初建账未完成')) {
        ElMessage.error(`${msg}，请前往「基础数据-期初建账」录入期初余额`)
      } else {
        ElMessage.error(msg)
      }
    } finally {
      running.value = false
    }
  }

  return { selectedRows, result, resultVisible, running, onSelectionChange, clearSelection, run }
}
