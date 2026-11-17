import type { BatchActionDef } from '@/composables/useBatchOperation'

/**
 * SPEC-P136 —— 批量操作条的权限门禁判定（纯函数，便于单元测试）。
 *
 * <p><b>为什么单独抽出来</b>：`BatchActionBar.vue` 里既要用 Element Plus 的
 * tooltip/button，又要判「权限 / 勾选数 / 状态矩阵」三件事。混在模板里只能靠
 * 组件快照间接断言，而 tooltip 内容在 happy-dom 下拿不到 ⇒ 门禁会退化成
 * 「看起来在测、其实没测」。抽成纯函数后，每条判据都能被直接断言。
 */

export type PermissionChecker = (perm: string) => boolean

/** 当前账号是否缺少该动作声明的权限（未声明 permission 的动作一律视为有权限）。 */
export function isPermissionLocked(action: BatchActionDef, hasPermission: PermissionChecker): boolean {
  return !!action.permission && !hasPermission(action.permission)
}

export interface ActionGate {
  action: BatchActionDef
  selectedCount: number
  maxCount: number
  /** 选中行是否全部满足状态矩阵 */
  statusOk: boolean
  hasPermission: PermissionChecker
}

/**
 * 按钮是否原生 disabled（浏览器不派发 click）。
 *
 * <p><b>为什么无权限时反而不 disabled</b>：用户诉求是「点一下告诉我为什么不能点」。
 * 原生 disabled 的按钮收不到 click ⇒ 只能靠 tooltip（hover），触屏与不熟悉悬浮提示的
 * 用户完全得不到解释。故无权限时改为「视觉禁用 + 可点击」，由 `useBatchOperation.run`
 * 弹窗说明。状态不符则保持原生 disabled —— 那种情况 tooltip 已足够且不应再弹窗。
 */
export function isNativelyDisabled(g: ActionGate): boolean {
  if (isPermissionLocked(g.action, g.hasPermission)) return false
  if (g.selectedCount === 0 || g.selectedCount > g.maxCount) return true
  return !g.statusOk
}

/** 悬浮提示；返回空串表示按钮可用、无需提示。 */
export function actionTooltip(g: ActionGate): string {
  if (isPermissionLocked(g.action, g.hasPermission)) {
    return `无「${g.action.label}」权限，请联系管理员开通（${g.action.permission}）`
  }
  if (g.selectedCount === 0) return '请先勾选记录'
  if (g.selectedCount > g.maxCount) return `单次最多批量操作 ${g.maxCount} 条`
  if (!g.statusOk) return `当前选中记录不支持「${g.action.label}」`
  return ''
}
