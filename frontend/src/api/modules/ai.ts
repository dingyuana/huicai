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

/** AI 分类反馈日志 —— 按 t_ai_feedback_log 真实列声明（P102 批次 7）。 */
export interface AiFeedbackLog {
  id?: number
  bankTxnId?: number
  aiSuggestedAction?: string
  aiConfidence?: number
  aiBusinessScene?: string
  humanAction?: string
  humanModifiedFields?: string
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

/**
 * 今日 AI 额度（SPEC-P135）。**只读，不消耗额度**。
 *
 * 🔴 `available: false` 时 `used = -1` / `limit = 0` 是**如实报告「配额守卫不可用」**，
 * 而非「额度为 0」—— 两者在页面上必须区别显示，否则用户会以为被限流了。
 * 契约字段照抄 `AiQuotaController#quota` 的 Map.of(...)。
 */
export interface AiQuota {
  available: boolean
  used: number
  limit: number
  resetAt: string
  enforcement: string
}

export function getAiQuota(): Promise<AiQuota> {
  return request.get('/ai/quota')
}

export function listAnomalies(bizType?: string, resolved?: boolean): Promise<AiAnomalyTag[]> {
  return request.get('/v1/ai/anomalies', { params: { bizType, resolved } })
}
