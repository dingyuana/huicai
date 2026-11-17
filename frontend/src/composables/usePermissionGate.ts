import { getActivePinia } from 'pinia'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth.store'

/**
 * SPEC-P136 —— 单条操作按钮的权限门禁。
 *
 * <p><b>为什么不用原生 disabled</b>：disabled 的按钮不派发 click，
 * 用户点不出任何解释，只能靠 hover tooltip。改为「视觉禁用 + 可点击」，
 * 点击时弹对话框说明缺少哪个权限码 —— 与批量操作条保持同一交互。
 *
 * <p><b>无 pinia 时放行</b>：仅单元测试/组件外调用会命中；生产环境 pinia
 * 在 main.ts 中先于挂载安装。真正的边界始终是后端 403。
 */
export function usePermissionGate() {
  function hasPermission(perm: string): boolean {
    if (!getActivePinia()) return true
    return useAuthStore().hasPermission(perm)
  }

  /** 是否缺少权限（用于视觉禁用） */
  function locked(perm: string): boolean {
    return !hasPermission(perm)
  }

  /**
   * 执行前的门禁：缺权限则弹窗并返回 false，调用方据此中止。
   *
   * <p>不抛异常 —— 调用方是 UI 事件处理器，抛出去只会被 catch 成红字 toast。
   */
  async function ensure(perm: string, label: string): Promise<boolean> {
    if (hasPermission(perm)) return true
    await ElMessageBox.alert(
      `当前账号缺少「${label}」所需的权限（${perm}），请联系管理员开通。`,
      '权限不足',
      { type: 'warning' },
    ).catch(() => undefined)
    return false
  }

  return { hasPermission, locked, ensure }
}
