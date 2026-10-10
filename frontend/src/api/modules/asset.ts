import request from '@/api/request'

export interface AssetCategory {
  id?: number
  code: string
  name: string
  parentId?: number
  level?: number
  depreciationMethod?: string
  usefulLife?: number
  residualRate?: number
  assetSubjectId?: number
  depreciationSubjectId?: number
  expenseSubjectId?: number
  remark?: string
}

export interface AssetCard {
  id?: number
  assetCode: string
  assetName: string
  categoryId: number
  spec?: string
  deptId?: number
  custodianId?: number
  acquisitionDate: string
  originalValue: number
  residualValue?: number
  usefulLife: number
  depreciationMethod?: string
  status?: string
  location?: string
  serialNo?: string
  accumulatedDepreciation?: number
  netValue?: number
  remark?: string
}

export function pageAssetCategory(params: any): Promise<any> {
  return request.get('/sme/asset/v1/asset-categories/page', { params })
}

export function listAssetCategory(): Promise<AssetCategory[]> {
  return request.get('/sme/asset/v1/asset-categories/list')
}

export function createAssetCategory(data: AssetCategory): Promise<AssetCategory> {
  return request.post('/sme/asset/v1/asset-categories', data)
}

export function updateAssetCategory(id: number, data: AssetCategory): Promise<AssetCategory> {
  return request.put(`/sme/asset/v1/asset-categories/${id}`, data)
}

export function deleteAssetCategory(id: number): Promise<void> {
  return request.delete(`/sme/asset/v1/asset-categories/${id}`)
}

export function pageAssetCard(params: any): Promise<any> {
  return request.get('/sme/asset/v1/asset-cards/page', { params })
}

export function getAssetCard(id: number): Promise<AssetCard> {
  return request.get(`/sme/asset/v1/asset-cards/${id}`)
}

export function createAssetCard(data: AssetCard): Promise<AssetCard> {
  return request.post('/sme/asset/v1/asset-cards', data)
}

export function updateAssetCard(id: number, data: AssetCard): Promise<AssetCard> {
  return request.put(`/sme/asset/v1/asset-cards/${id}`, data)
}

export function deleteAssetCard(id: number): Promise<void> {
  return request.delete(`/sme/asset/v1/asset-cards/${id}`)
}

export function calculateDepreciation(id: number, period: string): Promise<number> {
  return request.get(`/sme/asset/v1/asset-cards/${id}/depreciation`, { params: { period } })
}

export function depreciatePeriod(period: string): Promise<void> {
  return request.post(`/sme/asset/v1/asset-cards/depreciate/${period}`)
}

export function depreciateOne(id: number, period: string): Promise<void> {
  return request.post(`/sme/asset/v1/asset-cards/${id}/depreciate`, null, { params: { period } })
}

export function recentAssetCards(limit = 10): Promise<any[]> {
  return request.get('/sme/asset/v1/asset-cards/recent', { params: { limit } })
}

export function getAssetCategory(id: number): Promise<AssetCategory> {
  return request.get(`/sme/asset/v1/asset-categories/${id}`)
}

// ==================== 资产处置 ====================

/**
 * 资产处置 —— 字段集 = `t_asset_disposal` 的真实列，由 `AssetDisposalVO` 逐字段对应。
 *
 * ⚠️ 原声明的 `assetCardId / disposalValue / netBookValue / reason` 四字段
 * **后端一个都没有**（真实列是 `asset_id / original_value / net_value`，且无 remark），
 * 且 grep `AssetDisposalList.vue` 引用行数均为 0 ⇒ 属契约陈旧，已删除。
 * 反之页面真正渲染的 `disposalNo / originalValue / netValue` 原先不在 interface 里，已补上。
 * 判据见 AGENTS §4.5 第 38 条：字段敏感性不能按列名归类，须 grep 页面有没有真的读它。
 */
export interface AssetDisposal {
  id?: number
  disposalNo?: string
  assetId?: number
  disposalType?: string
  disposalDate?: string
  period?: string
  originalValue?: number
  accumulatedDepreciation?: number
  netValue?: number
  disposalIncome?: number
  disposalExpense?: number
  gainLoss?: number
  status?: string
  voucherId?: number
}

export function pageAssetDisposal(params: any): Promise<any> {
  return request.get('/sme/asset/v1/asset-disposals/page', { params })
}

export function getAssetDisposal(id: number): Promise<AssetDisposal> {
  return request.get(`/sme/asset/v1/asset-disposals/${id}`)
}

