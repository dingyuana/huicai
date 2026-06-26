import request from '@/api/request'

/** 分录行VO */
export interface TemplateLineVO {
  id?: number
  subjectId: number
  subjectCode?: string
  subjectName?: string
  drAmountTemplate?: string
  crAmountTemplate?: string
  summaryTemplate?: string
  direction: string
  assistType?: string
  assistRequired?: boolean
  lineOrder: number
}

/** 凭证模板VO */
export interface VoucherTemplateVO {
  id: number
  name: string
  description?: string
  classification?: string
  source?: string
  businessType?: string
  direction?: string
  matchPriority?: number
  numberPrefix: string
  isActive: boolean
  createdAt: string
  updatedAt: string
  lines: TemplateLineVO[]
}

/** 创建请求 */
export interface VoucherTemplateCreateRequest {
  name: string
  description?: string
  classification?: string
  source?: string
  businessType?: string
  direction?: string
  matchPriority?: number
  numberPrefix?: string
  isActive?: boolean
  lines: TemplateLineVO[]
}

/** 获取模板列表 */
export function listTemplates(classification?: string) {
  const params: Record<string, string> = {}
  if (classification) params.classification = classification
  return request.get<VoucherTemplateVO[]>('/voucher-templates', { params })
}

/** 获取单个模板详情 */
export function getTemplate(id: number) {
  return request.get<VoucherTemplateVO>(`/voucher-templates/${id}`)
}

/** 创建模板 */
export function createTemplate(data: VoucherTemplateCreateRequest) {
  return request.post<VoucherTemplateVO>('/voucher-templates', data)
}

/** 更新模板基本信息 */
export function updateTemplate(id: number, data: {
  name?: string; description?: string; classification?: string;
  source?: string; businessType?: string; direction?: string; matchPriority?: number;
  numberPrefix?: string;
}) {
  return request.put(`/voucher-templates/${id}`, data)
}

/** 更新模板分录行 (全量替换) */
export function updateTemplateLines(id: number, lines: TemplateLineVO[]) {
  return request.put(`/voucher-templates/${id}/lines`, lines)
}

/** 激活/停用模板 */
export function toggleTemplateActive(id: number, active: boolean) {
  return request.post(`/voucher-templates/${id}/toggle-active`, null, { params: { active } })
}

/** 删除模板 */
export function deleteTemplate(id: number) {
  return request.delete(`/voucher-templates/${id}`)
}
