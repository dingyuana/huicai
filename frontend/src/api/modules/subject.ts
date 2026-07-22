import request from '@/api/request'

/** 科目树节点 */
export interface SubjectVO {
  id: string
  code: string
  name: string
  parentId: string | null
  level: number
  direction: string
  isLeaf: boolean
  auxCalcType: string | null
  isActive: boolean
  remark: string
  createdAt: string
  children: SubjectVO[]
}

/** 科目创建参数 */
export interface SubjectCreateParam {
  code: string
  name: string
  parentId?: string | null
  direction: string
  auxCalcType?: string | null
  isActive?: boolean
  remark?: string
}

/** 科目更新参数 */
export interface SubjectUpdateParam {
  code: string
  name: string
  direction: string
  auxCalcType?: string | null
  isActive?: boolean
  remark?: string
}

export function getSubjectTree(): Promise<SubjectVO[]> {
  return request.get('/v1/subjects/tree')
}

export function getSubject(id: string): Promise<SubjectVO> {
  return request.get(`/v1/subjects/${id}`)
}

export function createSubject(data: SubjectCreateParam): Promise<void> {
  return request.post('/v1/subjects', data)
}

export function updateSubject(id: string, data: SubjectUpdateParam): Promise<void> {
  return request.put(`/v1/subjects/${id}`, data)
}

export function deleteSubject(id: string): Promise<void> {
  return request.delete(`/v1/subjects/${id}`)
}

export function importStandardSubjects(): Promise<number> {
  return request.post('/v1/subjects/import-standard')
}

/** 导入科目（Excel 批量上传） */
export interface ImportResult {
  total: number
  success: number
  errors: Array<{ row: number; message: string }>
}

export function importSubjects(file: File): Promise<ImportResult> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/v1/subjects/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** 下载科目导入模板 */
export function downloadSubjectTemplate(): Promise<void> {
  return request.get('/v1/subjects/export-template', {
    responseType: 'blob',
  }).then((res: any) => {
    const blob = new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '科目导入模板.xlsx'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  })
}

export function initOpeningBalances(period: string, balances: Record<number, number>): Promise<void> {
  return request.post('/base/balance/v1/subject-balances/init', balances, { params: { period } })
}

export function getSubjectBalances(period: string): Promise<any[]> {
  return request.get('/base/balance/v1/subject-balances', { params: { period } })
}

export function checkTrialBalance(period: string): Promise<any> {
  return request.get('/base/balance/v1/subject-balances/trial-balance', { params: { period } })
}
