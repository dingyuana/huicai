import request from '@/api/request'
import type { PageResult } from '@/types/api'

export interface BankAccountVO {
  id: string
  accountNo: string
  accountName: string
  bankName?: string
  currency?: string
  subjectId?: string
  balance: number
  isActive: boolean
  remark?: string
  createdAt?: string

  /** 所属企业名称（系统管理员跨企业视图用；租户 id 不外露） */
  enterpriseName?: string
}

export function getBankAccountPage(params: { keyword?: string; current?: number; size?: number }): Promise<PageResult<BankAccountVO>> {
  return request.get('/sme/cash/v1/bank-accounts/page', { params })
}
/**
 * 系统管理员专用：查看**全部企业**的银行账户（只读）。
 *
 * <p>银行账户列表此前只返回当前企业的账户，且返回体不含企业信息，
 * 管理员无法判断一条账户属于哪家公司。新建/修改仍绑定当前企业上下文。
 */
export function allEnterprisesBankAccounts(): Promise<BankAccountVO[]> {
  return request.get('/sme/cash/v1/bank-accounts/all-enterprises')
}

export function getActiveBankAccounts(): Promise<BankAccountVO[]> {
  return request.get('/sme/cash/v1/bank-accounts/active')
}
export function getBankAccount(id: string): Promise<BankAccountVO> {
  return request.get(`/sme/cash/v1/bank-accounts/${id}`)
}
export function createBankAccount(data: Partial<BankAccountVO>): Promise<BankAccountVO> {
  return request.post('/sme/cash/v1/bank-accounts', data)
}
export function updateBankAccount(id: string, data: Partial<BankAccountVO>): Promise<BankAccountVO> {
  return request.put(`/sme/cash/v1/bank-accounts/${id}`, data)
}
export function deleteBankAccount(id: string): Promise<void> {
  return request.delete(`/sme/cash/v1/bank-accounts/${id}`)
}
