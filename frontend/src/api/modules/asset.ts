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
  return request.get('/asset-categories/page', { params })
}

export function listAssetCategory(): Promise<AssetCategory[]> {
  return request.get('/asset-categories/list')
}

export function createAssetCategory(data: AssetCategory): Promise<AssetCategory> {
  return request.post('/asset-categories', data)
}

export function updateAssetCategory(id: number, data: AssetCategory): Promise<AssetCategory> {
  return request.put(`/asset-categories/${id}`, data)
}

export function deleteAssetCategory(id: number): Promise<void> {
  return request.delete(`/asset-categories/${id}`)
}

export function pageAssetCard(params: any): Promise<any> {
  return request.get('/asset-cards/page', { params })
}

export function getAssetCard(id: number): Promise<AssetCard> {
  return request.get(`/asset-cards/${id}`)
}

export function createAssetCard(data: AssetCard): Promise<AssetCard> {
  return request.post('/asset-cards', data)
}

export function updateAssetCard(id: number, data: AssetCard): Promise<AssetCard> {
  return request.put(`/asset-cards/${id}`, data)
}

export function deleteAssetCard(id: number): Promise<void> {
  return request.delete(`/asset-cards/${id}`)
}

export function calculateDepreciation(id: number, period: string): Promise<number> {
  return request.get(`/asset-cards/${id}/depreciation`, { params: { period } })
}

export function depreciatePeriod(period: string): Promise<void> {
  return request.post(`/asset-cards/depreciate/${period}`)
}

export function depreciateOne(id: number, period: string): Promise<void> {
  return request.post(`/asset-cards/${id}/depreciate`, null, { params: { period } })
}

export function recentAssetCards(limit = 10): Promise<any[]> {
  return request.get('/asset-cards/recent', { params: { limit } })
}
