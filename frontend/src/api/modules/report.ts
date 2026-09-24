import request from '@/api/request'

const XLSX_MIME = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'

/**
 * 触发浏览器下载 blob（通用助手）。
 * 统一 revokeObjectURL，避免内存泄漏。
 */
function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

/** 导出 Excel 通用：取 blob 后按 标题_期间.xlsx 命名 */
async function downloadReportExcel(
  path: string,
  title: string,
  period: string,
): Promise<void> {
  const res = await request.get(path, { params: { period }, responseType: 'blob' })
  const blob = new Blob([res as unknown as BlobPart], { type: XLSX_MIME })
  downloadBlob(blob, `${title}_${period}.xlsx`)
}

// ─── Excel 导出（P89-D）───
export function exportSubjectBalance(period: string): Promise<void> {
  return downloadReportExcel('/base/report/v1/reports/subject-balance/export', '科目余额表', period)
}

export function exportBalanceSheet(period: string): Promise<void> {
  return downloadReportExcel('/base/report/v1/reports/balance-sheet/export', '资产负债表', period)
}

export function exportIncomeStatement(period: string): Promise<void> {
  return downloadReportExcel('/base/report/v1/reports/income-statement/export', '利润表', period)
}

export function exportCashFlow(period: string): Promise<void> {
  return downloadReportExcel('/base/report/v1/reports/cash-flow/export', '现金流量表', period)
}

export function subjectBalance(period: string): Promise<any[]> {
  return request.get('/base/report/v1/reports/subject-balance', { params: { period } })
}

export function balanceSheet(period: string): Promise<any> {
  return request.get('/base/report/v1/reports/balance-sheet', { params: { period } })
}

export function incomeStatement(period: string): Promise<any> {
  return request.get('/base/report/v1/reports/income-statement', { params: { period } })
}

export function cashFlowStatement(period: string): Promise<any> {
  return request.get('/base/report/v1/reports/cash-flow', { params: { period } })
}

export function trend(startPeriod: string, endPeriod: string): Promise<any[]> {
  return request.get('/base/report/v1/reports/trend', { params: { startPeriod, endPeriod } })
}

export function keyMetrics(period: string): Promise<any> {
  return request.get('/base/report/v1/reports/analysis/key-metrics', { params: { period } })
}

export function dupontAnalysis(period: string): Promise<any> {
  return request.get('/base/report/v1/reports/analysis/dupont', { params: { period } })
}

export function yoyMom(period: string): Promise<any> {
  return request.get('/base/report/v1/reports/analysis/yoy-mom', { params: { period } })
}

export function listMetrics(): Promise<any[]> {
  return request.get('/base/report/v1/reports/analysis/metrics')
}
