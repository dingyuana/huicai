import request from '@/api/request'

/** 科目树节点 */
export interface SubjectVO {
  id: number
  code: string
  name: string
  parentId: number | null
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
  parentId?: number | null
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
  return request.get('/subjects/tree')
}

export function getSubject(id: number): Promise<SubjectVO> {
  return request.get(`/subjects/${id}`)
}

export function createSubject(data: SubjectCreateParam): Promise<void> {
  return request.post('/subjects', data)
}

export function updateSubject(id: number, data: SubjectUpdateParam): Promise<void> {
  return request.put(`/subjects/${id}`, data)
}

export function deleteSubject(id: number): Promise<void> {
  return request.delete(`/subjects/${id}`)
}

export function importStandardSubjects(): Promise<number> {
  return request.post('/subjects/import-standard')
}
