import request from '@/api/request'

export interface AiTask {
  id?: number
  taskNo?: string
  taskType: string
  bizType?: string
  bizId?: number
  status?: string
  inputData?: any
  outputData?: any
  confidence?: number
  reviewed?: boolean
  applyStatus?: string
  errorMessage?: string
  createdAt?: string
  completedAt?: string
}

/**
 * 异常标签 —— 按 `t_ai_anomaly_tag` 真实列生成（P102 批次 5）。
 * 此前 `listAnomalies` 返回 `Promise<any[]>`，无任何契约约束；补上后由
 * `MasterDataVoContractTest` 逐字段核对后端 AiAnomalyTagVO 与本 interface。
 */
export interface AiAnomalyTag {
  id?: number
  bizType?: string
  bizId?: number
  anomalyType?: string
  severity?: string
  description?: string
  aiTaskId?: number
  resolved?: boolean
  resolvedBy?: number
  resolvedAt?: string
  createdAt?: string
}

export function dispatchTask(taskType: string, bizType: string, bizId: number, inputData: any): Promise<AiTask> {
  return request.post('/v1/ai/tasks', { taskType, bizType, bizId, inputData })
}

export function pageAiTask(params: any): Promise<any> {
  return request.get('/v1/ai/tasks/page', { params })
}

export function getAiTask(id: number): Promise<AiTask> {
  return request.get(`/v1/ai/tasks/${id}`)
}

export function reviewAiTask(id: number, reviewerId: number, approved: boolean): Promise<AiTask> {
  return request.post(`/v1/ai/tasks/${id}/review`, null, { params: { reviewerId, approved } })
}

export function listAnomalies(bizType?: string, resolved?: boolean): Promise<AiAnomalyTag[]> {
  return request.get('/v1/ai/anomalies', { params: { bizType, resolved } })
}
