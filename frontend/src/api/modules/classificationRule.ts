import request from '@/api/request'

export interface ClassificationRule {
  id?: number
  tenantId?: number
  name: string
  ruleType: string       // keyword / keyword_regex / counterparty_match
  pattern: string
  matchField: string     // description / counterparty
  direction: string      // in / out / ''
  classification: string
  priority: number
  isActive?: boolean
  routeType?: string     // A / B / C
  debitSubjectId?: number
  creditSubjectId?: number
  createdAt?: string
  updatedAt?: string
}

export function pageRules(params: {
  tenantId?: number
  current?: number
  size?: number
}): Promise<any> {
  return request.get('/classification-rules', { params })
}

export function getRule(id: number): Promise<ClassificationRule> {
  return request.get(`/classification-rules/${id}`)
}

export function createRule(data: ClassificationRule): Promise<ClassificationRule> {
  return request.post('/classification-rules', data)
}

export function updateRule(id: number, data: ClassificationRule): Promise<ClassificationRule> {
  return request.put(`/classification-rules/${id}`, data)
}

export function deleteRule(id: number): Promise<void> {
  return request.delete(`/classification-rules/${id}`)
}

export function reorderRules(ids: number[]): Promise<void> {
  return request.post('/classification-rules/reorder', ids)
}

export function seedRules(tenantId: number): Promise<number> {
  return request.post('/classification-rules/seed', null, { params: { tenantId } })
}

export function testMatch(description: string, direction?: string, counterparty?: string): Promise<ClassificationRule | null> {
  return request.post('/classification-rules/match', null, {
    params: { description, direction, counterparty },
  })
}
