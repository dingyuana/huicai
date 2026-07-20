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

export interface AssetDisposal {
  id?: number
  assetCardId: number
  disposalType?: string
  disposalDate: string
  disposalValue?: number
  netBookValue?: number
  gainLoss?: number
  reason?: string
  status?: string
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

export interface AssetInventory {
  id?: number
  inventoryNo?: string
  planName: string
  inventoryDate: string
  status?: string
  totalCount?: number
  matchedCount?: number
  surplusCount?: number
  lossCount?: number
  remark?: string
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