export function createAssetDisposal(data: AssetDisposal): Promise<AssetDisposal> {
  return request.post('/sme/asset/v1/asset-disposals', data)
}

export function approveAssetDisposal(id: number): Promise<AssetDisposal> {
  return request.post(`/sme/asset/v1/asset-disposals/${id}/approve`)
}

export function deleteAssetDisposal(id: number): Promise<void> {
  return request.delete(`/sme/asset/v1/asset-disposals/${id}`)
}

// ==================== 资产盘点 ====================

/**
 * 资产盘点 —— 字段集 = `t_asset_inventory` 的真实列，由 `AssetInventoryVO` 逐字段对应。
 *
 * ⚠️ 原声明的 `planName / matchedCount / surplusCount / remark` 四字段后端**一个都没有**
 * （真实列是 `total_count / profit_count / loss_count`，且无 remark 列），
 * 已删除。但需诚实标注：这不只是「契约陈旧」——
 * `views/asset/inventory/AssetInventoryList.vue` 整个模板只有一句
 * `<el-empty description="资产盘点功能开发中" />`，页面**尚未实现**，
 * 上述字段的引用行数全部为 0。故按真实列更正契约，功能缺口另行立项。
 */
export interface AssetInventory {
  id?: number
  inventoryNo?: string
  inventoryDate?: string
  period?: string
  status?: string
  totalCount?: number
  profitCount?: number
  lossCount?: number
  voucherId?: number
}

export function pageAssetInventory(params: any): Promise<any> {
  return request.get('/sme/asset/v1/asset-inventories/page', { params })
}

export function getAssetInventory(id: number): Promise<AssetInventory> {
  return request.get(`/sme/asset/v1/asset-inventories/${id}`)
}

export function createAssetInventory(data: AssetInventory): Promise<AssetInventory> {
  return request.post('/sme/asset/v1/asset-inventories', data)
}

export function completeAssetInventory(id: number): Promise<AssetInventory> {
  return request.post(`/sme/asset/v1/asset-inventories/${id}/complete`)
}

export function deleteAssetInventory(id: number): Promise<void> {
  return request.delete(`/sme/asset/v1/asset-inventories/${id}`)
}

// ─── P77 折旧与资产统计报表 ───

export type AssetReportGroupBy = 'CATEGORY' | 'DEPT' | 'DEPT_CATEGORY'

export interface AssetCategorySummaryRowVO {
  categoryId: number
  categoryName: string
  qty: number
  originalValue: number | null
  accumulatedDepreciation: number | null
  netValue: number | null
  currentDepreciation: number | null
  netRatio: number | null
}

export interface AssetCategorySummaryVO {
  period: string
  rows: AssetCategorySummaryRowVO[]
  totalQty: number
  totalOriginalValue: number | null
  totalNetValue: number | null
}

export interface AssetDepreciationRowVO {
  dimKey: string
  deptId: number | null
  deptName: string
  categoryId: number | null
  categoryName: string
  assetCount: number
  depreciated: number | null
  openingAccumulated: number | null
  closingAccumulated: number | null
}

export interface AssetDepreciationSummaryVO {
  periodFrom: string
  periodTo: string
  groupBy: AssetReportGroupBy
  rows: AssetDepreciationRowVO[]
  totalDepreciated: number | null
  consistent: boolean
}

export function getAssetCategorySummary(params: { period: string; categoryId?: number }): Promise<AssetCategorySummaryVO> {
  return request.get('/sme/asset/v1/asset-reports/category-summary', { params })
}

export function getAssetDepreciationSummary(params: {
  periodFrom: string
  periodTo: string
  groupBy?: AssetReportGroupBy
}): Promise<AssetDepreciationSummaryVO> {
  return request.get('/sme/asset/v1/asset-reports/depreciation-summary', { params })
}

export function exportAssetCategorySummary(params: { period: string; categoryId?: number }): Promise<void> {
  return request.get('/sme/asset/v1/asset-reports/category-summary/export', {
    params,
    responseType: 'blob',
  }).then((res: any) => {
    const blob = new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `资产分类汇总_${params.period}.xlsx`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  })
}

export function exportAssetDepreciationSummary(params: {
  periodFrom: string
  periodTo: string
  groupBy?: AssetReportGroupBy
}): Promise<void> {
  return request.get('/sme/asset/v1/asset-reports/depreciation-summary/export', {
    params,
    responseType: 'blob',
  }).then((res: any) => {
    const blob = new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `折旧计提汇总_${params.periodFrom}-${params.periodTo}.xlsx`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  })
}
