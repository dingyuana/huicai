import request from '@/api/request'

export function subjectBalance(period: string): Promise<any[]> {
  return request.get('/reports/subject-balance', { params: { period } })
}

export function balanceSheet(period: string): Promise<any> {
  return request.get('/reports/balance-sheet', { params: { period } })
}

export function incomeStatement(period: string): Promise<any> {
  return request.get('/reports/income-statement', { params: { period } })
}

export function cashFlowStatement(period: string): Promise<any> {
  return request.get('/reports/cash-flow', { params: { period } })
}

export function trend(startPeriod: string, endPeriod: string): Promise<any[]> {
  return request.get('/reports/trend', { params: { startPeriod, endPeriod } })
}

export function keyMetrics(period: string): Promise<any> {
  return request.get('/reports/analysis/key-metrics', { params: { period } })
}

export function dupontAnalysis(period: string): Promise<any> {
  return request.get('/reports/analysis/dupont', { params: { period } })
}

export function yoyMom(period: string): Promise<any> {
  return request.get('/reports/analysis/yoy-mom', { params: { period } })
}

export function listMetrics(): Promise<any[]> {
  return request.get('/reports/analysis/metrics')
}
