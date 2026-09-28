/**
 * 报表诊断条渲染数据整形（P97/REQ-100 + REQ-102）。
 *
 * 抽成纯函数便于单测：黄条的"要不要显示/显示什么"是口径问题，不该埋在组件里。
 * 诊断结果是**建议**（铁律 #2），此处只做展示整形，不改任何数据。
 */

export interface DiagnosticItem {
  ruleId: string
  severity?: string
  title?: string
  detail?: string
}

/** 规则 id → 展示前缀，便于用户识别诊断来源（与 P97 规则 id 体系一致） */
const RULE_LABEL: Record<string, string> = {
  R_REVENUE_ZERO: '零收入高费用',
  R_CASH_DROP: '现金骤降',
  R_OPENING_DISCONTINUITY: '期初不连续',
}

export const formatDiagnostic = (d: DiagnosticItem): string => {
  const label = RULE_LABEL[d.ruleId] ?? d.ruleId
  return d.detail ? `[${label}] ${d.title ?? ''}：${d.detail}` : `[${label}] ${d.title ?? ''}`
}

/** 过滤空诊断并整形；无诊断返回空数组（调用方据此不渲染黄条） */
export const toDiagnosticLines = (list: DiagnosticItem[] | null | undefined): string[] =>
  (list || [])
    .filter(d => d && (d.title || d.detail))
    .map(formatDiagnostic)

/** 汇总为单条 el-alert 文案；多条用换行拼接 */
export const toDiagnosticAlertText = (list: DiagnosticItem[] | null | undefined): string =>
  toDiagnosticLines(list).join('\n')
